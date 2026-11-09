package online.yudream.plugin.qqrobot.application.service;

import online.yudream.plugin.qqrobot.bridge.BridgeHub;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「服务器在线列表」的数据装配。
 *
 * <p>服务器台账不在这里维护：名称、启用状态、子服拓扑来自
 * {@code minecraft-server} 插件的 {@code PluginMinecraftService}。本类只做两件事：</p>
 * <ol>
 *   <li>按模板配置里的保留/排除名单过滤，并决定是否只显示有人的服务器；</li>
 *   <li>把在线人数与玩家名补上。人数优先取服务端桥接的实时快照（它直接来自
 *       Bukkit 的在线列表），桥接没连上时退回 {@code minecraft-server} 的子服在线数。</li>
 * </ol>
 *
 * <p>桥接是可选依赖：没有安装服务端插件时，列表仍然能按子服人数出图，只是没有
 * 玩家名册。</p>
 */
public class ServerListData {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** 由启动阶段注入；{@code null} 表示没有 minecraft-server 插件。 */
    @FunctionalInterface
    public interface ServerDirectory {
        List<ServerEntry> servers();
    }

    public record ServerEntry(String id, String name, String edition, boolean enabled,
                              List<SubServer> subServers) {
        public ServerEntry {
            subServers = subServers == null ? List.of() : List.copyOf(subServers);
        }
    }

    public record SubServer(String name, int online) {
    }

    private final ServerDirectory directory;
    private BridgeHub bridge;

    public ServerListData(ServerDirectory directory) {
        this.directory = directory;
    }

    public void attachBridge(BridgeHub bridge) {
        this.bridge = bridge;
    }

    /**
     * 生成模板变量。{@code settings} 为空时按默认配置出全量列表。
     *
     * @param settings 模板的结构化配置（标题、筛选、显示开关等）
     */
    public Map<String, Object> variables(Map<String, Object> settings) {
        Map<String, Object> config = ServerListSchema.merge(settings, null);
        List<ServerEntry> entries = directory == null || directory.servers() == null
                ? List.of() : directory.servers();
        List<String> included = strings(config.get("includedServers"));
        List<String> excluded = strings(config.get("excludedServers"));
        boolean onlyOnline = flag(config, "showOnlyOnline", true);
        boolean showPlayers = flag(config, "showPlayerList", true);

        List<Map<String, Object>> servers = new ArrayList<>();
        int total = 0;
        for (ServerEntry entry : entries) {
            if (!entry.enabled() || matches(excluded, entry)) {
                continue;
            }
            if (!included.isEmpty() && !matches(included, entry)) {
                continue;
            }
            List<Map<String, Object>> players = showPlayers ? players(entry) : List.of();
            int online = onlineCount(entry, players);
            if (onlyOnline && online <= 0 && players.isEmpty()) {
                continue;
            }
            total += online;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.id());
            row.put("name", entry.name());
            row.put("online", String.valueOf(online));
            row.put("edition", entry.edition());
            row.put("stateClass", online > 0 ? "on" : "off");
            row.put("players", players);
            // 模板里没有 if/else，空名册用一行提示占位，避免空白卡片。
            row.put("emptyHint", players.isEmpty() && showPlayers ? "暂无玩家在线" : "");
            servers.add(row);
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("title", text(config, "title", "服务器在线列表"));
        variables.put("site", text(config, "site", ""));
        variables.put("total", String.valueOf(total));
        variables.put("servers", servers);
        variables.put("updated", LocalTime.now().format(TIME));
        variables.put("status", servers.isEmpty() ? "无在线" : "正常");
        return variables;
    }

    /** 以桥接快照为准补齐玩家名；没有桥接时只保留人数，名册留空。 */
    private List<Map<String, Object>> players(ServerEntry entry) {
        if (bridge == null) {
            return List.of();
        }
        for (Map<String, Object> session : bridge.snapshot()) {
            if (!entry.id().equals(String.valueOf(session.getOrDefault("serverId", "")))) {
                continue;
            }
            Object names = session.get("players");
            if (!(names instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>(list.size());
            for (Object name : list) {
                String player = name == null ? "" : String.valueOf(name).trim();
                if (player.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", player);
                rows.add(row);
            }
            return rows;
        }
        return List.of();
    }

    private static int onlineCount(ServerEntry entry, List<Map<String, Object>> players) {
        if (!players.isEmpty()) {
            return players.size();
        }
        return entry.subServers().stream().mapToInt(SubServer::online).sum();
    }

    private static boolean matches(List<String> keys, ServerEntry entry) {
        return keys.stream().anyMatch(key -> key.equalsIgnoreCase(entry.id())
                || key.equalsIgnoreCase(entry.name()));
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).map(String::trim)
                .filter(item -> !item.isEmpty()).toList();
    }

    private static String text(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static boolean flag(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
