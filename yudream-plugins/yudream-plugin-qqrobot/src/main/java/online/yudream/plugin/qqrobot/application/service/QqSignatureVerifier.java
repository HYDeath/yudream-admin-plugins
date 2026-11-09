package online.yudream.plugin.qqrobot.application.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.HexFormat;

/**
 * QQ 官方机器人回调签名校验。
 *
 * <p>开放平台用 Ed25519 对 {@code timestamp + body} 签名，放进
 * {@code X-Signature-Ed25519}，时间戳放 {@code X-Signature-Timestamp}，
 * 公钥放在机器人详情页的「回调配置」里（hex 编码的 32 字节 raw key）。
 * 校验通过才处理事件，避免任何人伪造 FRIEND_ADD 触发绑定码。</p>
 *
 * <p>纯静态工具，便于单测；不带任何网络依赖。</p>
 */
public final class QqSignatureVerifier {
    /** 官方要求时间戳与本地时间相差不超过 5 分钟。 */
    private static final long MAX_SKEW_SECONDS = 300;

    private QqSignatureVerifier() {
    }

    public static boolean verify(String signatureBase64, String timestamp, String body,
                                 String publicKeyHex) {
        return verify(signatureBase64, timestamp, body, publicKeyHex, Instant.now().getEpochSecond());
    }

    static boolean verify(String signatureBase64, String timestamp, String body,
                          String publicKeyHex, long nowSeconds) {
        if (blank(signatureBase64) || blank(timestamp) || blank(publicKeyHex)) {
            return false;
        }
        long stamp;
        try {
            stamp = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return false;
        }
        if (Math.abs(nowSeconds - stamp) > MAX_SKEW_SECONDS) {
            return false;
        }
        try {
            // QQ 官方回调文档规定 X-Signature-Ed25519 是 64 字节签名的
            // 128 位十六进制文本，不是 Base64。严格按 hex 解码，避免
            // 错误接受格式相似但无法验证的请求。
            byte[] signature = HexFormat.of().parseHex(signatureBase64.trim());
            if (signature.length != 64 || (signature[63] & 0xE0) != 0) {
                return false;
            }
            PublicKey key = publicKey(publicKeyHex);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update((timestamp.trim() + (body == null ? "" : body)).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 官方提供的公钥是 32 字节 raw key 的 hex；Java 需要 X.509 包装，
     * 这里补上 Ed25519 的固定前缀再构造 PublicKey。
     */
    static PublicKey publicKey(String publicKeyHex) throws Exception {
        String hex = publicKeyHex.trim().replace(" ", "");
        byte[] raw = HexFormat.of().parseHex(hex);
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 公钥必须是 32 字节");
        }
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        byte[] encoded = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
