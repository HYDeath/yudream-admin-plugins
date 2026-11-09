package online.yudream.plugin.qqrobot.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindSettingsTest {
    @Test
    void clampsUnsafeValues() {
        BindSettings value = new BindSettings("unknown", List.of("a", "a"), 0, 0, 2,
                true, true, true, " ", "weird", "", "", "", "", "", "", "", List.of()).normalized();

        assertEquals(BindSettings.MODE_DUAL, value.bindMode());
        assertEquals(1, value.accountsPerSocial());
        assertEquals(1, value.codeTtlMinutes());
        assertEquals(4, value.codeLength());
        assertEquals("numeric", value.codeCharset());
        assertEquals("绑定", value.bindKeyword());
        assertEquals("解绑", value.unbindKeyword());
        assertEquals(List.of("a"), value.forcedServers());
    }

    @Test
    void roundTripsThroughDocument() {
        BindSettings source = new BindSettings("trust", List.of("survival"), 3, 10, 6,
                true, false, false, "EB-", "alnum", "绑定", "解绑",
                "kick", "ok", "fail #why", "bye", "nope #why", List.of("give $player diamond 1")).normalized();
        BindSettings restored = BindSettings.fromDocument(source.toDocument());

        assertEquals(source, restored);
        assertTrue(restored.isForced("survival"));
        assertFalse(restored.isForced("lobby"));
    }

    @Test
    void roundTripsGroupLeaveAndCardRules() {
        BindSettings source = new BindSettings("trust", List.of(), 3, 10, 6,
                true, false, false, "", "numeric", "绑定", "解绑",
                "kick", "ok", "fail", "bye", "nope", List.of(),
                true, false, false, true, false, true, List.of("staff")).normalized();

        BindSettings restored = BindSettings.fromDocument(source.toDocument());

        assertTrue(restored.autoUnbindOnGroupLeave());
        assertFalse(restored.renameOnBindSuccess());
        assertFalse(restored.preventUserRename());
        assertEquals(List.of("staff"), restored.ignoredPermissionGroups());
    }
}
