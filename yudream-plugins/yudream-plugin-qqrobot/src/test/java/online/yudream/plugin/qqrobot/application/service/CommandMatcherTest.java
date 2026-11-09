package online.yudream.plugin.qqrobot.application.service;

import online.yudream.plugin.qqrobot.domain.CommandSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandMatcherTest {
    private final CommandMatcher matcher = new CommandMatcher(CommandSettings.defaults());

    @Test
    void matchesBareKeywordInGroup() {
        CommandMatcher.Match match = matcher.match("背包", "group");

        assertNotNull(match);
        assertEquals(CommandSettings.ACTION_INVENTORY, match.action());
        assertEquals("", match.args());
    }

    @Test
    void slashIsAlwaysAccepted() {
        CommandMatcher.Match match = matcher.match("/背包", "group");

        assertNotNull(match);
        assertEquals(CommandSettings.ACTION_INVENTORY, match.action());
    }

    @Test
    void longestTriggerWins() {
        CommandMatcher.Match match = matcher.match("潜影盒 2", "group");

        assertNotNull(match);
        assertEquals(CommandSettings.ACTION_SHULKER, match.action());
        assertEquals(2, match.index());
    }

    @Test
    void aliasesAreCaseInsensitive() {
        CommandMatcher.Match match = matcher.match("BAG Steve", "private");

        assertNotNull(match);
        assertEquals(CommandSettings.ACTION_INVENTORY, match.action());
        assertEquals("Steve", match.args());
    }

    @Test
    void unknownTextIsIgnored() {
        assertNull(matcher.match("今天天气不错", "group"));
        assertNull(matcher.match("绑", "group"));
    }

    @Test
    void scopeLimitsMatching() {
        CommandSettings onlyPrivate = new CommandSettings(false, List.of(), true, List.of(
                new CommandSettings.CommandBinding(CommandSettings.ACTION_INVENTORY, "背包",
                        List.of("背包"), true, List.of("private"), "")));

        assertNotNull(new CommandMatcher(onlyPrivate).match("背包", "private"));
        assertNull(new CommandMatcher(onlyPrivate).match("背包", "game"));
    }

    @Test
    void prefixCanBeRequired() {
        CommandSettings required = new CommandSettings(true, List.of("#"), true,
                CommandSettings.defaults().commands());
        CommandMatcher strict = new CommandMatcher(required);

        assertNull(strict.match("背包", "group"));
        assertNotNull(strict.match("# 背包", "group"));
        assertNotNull(strict.match("/背包", "group"));
    }

    @Test
    void disabledCommandNeverMatches() {
        CommandSettings disabled = new CommandSettings(false, List.of(), true, List.of(
                new CommandSettings.CommandBinding(CommandSettings.ACTION_INVENTORY, "背包",
                        List.of("背包"), false, List.of("group"), "")));

        assertNull(new CommandMatcher(disabled).match("背包", "group"));
    }
}
