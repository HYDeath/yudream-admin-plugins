package online.yudream.plugin.schematic;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.messaging.*;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import online.yudream.plugin.schematic.application.SchematicJobs;
import online.yudream.plugin.schematic.interfaces.SchematicMessages;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class GroupFileFlowTest {
    @Test void groupFileResolvesDownloadRendersRepliesAndDeduplicatesTheEvent() throws Exception {
        var outgoing = new CopyOnWriteArrayList<PluginMessageRequest>(); var replied = new CountDownLatch(1);
        var calls = new AtomicInteger(); var downloads = new AtomicInteger();
        var messaging = proxy(PluginMessagingService.class, (method, args) -> {
            var request = (PluginMessageRequest) args[0]; outgoing.add(request);
            if (request.content().type() == PluginMessageContent.Type.TEXT) replied.countDown();
            return CompletableFuture.completedFuture(new PluginMessageResult(List.of("reply"), false, false));
        });
        PluginMessagingRawService raw = (connection, method, payload) -> {
            calls.incrementAndGet(); assertEquals("get_group_file_download_url", method); assertEquals("123456", payload.get("group_id"));
            assertEquals("file-1", payload.get("file_id")); return CompletableFuture.completedFuture(Map.of("download_url", "https://files.qq.com/fixture"));
        };
        var users = proxy(PluginUserService.class, (method, args) -> Optional.empty());
        var framework = proxy(FrameworkServices.class, (method, args) -> switch (method) { case "messaging" -> messaging; case "messagingRaw" -> raw; case "users" -> users; default -> throw new UnsupportedOperationException(method); });
        var context = proxy(PluginContext.class, (method, args) -> framework);
        var documents = new JobsTest.Documents();
        try (var jobs = new SchematicJobs(documents, new JobsTest.Files(), model -> new byte[]{1,2,3});
             var handler = new SchematicMessages(context, jobs, url -> { downloads.incrementAndGet(); assertEquals("https://files.qq.com/fixture",url); return NbtFixtures.structure(); })) {
            var event = event(Map.of()); handler.onMessage(event); handler.onMessage(event);
            assertTrue(replied.await(5, TimeUnit.SECONDS)); assertEquals(1, calls.get()); assertEquals(1, downloads.get()); assertEquals(2,outgoing.size());
            assertEquals(PluginMessageContent.Type.IMAGE, outgoing.getFirst().content().type());
            assertEquals("base64://AQID",outgoing.getFirst().content().content());
            assertTrue(outgoing.get(1).content().content().contains("工具箱分享码"));
            assertTrue(outgoing.get(1).content().content().contains("3839"));
            assertEquals("group",outgoing.getFirst().content().referrer().get("message_scene"));
            assertEquals("msg-1",outgoing.getFirst().content().referrer().get("msg_id"));
            assertEquals("READY", ((Map<?,?>)((List<?>)jobs.page("",true,1,12).get("records")).getFirst()).get("status"));
            handler.onMessage(event(Map.of("sandboxSessionId","sandbox-only")));
            assertEquals(1, downloads.get());
        }
    }
    private static PluginEvent event(Map<String,Object> referrer) {
        return new PluginEvent("seq-1","message_receive","milky","777","123456","",null,null,referrer,"message_receive",Map.of("message_scene","group","msg_id","msg-1","segments",List.of(Map.of("type","file","data",Map.of("file_name","玩家随意命名且没有后缀","file_id","file-1")))),"connection-1","888","msg-1");
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, BiFunction<String,Object[],Object> action) { return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(instance,method,args)-> action.apply(method.getName(),args)); }
}
