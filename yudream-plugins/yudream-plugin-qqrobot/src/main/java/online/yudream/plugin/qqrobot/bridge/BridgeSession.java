package online.yudream.plugin.qqrobot.bridge;

import online.yudream.base.plugin.spi.ws.PluginWsSession;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个 Minecraft 服务端连接。会话级状态（在线人数、TPS、资源包版本）
 * 只保存在内存，重连后由服务端重新上报。
 */
public final class BridgeSession {
    private final PluginWsSession socket;
    private final String serverId;
    private final String serverName;
    private final Instant connectedAt;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private volatile Instant lastSeen = Instant.now();

    public BridgeSession(PluginWsSession socket, String serverId, String serverName) {
        this.socket = socket;
        this.serverId = serverId;
        this.serverName = serverName;
        this.connectedAt = Instant.now();
    }

    public String id() {
        return socket.id();
    }

    public String serverId() {
        return serverId;
    }

    public String serverName() {
        return serverName;
    }

    public Instant connectedAt() {
        return connectedAt;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public void touch() {
        lastSeen = Instant.now();
    }

    public boolean open() {
        return socket.isOpen();
    }

    public void send(String payload) {
        if (socket.isOpen()) {
            socket.sendText(payload);
        }
    }

    public void close(int status, String reason) {
        socket.close(status, reason);
    }

    public void state(String key, Object value) {
        if (value == null) {
            state.remove(key);
        } else {
            state.put(key, value);
        }
    }

    public Object state(String key) {
        return state.get(key);
    }

    public Map<String, Object> stateSnapshot() {
        return Map.copyOf(state);
    }
}
