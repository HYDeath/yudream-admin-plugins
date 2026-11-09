package online.yudream.plugin.qqrobot.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSettingsTest {
    @Test
    void documentRoundTripKeepsTriggers() {
        CommandSettings saved = new CommandSettings(false, List.of("#"), true, List.of(
                new CommandSettings.CommandBinding(CommandSettings.ACTION_SHULKER, "潜影盒",
                        List.of("潜影盒", "SB"), true, List.of("group", "game"), "渲染中")));

        CommandSettings loaded = CommandSettings.fromDocument(saved.toDocument());
        CommandSettings.CommandBinding shulker = loaded.command(CommandSettings.ACTION_SHULKER);

        assertEquals(List.of("潜影盒", "sb"), shulker.triggers());
        assertTrue(shulker.supports("game"));
        assertEquals(List.of("#", "/"), loaded.prefixes());
    }

    @Test
    void missingActionsAreFilledWithDefaults() {
        CommandSettings loaded = CommandSettings.fromDocument(Map.of(
                "commands", List.of(Map.of("action", "inventory", "triggers", List.of("背包")))));

        assertEquals(CommandSettings.ACTIONS.size(), loaded.commands().size());
        assertTrue(loaded.command(CommandSettings.ACTION_BIND).enabled());
    }

    @Test
    void unknownActionsAreDropped() {
        CommandSettings loaded = CommandSettings.fromDocument(Map.of(
                "commands", List.of(Map.of("action", "rm -rf", "triggers", List.of("x")))));

        assertFalse(loaded.commands().stream().anyMatch(command -> command.action().equals("rm -rf")));
    }

    @Test
    void scopesDefaultToPrivateAndGroup() {
        CommandSettings.CommandBinding binding = new CommandSettings.CommandBinding(
                "inventory", "背包", List.of("背包"), true, List.of("nope"), "").normalized();

        assertEquals(List.of("private", "group"), binding.scopes());
    }
}
