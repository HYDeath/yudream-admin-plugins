package online.yudream.plugin.schematic.infrastructure;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Only downloads QQ-supplied CDN attachments; arbitrary chat text URLs are never fetched. */
public final class QqFileDownload implements AutoCloseable {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    public byte[] download(String value) throws Exception {
        URI uri = validate(value);
        var future = http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(), info -> new LimitedBody());
        try {
            var response = future.get(25, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new IllegalArgumentException("QQ 文件下载失败或链接已过期，请重新发送文件");
            return response.body();
        } finally { if (!future.isDone()) future.cancel(true); }
    }
    static URI validate(String value) throws Exception {
        if (value == null || value.length() > 8192) throw new IllegalArgumentException("缺少文件下载地址");
        URI uri = URI.create(value.startsWith("//") ? "https:" + value : value);
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null || !Set.of("http", "https").contains(uri.getScheme()) || uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80) throw new IllegalArgumentException("文件下载地址不合法");
        host = host.toLowerCase(Locale.ROOT);
        if (!allowedHost(host)) throw new IllegalArgumentException("仅允许 QQ 附件 CDN 下载地址，请在工具箱直接上传此文件");
        for (InetAddress address : InetAddress.getAllByName(host)) if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress() || (address.getAddress()[0] & 254) == 252) throw new IllegalArgumentException("附件地址不可访问内网");
        return uri;
    }
    static boolean allowedHost(String host) {
        // Current official group-file attachment examples use this exact Tencent CDN host.
        return host.equals("multimedia.nt.qq.com.cn") || host.endsWith(".qq.com")
                || host.endsWith(".qpic.cn") || host.endsWith(".gtimg.cn") || host.endsWith(".myqcloud.com");
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) output.size() + buffer.remaining() > NbtReader.MAX_FILE) { subscription.cancel(); body.completeExceptionally(new IllegalArgumentException("群文件超过 16 MiB")); return; }
                byte[] data = new byte[buffer.remaining()]; buffer.get(data); output.writeBytes(data);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(output.toByteArray()); }
    }
    @Override public void close() { http.shutdownNow(); }
}
