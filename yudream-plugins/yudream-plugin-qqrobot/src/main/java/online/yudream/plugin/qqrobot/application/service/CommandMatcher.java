package online.yudream.plugin.qqrobot.application.service;

import online.yudream.plugin.qqrobot.domain.CommandSettings;
import online.yudream.plugin.qqrobot.domain.CommandSettings.CommandBinding;

import java.util.Locale;

/**
 * 指令匹配器：把「用户输入的一句话」翻译成「动作 + 参数」。
 *
 * <p>规则刻意做得宽松，因为同一套触发词要同时面对 QQ 群聊、私聊和服务端
 * 聊天栏：</p>
 * <ul>
 *   <li>前缀可选。配置了 {@code prefixRequired} 才强制要求前缀；</li>
 *   <li>{@code /} 始终接受（游戏内习惯写法），除非显式关掉 {@code allowSlash}；</li>
 *   <li>前缀与触发词之间允许空格，例如「# 绑定 Steve」；</li>
 *   <li>参数原样返回，「潜影盒 2」→ {@code 2}，「绑定 Steve」→ {@code Steve}。</li>
 * </ul>
 *
 * <p>没有命中时返回 {@code null}，由调用方决定是否忽略该条消息——机器人
 * 不应该对群里每句话都回一句“未知指令”。</p>
 */
public final class CommandMatcher {
    private final CommandSettings settings;

    public CommandMatcher(CommandSettings settings) {
        this.settings = settings == null ? CommandSettings.defaults() : settings.normalized();
    }

    /**
     * @param text  原始消息内容
     * @param scope {@code private} / {@code group} / {@code game}
     */
    public Match match(String text, String scope) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 200) {
            return null;
        }
        String body = stripPrefix(value);
        if (body == null) {
            return null;
        }
        for (CommandBinding command : settings.commands()) {
            if (!command.enabled() || !command.supports(scope)) {
                continue;
            }
            String args = command.match(body);
            if (args != null) {
                return new Match(command, args);
            }
        }
        return null;
    }

    /** 只匹配指定动作，供服务端同步后按动作找触发词。 */
    public Match matchAction(String action, String text, String scope) {
        Match match = match(text, scope);
        return match != null && match.command().action().equals(action) ? match : null;
    }

    /**
     * 去掉前缀。返回 {@code null} 表示“这条消息不带前缀，但配置要求必须带”。
     */
    private String stripPrefix(String value) {
        String matched = null;
        for (String prefix : settings.prefixes()) {
            if (value.regionMatches(true, 0, prefix, 0, prefix.length())
                    && (matched == null || prefix.length() > matched.length())) {
                matched = prefix;
            }
        }
        if (matched == null) {
            return settings.prefixRequired() ? null : value;
        }
        String rest = value.substring(matched.length()).trim();
        return rest.isEmpty() ? null : rest;
    }

    /** 命中结果。{@code args} 已经去掉触发词与前后空白。 */
    public record Match(CommandBinding command, String args) {
        public String action() {
            return command.action();
        }

        public String label() {
            return command.label();
        }

        public int index() {
            if (args == null || args.isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(args.trim().split("\\s+")[0]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        public String lowerArgs() {
            return args == null ? "" : args.toLowerCase(Locale.ROOT);
        }
    }
}
