package online.yudream.plugin.schematic;

import online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.plugin.schematic.application.SchematicAdmission;
import online.yudream.plugin.schematic.application.SchematicJobs;
import online.yudream.plugin.schematic.interfaces.SchematicHttp;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SchematicAdmissionTest {
    @Test void onlyAdminApprovalUnlocksUploads() throws Exception {
        assertEquals(true, new SchematicAdmission(context(new Approval("approved", true))).permits("user:1"));
        for (Approval approval : List.of(new Approval("passed", false), new Approval("questionnaire_pending_review", false),
                new Approval("failed", false), new Approval("rejected", false))) {
            assertEquals(false, new SchematicAdmission(context(approval)).permits("user:1"), approval.status());
        }
        assertEquals(false, new SchematicAdmission(noRecord()).permits("user:1"));
        assertEquals(false, new SchematicAdmission(noService()).permits("user:1"));
        assertEquals(false, new SchematicAdmission(noService()).permits("qq:1:2:3"));
        try (var jobs = new SchematicJobs(new JobsTest.Documents(), new JobsTest.Files(), model -> new byte[]{1})) {
            var http = new SchematicHttp(jobs, new SchematicAdmission(noRecord()));
            PluginHttpResponse response = http.upload(upload());
            assertEquals(403, response.status());
            assertTrue(String.valueOf(response.body()).contains("注册审核"));
            assertEquals(0L, jobs.page("user:1", false, 1, 12).get("total"));
        }
    }

    private record Approval(String status, boolean approved) implements RegistrationReviewApi {
        public Optional<Admission> admission(String userId) {
            return Optional.of(new Admission(userId, status, approved || "passed".equals(status), approved, "quiz", 1));
        }
    }

    private static PluginHttpRequest upload() throws Exception {
        return new PluginHttpRequest("POST", "/me/jobs", Map.of(), Map.of(), "",
                Map.of("file", new online.yudream.base.plugin.spi.http.PluginHttpPart("file", "a.nbt", "application/octet-stream", NbtFixtures.structure())),
                new PluginPrincipal(1L, List.of("*")));
    }

    static PluginContext context(RegistrationReviewApi api) {
        return stub(Optional.of(api), Optional.empty());
    }

    static PluginContext noRecord() {
        return stub(Optional.of(userId -> Optional.empty()), Optional.empty());
    }

    static PluginContext noService() {
        return stub(Optional.empty(), Optional.empty());
    }

    static PluginContext failing() {
        return stub(Optional.of(userId -> Optional.empty()), Optional.of(new IllegalStateException("disabled")));
    }

    @SuppressWarnings("unchecked")
    private static PluginContext stub(Optional<RegistrationReviewApi> api, Optional<RuntimeException> failure) {
        return (PluginContext) java.lang.reflect.Proxy.newProxyInstance(PluginContext.class.getClassLoader(), new Class<?>[]{PluginContext.class}, (instance, method, args) -> switch (method.getName()) {
                        case "service" -> {
                failure.ifPresent(error -> { throw error; });
                if (!"registration-review".equals(args[0]) || !RegistrationReviewApi.class.equals(args[1])) yield Optional.empty();
                yield api;
            }
            case "hashCode" -> System.identityHashCode(instance);
            case "equals" -> instance == args[0];
            case "toString" -> "SchematicAdmissionTestContext";
            default -> null;
        });
    }
}
