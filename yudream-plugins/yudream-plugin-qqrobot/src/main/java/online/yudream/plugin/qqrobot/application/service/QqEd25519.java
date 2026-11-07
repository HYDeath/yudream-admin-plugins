package online.yudream.plugin.qqrobot.application.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.HexFormat;

/**
 * QQ 开放平台的 Ed25519 密钥派生与签名。
 *
 * <p>官方不用 PEM 密钥，而是把 Bot Secret 反复自我拼接（每轮翻倍）到至少
 * 32 字节，截取前 32 字节作为 Ed25519 seed；同一份 seed 既决定验签用的公钥，
 * 也用于回调地址验证（op=13）的应答签名。因此 AppSecret 既是凭证也是密钥
 * 材料，必须留在插件密钥库里。</p>
 *
 * <p>签名体固定为 {@code event_ts + plain_token}（时间戳在前，两者都是原样
 * 字符串），输出 hex。密钥派生是确定性的，测试用官方文档给的样例向量校验。</p>
 */
public final class QqEd25519 {
    private static final int SEED_SIZE = 32;

    private QqEd25519() {
    }

    /** 把任意长度的 secret 扩成 32 字节 seed：不够就翻倍拼接，超长截断。 */
    public static byte[] seed(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        String value = secret;
        while (value.getBytes(StandardCharsets.UTF_8).length < SEED_SIZE) {
            value = value + value;
        }
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        byte[] seed = new byte[SEED_SIZE];
        System.arraycopy(raw, 0, seed, 0, SEED_SIZE);
        return seed;
    }

    public static KeyPair keyPair(String secret) {
        return keyPairFromSeed(seed(secret));
    }

    /** 从 32 字节 seed 生成密钥对，单测用它对齐 RFC 8032 向量。 */
    static KeyPair keyPairFromSeed(byte[] seed) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            // JDK 的 Ed25519 KeyPairGenerator 只从 SecureRandom 取 32 字节 seed，
            // 喂固定 seed 就能得到确定性的密钥对（已用 RFC 8032 测试向量对齐）。
            generator.initialize(NamedParameterSpec.ED25519, new SeedRandom(seed));
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Ed25519 密钥派生失败", exception);
        }
    }

    private static final class SeedRandom extends SecureRandom {
        private final byte[] seed;

        private SeedRandom(byte[] seed) {
            this.seed = seed.clone();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            System.arraycopy(seed, 0, bytes, 0, Math.min(bytes.length, seed.length));
        }
    }

    /** 公钥的 32 字节 raw 形式（去掉 X.509 固定前缀），hex 小写。 */
    public static String publicKeyHex(String secret) {
        byte[] encoded = keyPair(secret).getPublic().getEncoded();
        byte[] raw = new byte[SEED_SIZE];
        System.arraycopy(encoded, encoded.length - SEED_SIZE, raw, 0, SEED_SIZE);
        return HexFormat.of().formatHex(raw);
    }

    /** 回调地址验证应答：{@code hex(Ed25519(seed, event_ts + plain_token))}。 */
    public static String signValidation(String secret, String eventTs, String plainToken) {
        return sign(secret, (eventTs == null ? "" : eventTs) + (plainToken == null ? "" : plainToken));
    }

    public static String sign(String secret, String message) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keyPair(secret).getPrivate());
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signer.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Ed25519 签名失败", exception);
        }
    }

}
