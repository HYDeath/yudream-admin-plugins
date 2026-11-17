package online.yudream.plugin.qqrobot.bootstrap;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.messaging.PluginInteractionFilter;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageHandler;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageInteractionRegistry;
import online.yudream.plugin.qqrobot.application.service.QqBotService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqRobotPluginTest {
    @Test
    void subscribesToLeaveOnCurrentMessageAndFutureNativeChannelsWithDisposal() throws Exception {
        Map<String, PluginInteractionFilter> filters = new LinkedHashMap<>();
        Map<String, PluginMessageHandler> handlers = new LinkedHashMap<>();
        List<AutoCloseable> disposables = new ArrayList<>();
        List<String> closed = new ArrayList<>();
        var interactions = (PluginMessageInteractionRegistry) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PluginMessageInteractionRegistry.class},
                (proxy, method, args) -> {
                    filters.put(method.getName(), (PluginInteractionFilter) args[0]);
                    handlers.put(method.getName(), (PluginMessageHandler) args[1]);
                    return (AutoCloseable) () -> closed.add(method.getName());
                });
        var context = (PluginContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginContext.class}, (proxy, method, args) -> {
                    if ("interactions".equals(method.getName())) return interactions;
                    if ("onDispose".equals(method.getName())) {
                        disposables.add((AutoCloseable) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        List<PluginEvent> received = new ArrayList<>();
        var bot = new QqBotService(null, null, null, null) {
            @Override public void onEvent(PluginEvent event) { received.add(event); }
        };
        QqRobotPlugin.registerInteractions(context, bot);
        PluginEvent leave = new PluginEvent("1", "group_member_decrease", "milky", "target", "group",
                null, null, null, Map.of(), "group_member_decrease", Map.of(), "connection", "bot", "event");

        for (String channel : List.of("onMessage", "onNative")) {
            assertTrue(filters.get(channel).eventTypes().contains(leave.type()));
            handlers.get(channel).handle(leave);
        }
        assertEquals(List.of(leave, leave), received);
        assertTrue(filters.get("onMessage").eventTypes().contains("message_receive"));
        assertTrue(filters.get("onNative").eventTypes().contains("group_request"));
        assertEquals(2, disposables.size());
        for (AutoCloseable disposable : disposables) disposable.close();
        assertEquals(List.of("onMessage", "onNative"), closed);
    }
}
