package online.yudream.plugin.qqrobot.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 指令配置。
 *
 * <p>这些指令同时用于 QQ 私聊/群聊和服务端聊天栏，因此触发词只是“词”，
 * 是否要求前缀由 {@link #prefixRequired()} 决定：</p>
 * <ul>
 *   <li>{@code /} 开头的消息在游戏内由服务端插件按原生命令处理，不需要这里匹配；</li>
 *   <li>群聊里常见的写法是「绑定 Steve」「背包」或「/背包」，两种都接受；</li>
 *   <li>同一个动作可以有多个触发词（别名），例如「背包 / 查看背包 / bag」。</li>
 * </ul>
 *
 * <p>配置项按“动作”聚合，而不是按“入口”聚合：一条「背包」同时作用于
 * QQ 私聊、群聊和游戏内，避免三份重复配置互相打架。</p>
 */
public record CommandSettings(
        boolean prefixRequired,
        List<String> extraPrefixes,
        boolean allowSlash,
        List<CommandBinding> commands
) {
    /** 可用的动作。新增动作时在这里登记，前端据此渲染配置卡片。 */
    public static final String ACTION_BIND = "bind";
    public static final String ACTION_UNBIND = "unbind";
    public static final String ACTION_INVENTORY = "inventory";
    public static final String ACTION_ENDER_CHEST = "ender_chest";
    public static final String ACTION_SHULKER = "shulker";
    public static final String ACTION_ONLINE = "online";
    public static final String ACTION_PLAYER = "player";
    public static final String ACTION_REGISTRATION = "registration";

    public static final List<String> ACTIONS = List.of(
            ACTION_BIND, ACTION_UNBIND, ACTION_INVENTORY, ACTION_ENDER_CHEST,
            ACTION_SHULKER, ACTION_ONLINE, ACTION_PLAYER, ACTION_REGISTRATION);

    public static CommandSettings defaults() {
        return new CommandSettings(false, List.of(), true, List.of(
                new CommandBinding(ACTION_BIND, "绑定", List.of("绑定", "bind"), true,
                        List.of("private", "group", "game"), "正在为你生成绑定码…"),
                new CommandBinding(ACTION_UNBIND, "解绑", List.of("解绑", "unbind"), true,
                        List.of("private", "group", "game"), "已处理你的解绑请求。"),
                new CommandBinding(ACTION_INVENTORY, "背包", List.of("背包", "我的背包", "bag", "inventory"), true,
                        List.of("private", "group", "game"), "正在渲染背包，请稍候…"),
                new CommandBinding(ACTION_ENDER_CHEST, "末影箱", List.of("末影箱", "我的末影箱", "enderchest", "ec"), true,
                        List.of("private", "group", "game"), "正在渲染末影箱，请稍候…"),
                new CommandBinding(ACTION_SHULKER, "潜影盒", List.of("潜影盒", "我的潜影盒", "shulker", "sb"), true,
                        List.of("private", "group", "game"), "正在渲染潜影盒，请稍候…"),
                new CommandBinding(ACTION_ONLINE, "在线", List.of("在线", "在线列表", "list"), true,
                        List.of("private", "group"), "正在统计在线玩家…"),
                new CommandBinding(ACTION_PLAYER, "查询", List.of("查询", "玩家", "card"), true,
                        List.of("private", "group"), "正在生成玩家信息卡…"),
                new CommandBinding(ACTION_REGISTRATION, "注册审核",
                        List.of("注册", "审核", "registration", "apply"), true,
                        List.of("private", "group"), "正在查询注册审核状态…")
        ));
    }

    public CommandSettings normalized() {
        List<CommandBinding> source = commands == null || commands.isEmpty()
                ? defaults().commands() : commands;
        List<CommandBinding> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (CommandBinding command : source) {
            if (command == null) {
                continue;
            }
            CommandBinding value = command.normalized();
            if (value.action().isBlank() || !ACTIONS.contains(value.action()) || !seen.add(value.action())) {
                continue;
            }
            normalized.add(value);
        }
        // 缺失的动作补默认值，保证升级后旧配置也能直接用新指令。
        for (CommandBinding fallback : defaults().commands()) {
            if (seen.add(fallback.action())) {
                normalized.add(fallback);
            }
        }
        return new CommandSettings(
                prefixRequired,
                extraPrefixes == null ? List.of() : extraPrefixes.stream()
                        .filter(prefix -> prefix != null && !prefix.isBlank())
                        .map(String::trim).distinct().limit(8).toList(),
                allowSlash,
                List.copyOf(normalized)
        );
    }

    public CommandBinding command(String action) {
        return normalized().commands().stream()
                .filter(command -> command.action().equals(action))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知指令动作：" + action));
    }

    /** 所有可用于匹配的前缀；空集合表示不要求前缀。 */
    public List<String> prefixes() {
        CommandSettings value = normalized();
        List<String> result = new ArrayList<>(value.extraPrefixes());
        if (value.allowSlash() && !result.contains("/")) {
            result.add("/");
        }
        return result;
    }

    public Map<String, Object> toDocument() {
        CommandSettings value = normalized();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("prefixRequired", value.prefixRequired());
        document.put("extraPrefixes", value.extraPrefixes());
        document.put("allowSlash", value.allowSlash());
        document.put("commands", value.commands().stream().map(CommandBinding::toDocument).toList());
        return document;
    }

    public static CommandSettings fromDocument(Map<String, Object> document) {
        CommandSettings base = defaults();
        if (document == null) {
            return base;
        }
        List<CommandBinding> commands = new ArrayList<>();
        for (Map<String, Object> row : Documents.maps(document, "commands")) {
            commands.add(CommandBinding.fromDocument(row));
        }
        return new CommandSettings(
                Documents.bool(document, "prefixRequired", base.prefixRequired()),
                Documents.strings(document, "extraPrefixes"),
                Documents.bool(document, "allowSlash", base.allowSlash()),
                commands
        ).normalized();
    }

    /**
     * 单个动作的触发配置。
     *
     * @param action    动作标识，见本类的 {@code ACTION_*} 常量
     * @param label     展示名，用于消息与后台列表
     * @param triggers  触发词，第一个作为主词
     * @param enabled   是否启用
     * @param scopes    生效范围：{@code private}、{@code group}、{@code game}
     * @param replyHint 触发后回给用户的提示（QQ 侧）
     */
    public record CommandBinding(
            String action,
            String label,
            List<String> triggers,
            boolean enabled,
            List<String> scopes,
            String replyHint
    ) {
        private static final List<String> SCOPES = List.of("private", "group", "game");

        public CommandBinding normalized() {
            List<String> normalizedTriggers = triggers == null ? List.of() : triggers.stream()
                    .filter(trigger -> trigger != null && !trigger.isBlank())
                    .map(trigger -> trigger.trim().toLowerCase(Locale.ROOT))
                    .distinct().limit(8).toList();
            List<String> filtered = scopes == null ? List.of()
                    : scopes.stream().filter(SCOPES::contains).distinct().toList();
            List<String> normalizedScopes = filtered.isEmpty() ? List.of("private", "group") : filtered;
            return new CommandBinding(
                    action == null ? "" : action.trim(),
                    label == null || label.isBlank() ? (normalizedTriggers.isEmpty() ? "" : normalizedTriggers.getFirst())
                            : label.trim(),
                    normalizedTriggers,
                    enabled,
                    normalizedScopes,
                    replyHint == null ? "" : replyHint
            );
        }

        public boolean supports(String scope) {
            return normalized().scopes().contains(scope);
        }

        /** 匹配触发词，返回剩余参数（例如「潜影盒 2」→「2」）；不匹配返回 null。 */
        public String match(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            String lower = value.toLowerCase(Locale.ROOT);
            String best = null;
            for (String trigger : normalized().triggers()) {
                if (!lower.equals(trigger) && !lower.startsWith(trigger + " ")
                        && !lower.startsWith(trigger + "　")) {
                    continue;
                }
                // 多别名时优先匹配更长的，避免「潜影」抢先于「潜影盒」。
                if (best == null || trigger.length() > best.length()) {
                    best = trigger;
                }
            }
            return best == null ? null : value.substring(best.length()).trim();
        }

        public Map<String, Object> toDocument() {
            CommandBinding value = normalized();
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("action", value.action());
            document.put("label", value.label());
            document.put("triggers", value.triggers());
            document.put("enabled", value.enabled());
            document.put("scopes", value.scopes());
            document.put("replyHint", value.replyHint());
            return document;
        }

        public static CommandBinding fromDocument(Map<String, Object> document) {
            return new CommandBinding(
                    Documents.string(document, "action", ""),
                    Documents.string(document, "label", ""),
                    Documents.strings(document, "triggers"),
                    Documents.bool(document, "enabled", true),
                    Documents.strings(document, "scopes"),
                    Documents.string(document, "replyHint", "")
            ).normalized();
        }
    }
}
