package online.yudream.plugin.qqrobot.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqSignatureVerifierTest {
    private static final long NOW = 1_800_000_000L;
    private static final String BODY = """
            {"t":"FRIEND_ADD","openid":"A1B2C3D4E5F6A1B2"}
            """;

    @Test
    void acceptsSignatureFromMatchingKey() throws Exception {
        KeyPair pair = keyPair();
        String timestamp = String.valueOf(NOW);
        String signature = sign(pair, timestamp, BODY);

        assertTrue(QqSignatureVerifier.verify(signature, timestamp, BODY,
                rawHex(pair), NOW));
    }

    @Test
    void rejectsStaleTimestamp() throws Exception {
        KeyPair pair = keyPair();
        String timestamp = String.valueOf(NOW - 400);
        String signature = sign(pair, timestamp, BODY);

        assertFalse(QqSignatureVerifier.verify(signature, timestamp, BODY,
                rawHex(pair), NOW));
    }

    @Test
    void rejectsSignatureFromOtherKey() throws Exception {
        KeyPair signer = keyPair();
        KeyPair other = keyPair();
        String timestamp = String.valueOf(NOW);

        assertFalse(QqSignatureVerifier.verify(sign(signer, timestamp, BODY), timestamp, BODY,
                rawHex(other), NOW));
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        KeyPair pair = keyPair();
        String timestamp = String.valueOf(NOW);
        String signature = sign(pair, timestamp, BODY);

        assertFalse(QqSignatureVerifier.verify(signature, timestamp, BODY.replace("A1B2", "FFFF"),
                rawHex(pair), NOW));
    }

    @Test
    void rejectsMissingMaterials() {
        assertFalse(QqSignatureVerifier.verify("", "1", BODY, "00", NOW));
        assertFalse(QqSignatureVerifier.verify("abc", "", BODY, "00", NOW));
        assertFalse(QqSignatureVerifier.verify("abc", "1", BODY, "", NOW));
        assertFalse(QqSignatureVerifier.verify("abc", "not-a-number", BODY,
                "0123456789abcdef0123456789abcdef", NOW));
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        return generator.generateKeyPair();
    }

    /** 取 X.509 编码末尾 32 字节，还原开放平台给出的 raw key。 */
    private static String rawHex(KeyPair pair) {
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - raw.length, raw, 0, raw.length);
        return HexFormat.of().formatHex(raw);
    }

    private static String sign(KeyPair pair, String timestamp, String body) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }
}
