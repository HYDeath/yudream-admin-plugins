package online.yudream.plugin.schematic.interfaces;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.messaging.*;
import online.yudream.base.plugin.spi.system.user.PluginMessagingIdentity;
import online.yudream.plugin.schematic.application.*;
import online.yudream.plugin.schematic.infrastructure.QqFileDownload;
import online.yudream.plugin.schematic.infrastructure.NbtReader;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class SchematicMessages implements AutoCloseable {
    @FunctionalInterface public interface FileSource { byte[] download(String url) throws Exception; }
    public record Attachment(String name, String url, String fileId) { }
    private final PluginContext context;
    private final SchematicJobs jobs;
    private final SchematicAdmission admission;
    private final FileSource downloader;
    private final ExecutorService downloads = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), Thread.ofVirtual().name("schematic-file-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    public SchematicMessages(PluginContext context, SchematicJobs jobs, SchematicAdmission admission, QqFileDownload downloader) { this(context, jobs, admission, downloader::download); }
    public SchematicMessages(PluginContext context, SchematicJobs jobs, SchematicAdmission admission, FileSource downloader) { this.context = context; this.jobs = jobs; this.admission = admission; this.downloader = downloader; }

    public void onMessage(PluginEvent event) {
        if (event.userId() == null || Objects.equals(event.userId(), event.selfId()) || Boolean.TRUE.equals(event.referrer().get("sandbox")) || event.referrer().containsKey("sandboxSessionId")) return;
        List<Attachment> attachments = attachments(event.nativeData());
        if (attachments.isEmpty()) {
            if (event.content() != null && event.content().strip().matches("[/!]?投影(帮助)?")) send(event, PluginMessageContent.Type.TEXT, "发送 .litematic / Java 结构 .nbt 文件即可生成透明双视角建筑预览。官方 QQ 若未开放此类附件，请到网站工具箱 → 建筑投影上传。群预览可凭分享码在工具箱查看。文件上限 16 MiB，非空气方块上限 35 万。");
            return;
        }
        Attachment file = attachments.getFirst();
        String key = event.connectionId() + ":" + event.channelId() + ":" + event.userId();
        String delivery = key + ":" + Objects.toString(event.messageId(), event.sequence()) + ":" + Objects.toString(file.fileId(), file.name());
        synchronized (seen) {
            long now = System.currentTimeMillis(); seen.entrySet().removeIf(e -> now - e.getValue() > 600_000);
            if (seen.containsKey(delivery)) return;
            if (seen.size() >= 2048) seen.remove(seen.keySet().iterator().next());
            seen.put(delivery, now);
        }
        if (!pending.add(key)) return;
        try { downloads.execute(() -> receive(event, file, key)); }
        catch (RejectedExecutionException e) { pending.remove(key); send(event, PluginMessageContent.Type.TEXT, "投影处理繁忙，请稍后重发文件"); }
    }
    private void receive(PluginEvent event, Attachment file, String pendingKey) {
        try {
            String url = file.url();
            if ((url == null || url.isBlank()) && file.fileId() != null) {
                var result = context.framework().messagingRaw().invoke(event.connectionId(), "get_group_file_download_url", Map.of("group_id", event.channelId(), "file_id", file.fileId())).toCompletableFuture().get(15, TimeUnit.SECONDS);
                if (result.get("data") instanceof Map<?, ?> nested) result = toMap(nested);
                url = text(result, "download_url", "url");
            }
            String owner = owner(event);
            if (!admission.permits(owner)) { pending.remove(pendingKey); send(event, PluginMessageContent.Type.TEXT, SchematicAdmission.REJECTED_MESSAGE); return; }
            byte[] bytes = downloader.download(url);
            if (!NbtReader.hasSupportedHeader(bytes)) {
                if (SchematicParser.supported(file.name())) send(event, PluginMessageContent.Type.TEXT, "文件实际内容不是 NBT 投影，已拒绝处理；修改后缀不会改变文件格式。");
                pending.remove(pendingKey); return;
            }
            jobs.submit(owner, file.name(), bytes, job -> {
                try {
                    if ("READY".equals(job.get("status"))) {
                        send(event, PluginMessageContent.Type.IMAGE, "base64://" + Base64.getEncoder().encodeToString(jobs.preview(String.valueOf(job.get("shareToken")))))
                                .whenComplete((sent, error) -> send(event, PluginMessageContent.Type.TEXT, (error == null ? "" : "群图片发送失败，可凭分享码在工具箱查看。\n") + caption(job)));
                    } else send(event, PluginMessageContent.Type.TEXT, "投影解析失败：" + job.get("error"));
                } finally { pending.remove(pendingKey); }
            });
        } catch (Exception e) {
            pending.remove(pendingKey);
            send(event, PluginMessageContent.Type.TEXT, e instanceof IllegalArgumentException ? e.getMessage() : "投影文件下载或提交失败，请重新发送，或使用网站工具箱上传");
        }
    }
    private String owner(PluginEvent event) {
        String protocol = Objects.toString(event.referrer().get("protocol"), event.platform());
        boolean official = "official".equals(protocol);
        String type = Objects.toString(event.referrer().get("identityType"), official ? "member_openid" : "qq");
        var identity = new PluginMessagingIdentity(protocol, type, event.userId(), (String) event.referrer().get("appId"),
                "member_openid".equals(type) ? Objects.toString(event.referrer().get("groupOpenid"), event.channelId()) : null, event.connectionId());
        return context.framework().users().findByMessagingIdentity(identity).map(user -> "user:" + user.id()).orElse("qq:" + event.connectionId() + ":" + event.channelId() + ":" + event.userId());
    }
    private CompletionStage<PluginMessageResult> send(PluginEvent event, PluginMessageContent.Type type, String content) {
        Map<String, Object> reply = new LinkedHashMap<>(event.referrer());
        if (event.nativeData() instanceof Map<?, ?> nativeData) for (String name : List.of("message_scene", "msg_id", "message_id", "event_id", "interaction_id")) { Object v = nativeData.get(name); if (v != null) reply.put(name, v); }
        if (event.messageId() != null) reply.putIfAbsent("message_id", event.messageId());
        return context.framework().messaging().send(new PluginMessageRequest(event.connectionId(), event.platform(), event.selfId(), event.channelId(), new PluginMessageContent(type, content, null, reply)));
    }
    public static List<Attachment> attachments(Object data) {
        List<Attachment> found = new ArrayList<>(); collect(data, found, 0);
        return found.stream().distinct().limit(1).toList();
    }
    private static void collect(Object raw, List<Attachment> found, int depth) {
        if (depth > 7 || found.size() > 8) return;
        if (raw instanceof List<?> list) { list.stream().limit(64).forEach(v -> collect(v, found, depth + 1)); return; }
        if (!(raw instanceof Map<?, ?> value)) return;
        if (Set.of("image", "video", "record", "audio", "text", "mention", "face").contains(String.valueOf(value.get("type")))) return;
        if ("file".equals(value.get("type")) && value.get("data") instanceof Map<?, ?> file) {
            String fileUrl = text(file, "url", "download_url", "uri", "resource_url", "file_url");
            if (fileUrl != null) found.add(new Attachment(Objects.requireNonNullElse(text(file, "file_name", "filename", "name"), "投影"), fileUrl, text(file, "file_id")));
        }
        String name = text(value, "file_name", "filename", "name", "title"), url = text(value, "url", "download_url"), id = text(value, "file_id", "id");
        if (name != null && (url != null || id != null)) found.add(new Attachment(name, url, id));
        for (String key : List.of("segments", "attachments", "data", "file", "native", "msg_elements")) if (value.containsKey(key)) collect(value.get(key), found, depth + 1);
    }
    public static String caption(Map<String, Object> j) {
        long created = j.get("createdAt") instanceof Number n ? n.longValue() : 0;
        String date = created > 0 && created < 32_503_680_000_000L ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(created)) : "未记录";
        return "投影名称：" + j.get("name") + "\n保存者游戏 ID：" + j.get("author") + "\n创建时间：" + date
                + "\n方块数/体积：" + j.get("blocks") + "/" + j.get("volume") + "\n尺寸：" + j.get("width") + " × " + j.get("height") + " × " + j.get("length")
                + "\nLitematic 版本：" + (Objects.equals(j.get("formatVersion"), 0) ? "Java 结构 NBT" : j.get("formatVersion")) + "\n游戏数据版本：" + j.get("dataVersion")
                + "\n预览：体素概览 · 透明双视角\n工具箱分享码：" + j.get("shareToken");
    }
    private static String text(Map<?, ?> data, String... names) { for (String name : names) { Object value = data.get(name); if (value != null && !value.toString().isBlank()) return value.toString(); } return null; }
    private static Map<String, Object> toMap(Map<?, ?> value) { Map<String, Object> result = new LinkedHashMap<>(); value.forEach((k, v) -> result.put(String.valueOf(k), v)); return result; }
    @Override public void close() { downloads.shutdownNow(); pending.clear(); }
}
