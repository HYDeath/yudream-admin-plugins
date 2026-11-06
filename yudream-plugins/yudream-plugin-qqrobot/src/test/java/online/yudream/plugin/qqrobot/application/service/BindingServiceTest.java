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
                true, false, "", "numeric", "绑定", "解绑", "a", "b", "c", "d", "e"));
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
                    .map(Map.Entry::getValue).toList();
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
            return findAll(collection, page, size).stream()
                    .filter(document -> String.valueOf(document.get(field)).equals(String.valueOf(value)))
                    .toList();
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
