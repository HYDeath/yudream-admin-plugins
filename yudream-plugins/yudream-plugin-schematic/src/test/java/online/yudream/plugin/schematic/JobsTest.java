package online.yudream.plugin.schematic;
import online.yudream.base.plugin.spi.system.storage.*;
import online.yudream.base.plugin.spi.http.*;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.plugin.schematic.application.SchematicJobs;
import online.yudream.plugin.schematic.interfaces.SchematicHttp;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JobsTest {
    static final class Documents implements PluginDocumentStore {
        final Map<String, Map<String, Map<String, Object>>> data = new ConcurrentHashMap<>();
        public Map<String, Object> save(String c, String id, Map<String, Object> d) { data.computeIfAbsent(c, ignored -> new ConcurrentHashMap<>()).put(id, new LinkedHashMap<>(d)); return d; }
        public Optional<Map<String, Object>> findById(String c, String id) { return Optional.ofNullable(data.getOrDefault(c, Map.of()).get(id)).map(LinkedHashMap::new); }
        public List<Map<String, Object>> findAll(String c, int page, int size) { return data.getOrDefault(c, Map.of()).values().stream().skip((long)(page-1)*size).limit(size).map(d -> (Map<String,Object>)new LinkedHashMap<>(d)).toList(); }
        public List<Map<String, Object>> findByField(String c, String f, Object v, int page, int size) { return data.getOrDefault(c, Map.of()).values().stream().filter(d -> Objects.equals(d.get(f), v)).skip((long)(page-1)*size).limit(size).toList(); }
        public long count(String c) { return data.getOrDefault(c, Map.of()).size(); }
        public void delete(String c, String id) { data.getOrDefault(c, new HashMap<>()).remove(id); }
    }
    static final class Files implements PluginFileStore {
        final Map<String, byte[]> data = new ConcurrentHashMap<>();
        public String put(String key, InputStream input, long length, String contentType) { try { data.put(key, input.readAllBytes()); return key; } catch (IOException e) { throw new UncheckedIOException(e); } }
        public PluginStoredFile get(String key) { return new PluginStoredFile(key, "image/png", (long)data.get(key).length, new ByteArrayInputStream(data.get(key))); }
        public void delete(String key) { data.remove(key); }
    }
    @Test void personalScopeRemainsPersonalEvenForAdminsAndShareIsRevokedOnDelete() throws Exception {
        var documents = new Documents(); var files = new Files(); var finished = new CountDownLatch(1);
        try (var jobs = new SchematicJobs(documents, files, model -> new byte[]{1, 2, 3})) {
            var queued = jobs.submit("user:1", "test.nbt", NbtFixtures.structure(), ignored -> finished.countDown());
            assertTrue(finished.await(5, TimeUnit.SECONDS)); String id = (String)queued.get("id"), token = (String)queued.get("shareToken");
            assertEquals("READY", jobs.get("user:1", id, false).get("status"));
            assertThrows(NoSuchElementException.class, () -> jobs.get("user:2", id, false));
            assertEquals(0L, jobs.page("user:2", false, 1, 12).get("total"));
            assertEquals(1L, jobs.page("user:2", true, 1, 12).get("total"));
            assertFalse(jobs.shared(token).containsKey("owner")); assertArrayEquals(new byte[]{1,2,3}, jobs.preview(token));
            var http = new SchematicHttp(jobs);
            assertEquals(401, http.detail(request(id, null), false).status());
            assertEquals(404, http.detail(request(id, new PluginPrincipal(2L, List.of("*"))), false).status());
            assertEquals(200, http.detail(request(id, new PluginPrincipal(2L, List.of("*"))), true).status());
            assertThrows(NoSuchElementException.class, () -> jobs.delete("user:2", id, false));
            jobs.delete("user:1", id, false);
            assertTrue(files.data.isEmpty()); assertThrows(NoSuchElementException.class, () -> jobs.shared(token));
        }
    }
    @Test void preventsConcurrentOwnerWorkAndPersistsFailureWithoutExposingInternalErrors() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new CountDownLatch(1); var documents = new Documents();
        try (var jobs = new SchematicJobs(documents, new Files(), model -> { started.countDown(); release.await(5, TimeUnit.SECONDS); throw new IOException("private-url-or-credential"); })) {
            var job = jobs.submit("user:1", "a.nbt", NbtFixtures.structure(), ignored -> finished.countDown());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> jobs.submit("user:1", "b.nbt", NbtFixtures.structure(), null));
            assertThrows(IllegalArgumentException.class, () -> jobs.delete("user:1", (String)job.get("id"), false));
            release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS));
            var failed = jobs.get("user:1", (String)job.get("id"), false);
            assertEquals("FAILED", failed.get("status")); assertFalse(failed.get("error").toString().contains("credential"));
        }
    }
    @Test void adminPaginationRecoversInterruptedJobsAfterRestart() {
        var documents = new Documents(); String id = UUID.randomUUID().toString();
        documents.save("jobs", id, Map.of("id", id, "owner", "user:1", "status", "RENDERING", "shareToken", UUID.randomUUID().toString()));
        try (var jobs = new SchematicJobs(documents, new Files(), model -> new byte[0])) {
            var records = (List<?>)jobs.page("user:2", true, 1, 12).get("records");
            assertEquals("FAILED", ((Map<?,?>)records.getFirst()).get("status"));
            jobs.delete("user:2", id, true); assertEquals(0, documents.count("jobs"));
        }
    }
    private PluginHttpRequest request(String id, PluginPrincipal principal) { return new PluginHttpRequest("GET", "/me/jobs/"+id, Map.of(), Map.of(), "", principal); }
}
