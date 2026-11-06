package online.yudream.plugin.qqrobot.application.service;

public class QqRobotException extends RuntimeException {
    private final int status;

    public QqRobotException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static QqRobotException badRequest(String message) {
        return new QqRobotException(400, message);
    }

    public static QqRobotException notFound(String message) {
        return new QqRobotException(404, message);
    }

    public static QqRobotException conflict(String message) {
        return new QqRobotException(409, message);
    }

    /** 依赖的平台能力（如消息渲染）未就绪：前端据此展示前置配置引导，而不是报“操作失败”。 */
    public static QqRobotException unavailable(String message) {
        return new QqRobotException(503, message);
    }
}
