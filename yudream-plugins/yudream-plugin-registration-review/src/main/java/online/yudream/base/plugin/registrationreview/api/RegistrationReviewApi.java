package online.yudream.base.plugin.registrationreview.api;

import java.util.Optional;

public interface RegistrationReviewApi {
    record Admission(String userId, String status, boolean passed, boolean approved,
                     String mode, long updatedAt) {}

    Optional<Admission> admission(String userId);
}
