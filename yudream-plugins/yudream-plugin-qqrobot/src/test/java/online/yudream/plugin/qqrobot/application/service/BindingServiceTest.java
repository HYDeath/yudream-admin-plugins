package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingServiceTest {
    @Test
    void enforcesPerSocialLimitAndUnbind() {
        InMemoryDocuments documents = new InMemoryDocuments();
        SettingsService settings = new SettingsService(documents);
        settings.saveBindSettings(new BindSettings("dual", List.of(), 1, 5, 4, true,
                true, false, "", "numeric", "绑定", "解绑", "a", "b", "c", "d", "e", List.of()));
        BindingService service = new BindingService(documents, settings);

        service.bind("10001", "Steve", "Steve", "survival");
        QqRobotException error = assertThrows(QqRobotException.class,
                () -> service.bind("10001", "Steve", "Alex", "survival"));
        assertEquals(409, error.status());

        assertTrue(service.unbind("10001", "Steve"));
        assertFalse(service.unbind("10001", "Steve"));
        service.bind("10001", "Steve", "Alex", "survival");
        assertTrue(service.findByPlayer("alex").isPresent());
    }

    @Test
    void rejectsChineseNamesWhenConfigured() {
        InMemoryDocuments documents = new InMemoryDocuments();
        SettingsService settings = new SettingsService(documents);
        BindingService service = new BindingService(documents, settings);

        assertThrows(QqRobotException.class, () -> service.bind("1", "小明", "小明", "survival"));
    }

    @Test
    void unbindAllRemovesEveryPlayerOwnedBySocialIdentity() {
        InMemoryDocuments documents = new InMemoryDocuments();
        SettingsService settings = new SettingsService(documents);
        settings.saveBindSettings(new BindSettings("dual", List.of(), 3, 5, 4, true,
                true, false, "", "numeric", "绑定", "解绑", "a", "b", "c", "d", "e", List.of()));
        BindingService service = new BindingService(documents, settings);

        service.bind("10001", "User", "Steve", "survival");
        service.bind("10001", "User", "Alex", "survival");

        assertEquals(2, service.unbindAll("10001").size());
        assertTrue(service.bySocial("10001", 1, 20).isEmpty());
    }

    @Test
    void unbindAllCollectsEveryHistoricalPageBeforeDeleting() {
        InMemoryDocuments documents = new InMemoryDocuments();
        BindingService service = new BindingService(documents, new SettingsService(documents));
        for (int index = 0; index < 205; index++) {
            seed(documents, "member", "Player" + index, "group", "connection");
        }
        seed(documents, "other", "Keep", "group", "connection");

        assertEquals(205, service.unbindAll("member").size());
        assertTrue(service.bySocial("member", 1, 100).isEmpty());
        assertTrue(service.find("other", "Keep").isPresent());
        assertTrue(service.unbindAll("member").isEmpty());
    }

    @Test
    void officialCleanupScansPastOtherScopesAndPreservesThem() {
        InMemoryDocuments documents = new InMemoryDocuments();
        BindingService service = new BindingService(documents, new SettingsService(documents));
        for (int index = 0; index < 105; index++) {
            seed(documents, "member", "Other" + index, "group", "other-connection");
        }
        for (int index = 0; index < 205; index++) {
            seed(documents, "member", "Target" + index, "group", "connection");
        }
        seed(documents, "member", "OtherGroup", "other-group", "connection");
        seed(documents, "member", "Legacy", "", "");

        assertEquals(205, service.unbindInGroup("member", "connection", "group").size());
        assertEquals(107, documents.count("qqrobot-binding"));
        assertTrue(service.find("member", "Other104").isPresent());
        assertTrue(service.find("member", "OtherGroup").isPresent());
        assertTrue(service.find("member", "Legacy").isPresent());
        assertFalse(service.find("member", "Target204").isPresent());
        assertTrue(service.unbindInGroup("member", "connection", "group").isEmpty());
    }

    private static void seed(InMemoryDocuments documents, String socialId, String player,
                             String group, String connection) {
        var record = new online.yudream.plugin.qqrobot.domain.BindingRecord(
                online.yudream.plugin.qqrobot.domain.BindingRecord.key(socialId, player),
                socialId, "User", player, "server", group, connection, java.time.Instant.now());
        documents.save("qqrobot-binding", record.id(), record.toDocument());
    }

    private static final class InMemoryDocuments implements PluginDocumentStore {
        private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

        @Override
        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            Map<String, Object> stored = new LinkedHashMap<>(document);
            stored.put("id", id);
            values.put(collection + "/" + id, stored);
            return stored;
        }

        @Override
        public Optional<Map<String, Object>> findById(String collection, String id) {
            return Optional.ofNullable(values.get(collection + "/" + id));
        }

        @Override
        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(collection + "/"))
                    .map(Map.Entry::getValue)
                    .skip((long) (page - 1) * size).limit(size).toList();
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(collection + "/"))
                    .map(Map.Entry::getValue)
                    .filter(document -> java.util.Objects.equals(document.get(field), value))
                    .skip((long) (page - 1) * size).limit(size).toList();
        }

        @Override
        public long count(String collection) {
            return values.keySet().stream().filter(key -> key.startsWith(collection + "/")).count();
        }

        @Override
        public void delete(String collection, String id) {
            values.remove(collection + "/" + id);
        }
    }
}
