package online.yudream.plugin.qqrobot.application.service;

import online.yudream.plugin.qqrobot.domain.BindSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindCodeServiceTest {
    @Test
    void codeIsSingleUseAndScopedToPlayerAndServer() {
        BindCodeService service = new BindCodeService();
        BindSettings settings = BindSettings.defaults();
        BindCodeService.PendingCode code = service.issue("Steve", "survival", "10001", "Steve", settings);

        assertEquals(4, code.code().length());
        assertEquals("survival", code.serverId());
        assertTrue(service.take(code.code()).isPresent());
        assertFalse(service.take(code.code()).isPresent());
    }

    @Test
    void issuingForSamePlayerReplacesPreviousCode() {
        BindCodeService service = new BindCodeService();
        BindSettings settings = BindSettings.defaults();
        String first = service.issue("Alex", "survival", "1", "Alex", settings).code();
        String second = service.issue("Alex", "survival", "1", "Alex", settings).code();

        assertFalse(service.peek(first).isPresent());
        assertTrue(service.peek(second).isPresent());
        assertEquals(1, service.pendingCount());
    }

    @Test
    void alnumCodesUseConfiguredLength() {
        BindCodeService service = new BindCodeService();
        BindSettings settings = new BindSettings("dual", java.util.List.of(), 2, 5, 8, false,
                true, true, "", "alnum", "绑定", "解绑", "a", "b", "c", "d", "e", java.util.List.of()).normalized();
        String code = service.issue("Notch", "survival", "1", "Notch", settings).code();

        assertEquals(8, code.length());
        assertTrue(code.chars().allMatch(character -> Character.isDigit(character) || Character.isLetter(character)));
    }
}
