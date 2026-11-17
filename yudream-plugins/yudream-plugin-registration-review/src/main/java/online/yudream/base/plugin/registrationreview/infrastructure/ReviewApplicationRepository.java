package online.yudream.base.plugin.registrationreview.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import online.yudream.base.plugin.registrationreview.domain.DocValues;
import online.yudream.base.plugin.registrationreview.domain.ReviewApplication;
import online.yudream.base.plugin.registrationreview.domain.ReviewStatus;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 审核申请文档仓储。 */
public final class ReviewApplicationRepository {
    public static final String COLLECTION = "registration_review_applications";
    private static final int PAGE_SIZE = 200;

    private final PluginDocumentStore documents;

    public ReviewApplicationRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    public void save(ReviewApplication application) {
        documents.save(COLLECTION, application.id(), application.toDoc());
    }

    public Optional<ReviewApplication> findById(String id) {
        return documents.findById(COLLECTION, id).map(doc -> ReviewApplication.fromDoc(id, doc));
    }

    public List<ReviewApplication> listAll() {
        List<ReviewApplication> result = new ArrayList<>();
        int page = 1;
        while (true) {
            List<java.util.Map<String, Object>> docs = documents.findAll(COLLECTION, page, PAGE_SIZE);
            if (docs == null || docs.isEmpty()) {
                break;
            }
            for (var doc : docs) {
                Object id = doc.get("id");
                if (id == null) {
                    id = doc.get("_id");
                }
                if (id != null) {
                    result.add(ReviewApplication.fromDoc(String.valueOf(id), doc));
                }
            }
            if (docs.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        result.sort(java.util.Comparator.comparingLong(ReviewApplication::createdAt).reversed());
        return result;
    }

    public Optional<ReviewApplication> activeForUser(String userId) {
        return listAll().stream()
                .filter(application -> userId.equals(application.userId()) && application.active())
                .findFirst();
    }

    public long countByUser(String userId) {
        return listAll().stream().filter(application -> userId.equals(application.userId())).count();
    }

    public List<ReviewApplication> listByStatus(ReviewStatus status) {
        return listAll().stream().filter(application -> application.status() == status).toList();
    }

    public void delete(String id) {
        documents.delete(COLLECTION, id);
    }
}
