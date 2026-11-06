package online.yudream.plugin.easybot.application.service;

public class EasyBotException extends RuntimeException {
    private final int status;

    public EasyBotException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static EasyBotException badRequest(String message) {
        return new EasyBotException(400, message);
    }

    public static EasyBotException notFound(String message) {
        return new EasyBotException(404, message);
    }

    public static EasyBotException conflict(String message) {
        return new EasyBotException(409, message);
    }

    /** 依赖的平台能力（如消息渲染）未就绪：前端据此展示前置配置引导，而不是报“操作失败”。 */
    public static EasyBotException unavailable(String message) {
        return new EasyBotException(503, message);
    }
}
