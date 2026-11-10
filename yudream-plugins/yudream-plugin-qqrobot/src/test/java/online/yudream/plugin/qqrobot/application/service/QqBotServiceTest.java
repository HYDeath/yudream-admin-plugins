package online.yudream.plugin.qqrobot.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.QqBotSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqBotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MemoryStore store = new MemoryStore();
    private final SettingsService settings = new SettingsService(store);
    private final BindCodeService codes = new BindCodeService();
    private final NoticeService notices = new NoticeService(store);
    private final QqBotService bot = new QqBotService(settings, codes, notices, null);

    @BeforeEach
    void enableBot() {
        QqBotSettings defaults = QqBotSettings.defaults();
        settings.saveBotSettings(new QqBotSettings(true, "102000000", defaults.appSecretRef(),
                defaults.webhookPath(), defaults.apiBase(), true, true, true,
                defaults.friendAddMessage(), defaults.groupAddMessage(), true));
    }

    @Test
    void disabledBotIgnoresEvents() throws Exception {
        QqBotSettings current = settings.botSettings();
        settings.saveBotSettings(new QqBotSettings(false, current.appId(), current.appSecretRef(),
                current.webhookPath(), current.apiBase(), true, true, true,
                current.friendAddMessage(), current.groupAddMessage(), true));

        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"FRIEND_ADD","openid":"A1B2C3D4E5F6A1B2"}
                """));

        assertEquals("", result.get("reply"));
        assertEquals("机器人未启用", result.get("ignored"));
    }

    @Test
    void friendAddReturnsBindingGuide() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"FRIEND_ADD","openid":"A1B2C3D4E5F6A1B2","timestamp":1784570523,
                 "scene":2003,"scene_param":"callback_abc",
                 "author":{"union_openid":"DB85A74E07BA08B5"}}
                """));

        assertEquals("FRIEND_ADD", result.get("event"));
        assertEquals("A1B2C3D4E5F6A1B2", result.get("openid"));
        assertTrue(String.valueOf(result.get("reply")).contains("绑定"));
        assertEquals(1, notices.active().size());
        assertTrue(String.valueOf(notices.active().getFirst().get("message")).contains("A1B2…A1B2"));
    }

    @Test
    void friendAddFallsBackToUnionOpenid() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"FRIEND_ADD","author":{"union_openid":"DB85A74E07BA08B5"}}
                """));
        assertEquals("DB85A74E07BA08B5", result.get("openid"));
    }

    @Test
    void privateMessageIssuesBindCode() throws Exception {
        settings.saveBindSettings(new BindSettings("dual", List.of(), 2, 5, 4, false,
                true, true, "", "numeric", "绑定", "解绑", "", "", "", "", "", List.of()));
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-1","content":"绑定 Steve"}
                """));
        String reply = String.valueOf(result.get("reply"));
        assertTrue(reply.contains("验证码"));
        assertTrue(reply.contains("5 分钟"));
    }

    /** 官方回调把事件体包在 d 里；这里必须和真实 payload 一样能命中指令。 */
    @Test
    void officialEnvelopeReadsContentFromDataNode() throws Exception {
        settings.saveBindSettings(new BindSettings("dual", List.of(), 2, 5, 4, false,
                true, true, "", "numeric", "绑定", "解绑", "", "", "", "", "", List.of()));
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","id":"event-1","d":{
                  "id":"msg-1","content":"绑定 Steve","timestamp":"2026-11-10T00:40:10+08:00",
                  "author":{"user_openid":"OPENID-NESTED"}}}
                """));
        assertEquals("OPENID-NESTED", result.get("openid"));
        assertTrue(String.valueOf(result.get("reply")).contains("验证码"));
    }

    @Test
    void officialEnvelopeReadsGroupMemberOpenid() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"msg-2","content":" 在线","group_openid":"GROUP-1",
                  "author":{"member_openid":"MEMBER-1"}}}
                """));
        assertEquals("MEMBER-1", result.get("openid"));
        assertFalse(String.valueOf(result.get("reply")).isBlank());
    }

    @Test
    void chinesePlayerNameIsRejected() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-1","content":"绑定 史蒂夫"}
                """));
        assertTrue(String.valueOf(result.get("reply")).contains("中文"));
    }

    @Test
    void openidMaskKeepsBothEnds() {
        assertEquals("", QqBotService.mask(""));
        assertEquals("ABC", QqBotService.mask("ABC"));
    }

    @Test
    void myInventoryUsesBoundPlayerWithoutName() throws Exception {
        BindingService bindings = new BindingService(store, settings);
        bindings.bind("OPENID-BOUND", "小明", "Steve", "survival-a");
        QqBotService bound = new QqBotService(settings, codes, notices, null, bindings,
                name -> Optional.empty(), new QqApiClient());

        Map<String, Object> result = bound.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-BOUND","content":"我的背包"}
                """));

        String reply = String.valueOf(result.get("reply"));
        // 没有桥接时回桥接未就绪；关键是它没有要求再带玩家名。
        assertTrue(reply.contains("桥接") || reply.contains("Steve"), reply);
        assertFalse(reply.contains("请发送「背包 玩家ID"), reply);
    }

    @Test
    void myInventoryWithoutBindingAsksToBind() throws Exception {
        BindingService bindings = new BindingService(store, settings);
        QqBotService bound = new QqBotService(settings, codes, notices, null, bindings,
                name -> Optional.empty(), new QqApiClient());

        Map<String, Object> result = bound.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-NONE","content":"我的背包"}
                """));

        assertTrue(String.valueOf(result.get("reply")).contains("绑定"));
    }

    @Test
    void groupMemberLeaveCanUnbindAllAccounts() {
        settings.saveBindSettings(new BindSettings("dual", List.of(), 3, 5, 4, true,
                true, false, "", "numeric", "绑定", "解绑", "", "", "", "", "", List.of(),
                true, true, true, true, true, true, List.of()));
        BindingService bound = new BindingService(store, settings);
        bound.bind("OPENID-LEAVE", "User", "Steve", "survival");
        bound.bind("OPENID-LEAVE", "User", "Alex", "survival");
        QqBotService service = new QqBotService(settings, codes, notices, null, bound,
                name -> Optional.empty(), new QqApiClient());
        service.onEvent(new PluginEvent("1", "group_member_decrease", "milky", "OPENID-LEAVE", "group-1",
                null, null, null, Map.of(), "group_member_decrease",
                Map.of("group_id", "group-1", "user_id", "OPENID-LEAVE"), "connection-1", "bot-1", "event-1"));

        assertTrue(bound.bySocial("OPENID-LEAVE", 1, 20).isEmpty());
    }

    private static final class MemoryStore implements PluginDocumentStore {
        private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

        @Override
        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            values.put(collection + "/" + id, document);
            return document;
        }

        @Override
        public Optional<Map<String, Object>> findById(String collection, String id) {
            return Optional.ofNullable(values.get(collection + "/" + id));
        }

        @Override
        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(collection + "/"))
                    .map(Map.Entry::getValue).toList();
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value,
                                                     int page, int size) {
            return findAll(collection, page, size);
        }

        @Override
        public long count(String collection) {
            return findAll(collection, 1, 100).size();
        }

        @Override
        public void delete(String collection, String id) {
            values.remove(collection + "/" + id);
        }
    }
}
