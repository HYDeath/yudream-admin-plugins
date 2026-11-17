package online.yudream.plugin.schematic.application;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.plugin.schematic.domain.Schematic;
import online.yudream.plugin.schematic.infrastructure.NbtReader;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class SchematicJobs implements AutoCloseable {
    @FunctionalInterface public interface Renderer { byte[] render(Schematic model) throws Exception; }
    private final PluginDocumentStore documents;
    private final PluginFileStore files;
    private final Renderer renderer;
    private final ThreadPoolExecutor queue = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), Thread.ofVirtual().name("schematic-render-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> activeOwners = ConcurrentHashMap.newKeySet();
    private final Map<String, Future<?>> running = new ConcurrentHashMap<>();

    public SchematicJobs(PluginDocumentStore documents, PluginFileStore files, Renderer renderer) { this.documents = documents; this.files = files; this.renderer = renderer; }

    public synchronized Map<String, Object> submit(String owner, String filename, byte[] data, Consumer<Map<String, Object>> completed) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("上传者身份缺失");
        if (data == null || data.length == 0 || data.length > NbtReader.MAX_FILE) throw new IllegalArgumentException("文件须为 1 字节至 16 MiB");
        if (!NbtReader.hasSupportedHeader(data)) throw new IllegalArgumentException("文件内容不是投影 NBT，伪造后缀的程序或脚本会被拒绝");
        if (documents.count(collection(owner)) >= 100) throw new IllegalArgumentException("已保存 100 个投影，请先删除不需要的记录");
        if (!activeOwners.add(owner)) throw new IllegalArgumentException("你已有投影正在处理，请等待完成");
        String id = UUID.randomUUID().toString(), token = UUID.randomUUID().toString();
        var job = new LinkedHashMap<String, Object>();
        job.put("id", id); job.put("owner", owner); job.put("filename", safeFilename(filename)); job.put("status", "QUEUED");
        job.put("submittedAt", Instant.now().toString()); job.put("shareToken", token);
        try {
            save(job);
            FutureTask<Void> task = new FutureTask<>(() -> { process(job, data, completed); return null; });
            running.put(id, task); queue.execute(task);
        } catch (RuntimeException e) {
            activeOwners.remove(owner); running.remove(id); deleteRecords(job);
            throw new IllegalArgumentException("渲染队列暂不可用，请稍后重试", e);
        }
        return view(job);
    }
    private void process(Map<String, Object> job, byte[] data, Consumer<Map<String, Object>> completed) {
        String id = (String) job.get("id");
        try {
            synchronized (this) { job.put("status", "RENDERING"); save(job); }
            Schematic model = new SchematicParser().parse((String) job.get("filename"), data);
            byte[] preview = renderer.render(model);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            synchronized (this) {
                files.put("previews/" + id + ".png", new ByteArrayInputStream(preview), preview.length, "image/png");
                job.put("name", model.name()); job.put("author", model.author()); job.put("createdAt", model.createdAt());
                job.put("formatVersion", model.formatVersion()); job.put("dataVersion", model.dataVersion());
                job.put("width", model.width()); job.put("height", model.height()); job.put("length", model.length());
                job.put("blocks", model.blocks().size()); job.put("volume", model.volume());
                job.put("materials", model.materials()); job.put("status", "READY"); job.put("completedAt", Instant.now().toString()); save(job);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            synchronized (this) {
                job.put("status", "FAILED");
                job.put("error", e instanceof IllegalArgumentException ? e.getMessage() : "浏览器渲染失败，请确认平台的消息渲染能力已启用后重新上传");
                save(job);
            }
        } finally { activeOwners.remove(String.valueOf(job.get("owner"))); running.remove(id); }
        if (completed != null) { try { completed.accept(view(job)); } catch (RuntimeException ignored) { /* Delivery failure never invalidates a persisted preview. */ } }
    }
    public synchronized Map<String, Object> page(String owner, boolean admin, int page, int size) {
        page = Math.max(1, page); size = Math.min(50, Math.max(1, size));
        String collection = admin ? "jobs" : collection(owner);
        var values = documents.findAll(collection, page, size).stream().map(this::recover).map(this::view).toList();
        return Map.of("records", values, "total", documents.count(collection));
    }
    public synchronized Map<String, Object> get(String owner, String id, boolean admin) {
        return view(recover(owned(owner, id, admin)));
    }
    private Map<String, Object> recover(Map<String, Object> stored) {
        var job = new LinkedHashMap<>(stored);
        String id = String.valueOf(job.get("id"));
        // A process restart cannot resume browser work; make the persisted state actionable.
        if (Set.of("QUEUED", "RENDERING").contains(job.get("status")) && !running.containsKey(id)) {
            job.put("status", "FAILED"); job.put("error", "任务因服务重启中断，请重新上传"); save(job);
        }
        return job;
    }
    public synchronized void delete(String owner, String id, boolean admin) {
        var job = owned(owner, id, admin);
        if (running.containsKey(id)) throw new IllegalArgumentException("请等待渲染结束后删除");
        files.delete("previews/" + id + ".png"); deleteRecords(job);
    }
    public synchronized Map<String, Object> shared(String token) {
        if (!uuid(token)) throw new NoSuchElementException("投影不存在");
        var index = documents.findById("shares", token).orElseThrow(() -> new NoSuchElementException("投影不存在"));
        String jobId = Objects.toString(index.get("jobId"), "");
        var job = uuid(jobId) ? documents.findById("jobs", jobId).orElseThrow(() -> new NoSuchElementException("投影不存在"))
                : documents.findByField("jobs", "shareToken", token, 1, 1).stream().findFirst().orElseThrow(() -> new NoSuchElementException("投影不存在"));
        if (!Objects.equals(token, job.get("shareToken"))) throw new NoSuchElementException("投影不存在");
        var safe = new LinkedHashMap<>(view(job)); safe.remove("submittedAt"); return safe;
    }
    public byte[] preview(String token) {
        var job = shared(token);
        if (!"READY".equals(job.get("status"))) throw new NoSuchElementException("投影尚未完成");
        try (var input = files.get("previews/" + job.get("id") + ".png").inputStream()) { return input.readAllBytes(); }
        catch (IOException e) { throw new IllegalStateException("预览图不可读取", e); }
    }
    private Map<String, Object> owned(String owner, String id, boolean admin) {
        if (!uuid(id)) throw new NoSuchElementException("投影不存在");
        var job = documents.findById("jobs", id).orElseThrow(() -> new NoSuchElementException("投影不存在"));
        if (!admin && !Objects.equals(owner, job.get("owner"))) throw new NoSuchElementException("投影不存在");
        return new LinkedHashMap<>(job);
    }
    private void save(Map<String, Object> job) {
        String id = (String) job.get("id");
        documents.save("jobs", id, job); documents.save(collection(String.valueOf(job.get("owner"))), id, job);
        documents.save("shares", String.valueOf(job.get("shareToken")), Map.of("jobId", id));
    }
    private void deleteRecords(Map<String, Object> job) {
        String id = String.valueOf(job.get("id")); documents.delete("jobs", id); documents.delete(collection(String.valueOf(job.get("owner"))), id); documents.delete("shares", String.valueOf(job.get("shareToken")));
    }
    private Map<String, Object> view(Map<String, Object> job) {
        var result = new LinkedHashMap<>(job); result.remove("owner");
        result.put("previewPath", "/public/previews/" + result.get("shareToken") + ".png");
        result.put("renderer", "体素概览（近似方块色，大型建筑自动简化；不含原版材质及特殊模型）"); return result;
    }
    private static String collection(String owner) { return "owner-" + UUID.nameUUIDFromBytes(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static boolean uuid(String id) { return id != null && id.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"); }
    private static String safeFilename(String name) {
        String safe = Objects.toString(name, "投影").replace('\\', '/').replaceAll("[\\p{Cntrl}]", ""); safe = safe.substring(safe.lastIndexOf('/') + 1);
        if (safe.isBlank()) safe = "投影";
        if (safe.length() <= 180) return safe;
        return safe.substring(0, 180);
    }
    @Override public void close() {
        queue.shutdownNow(); running.values().forEach(task -> task.cancel(true));
        try { queue.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
