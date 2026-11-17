package online.yudream.base.plugin.registrationreview.support;

import java.util.Optional;
import online.yudream.base.plugin.questionbank.api.QuestionBankApi;
import online.yudream.base.plugin.spi.core.PluginContext;

/** 软依赖定位：每次调用现取，题库插件停用后不会留下旧服务引用。 */
public final class QuestionBankLocator {
    private QuestionBankLocator() {
    }

    public static Optional<QuestionBankApi> find(PluginContext context) {
        if (context == null || !context.dependencyAvailable("questionbank")) {
            return Optional.empty();
        }
        try {
            return context.service("questionbank", QuestionBankApi.class);
        }
        catch (LinkageError | RuntimeException e) {
            return Optional.empty();
        }
    }
}
