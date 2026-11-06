package online.yudream.plugin.easybot.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.easybot.domain.BindSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqBotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MemoryStore store = new MemoryStore();
    private final SettingsService settings = new SettingsService(store);
    private final BindCodeService codes = new BindCodeService();
    private final NoticeService notices = new NoticeService(store);
    private final QqBotService bot = new QqBotService(settings, codes, notices, null);

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
                true, true, "", "numeric", "绑定", "解绑", "", "", "", "", ""));
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-1","content":"绑定 Steve"}
                """));
        String reply = String.valueOf(result.get("reply"));
        assertTrue(reply.contains("验证码"));
        assertTrue(reply.contains("5 分钟"));
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
