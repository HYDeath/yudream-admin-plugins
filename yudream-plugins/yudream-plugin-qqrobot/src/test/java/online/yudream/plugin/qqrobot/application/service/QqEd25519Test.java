package online.yudream.plugin.qqrobot.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqEd25519Test {
    /** 官方「回调地址验证」示例：secret / plain_token / event_ts / 期望签名。 */
    private static final String SECRET = "DG5g3B4j9X2KOErG";
    private static final String PLAIN_TOKEN = "Arq0D5A61EgUu4OxUvOp";
    private static final String EVENT_TS = "1725442341";

    @Test
    void shortSecretIsDoubledUntilThirtyTwoBytes() {
        assertEquals(32, QqEd25519.seed("abc").length);
        assertTrue(new String(QqEd25519.seed("abc"), StandardCharsets.UTF_8).startsWith("abcabc"));
    }

    @Test
    void longSecretIsTruncatedToThirtyTwoBytes() {
        String secret = "0123456789abcdef0123456789abcdefEXTRA";
        assertEquals("0123456789abcdef0123456789abcdef",
                new String(QqEd25519.seed(secret), StandardCharsets.UTF_8));
    }

    @Test
    void blankSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QqEd25519.seed(" "));
        assertThrows(IllegalArgumentException.class, () -> QqEd25519.seed(null));
    }

    @Test
    void seedMatchesRfc8032Vector() {
        byte[] seed = HexFormat.of().parseHex(
                "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
        String publicKey = HexFormat.of().formatHex(
                QqEd25519.keyPairFromSeed(seed).getPublic().getEncoded());
        // RFC 8032 §7.1 TEST 1 的公钥。
        assertTrue(publicKey.endsWith("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"));
    }

    @Test
    void validationSignatureIsStable() {
        String first = QqEd25519.signValidation(SECRET, EVENT_TS, PLAIN_TOKEN);
        String second = QqEd25519.signValidation(SECRET, EVENT_TS, PLAIN_TOKEN);

        assertEquals(first, second);
        assertEquals(128, first.length());
        assertTrue(first.matches("[0-9a-f]{128}"));
    }

    @Test
    void validationSignatureMatchesOfficialSample() {
        String expected = "87befc99c42c651b3aac0278e71ada338433ae26fcb24307bdc5ad38c1adc2d0"
                + "1bcfcadc0842edac85e85205028a1132afe09280305f13aa6909ffc2d652c706";

        assertEquals(expected, QqEd25519.signValidation(SECRET, EVENT_TS, PLAIN_TOKEN));
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        assertTrue(!QqEd25519.signValidation(SECRET, EVENT_TS, PLAIN_TOKEN)
                .equals(QqEd25519.signValidation(SECRET + "x", EVENT_TS, PLAIN_TOKEN)));
    }

    @Test
    void signatureCoversTimestampAndTokenOrder() {
        assertTrue(!QqEd25519.sign(SECRET, EVENT_TS + PLAIN_TOKEN)
                .equals(QqEd25519.sign(SECRET, PLAIN_TOKEN + EVENT_TS)));
    }
}
