package online.yudream.plugin.qqrobot.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.QqBotSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqBotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MemoryStore store = new MemoryStore();
    private final SettingsService settings = new SettingsService(store);
    private final BindCodeService codes = new BindCodeService();
    private final NoticeService notices = new NoticeService(store);
    private final QqBotService bot = new QqBotService(settings, codes, notices, null);

    @BeforeEach
    void enableBot() {
        QqBotSettings defaults = QqBotSettings.defaults();
        settings.saveBotSettings(new QqBotSettings(true, "102000000", defaults.appSecretRef(),
                defaults.webhookPath(), defaults.apiBase(), true, true, true,
                defaults.friendAddMessage(), defaults.groupAddMessage(), true));
    }

    @Test
    void disabledBotIgnoresEvents() throws Exception {
        QqBotSettings current = settings.botSettings();
        settings.saveBotSettings(new QqBotSettings(false, current.appId(), current.appSecretRef(),
                current.webhookPath(), current.apiBase(), true, true, true,
                current.friendAddMessage(), current.groupAddMessage(), true));

        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"FRIEND_ADD","openid":"A1B2C3D4E5F6A1B2"}
                """));

        assertEquals("", result.get("reply"));
        assertEquals("机器人未启用", result.get("ignored"));
    }

    @Test
    void friendAddReturnsBindingGuide() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"FRIEND_ADD","openid":"A1B2C3D4E5F6A1B2","timestamp":1784570523,
                 "scene":2003,"scene_param":"callback_abc",
                 "author":{"union_openid":"DB85A74E07BA08B5"}}
                """));

        assertEquals("FRIEND_ADD", result.get("event"));
        assertEquals("A1B2C3D4E5F6A1B2", result.get("openid"));
        assertTrue(String.valueOf(result.get("reply")).contains("绑定"));
        assertEquals(1, notices.active().size());
        assertTrue(String.valueOf(notices.active().getFirst().get("message")).contains("A1B2…A1B2"));
    }

    @Test
    void friendAddFallsBackToUnionOpenid() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"FRIEND_ADD","author":{"union_openid":"DB85A74E07BA08B5"}}
                """));
        assertEquals("DB85A74E07BA08B5", result.get("openid"));
    }

    @Test
    void privateMessageIssuesBindCode() throws Exception {
        settings.saveBindSettings(new BindSettings("dual", List.of(), 2, 5, 4, false,
                true, true, "", "numeric", "绑定", "解绑", "", "", "", "", "", List.of()));
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-1","content":"绑定 Steve"}
                """));
        String reply = String.valueOf(result.get("reply"));
        assertTrue(reply.contains("验证码"));
        assertTrue(reply.contains("5 分钟"));
    }

    /** 官方回调把事件体包在 d 里；这里必须和真实 payload 一样能命中指令。 */
    @Test
    void officialEnvelopeReadsContentFromDataNode() throws Exception {
        settings.saveBindSettings(new BindSettings("dual", List.of(), 2, 5, 4, false,
                true, true, "", "numeric", "绑定", "解绑", "", "", "", "", "", List.of()));
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","id":"event-1","d":{
                  "id":"msg-1","content":"绑定 Steve","timestamp":"2026-11-10T00:40:10+08:00",
                  "author":{"user_openid":"OPENID-NESTED"}}}
                """));
        assertEquals("OPENID-NESTED", result.get("openid"));
        assertTrue(String.valueOf(result.get("reply")).contains("验证码"));
    }

    @Test
    void officialEnvelopeReadsGroupMemberOpenid() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"msg-2","content":" 在线","group_openid":"GROUP-1",
                  "author":{"member_openid":"MEMBER-1"}}}
                """));
        assertEquals("MEMBER-1", result.get("openid"));
        assertFalse(String.valueOf(result.get("reply")).isBlank());
    }

    @Test
    void chinesePlayerNameIsRejected() throws Exception {
        Map<String, Object> result = bot.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-1","content":"绑定 史蒂夫"}
                """));
        assertTrue(String.valueOf(result.get("reply")).contains("中文"));
    }

    @Test
    void openidMaskKeepsBothEnds() {
        assertEquals("", QqBotService.mask(""));
        assertEquals("ABC", QqBotService.mask("ABC"));
    }

    @Test
    void myInventoryUsesBoundPlayerWithoutName() throws Exception {
        BindingService bindings = new BindingService(store, settings);
        bindings.bind("OPENID-BOUND", "小明", "Steve", "survival-a");
        QqBotService bound = new QqBotService(settings, codes, notices, null, bindings,
                name -> Optional.empty(), new QqApiClient());

        Map<String, Object> result = bound.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-BOUND","content":"我的背包"}
                """));

        String reply = String.valueOf(result.get("reply"));
        // 没有桥接时回桥接未就绪；关键是它没有要求再带玩家名。
        assertTrue(reply.contains("桥接") || reply.contains("Steve"), reply);
        assertFalse(reply.contains("请发送「背包 玩家ID"), reply);
    }

    @Test
    void myInventoryWithoutBindingAsksToBind() throws Exception {
        BindingService bindings = new BindingService(store, settings);
        QqBotService bound = new QqBotService(settings, codes, notices, null, bindings,
                name -> Optional.empty(), new QqApiClient());

        Map<String, Object> result = bound.handle(mapper.readTree("""
                {"t":"C2C_MESSAGE_CREATE","openid":"OPENID-NONE","content":"我的背包"}
                """));

        assertTrue(String.valueOf(result.get("reply")).contains("绑定"));
    }

    @Test
    void groupMemberLeaveUnbindsOnlyThatMembersAccountsAndIsIdempotent() {
        enableAutoUnbind();
        settings.saveGroup(online.yudream.plugin.qqrobot.domain.GroupSettings.defaults("group-1"));
        BindingService bound = new BindingService(store, settings);
        bound.bind("123456789", "User", "Steve", "survival", "group-1", "connection-1");
        bound.bind("123456789", "User", "Alex", "creative", "group-2", "connection-2");
        bound.bind("987654321", "Admin", "Operator", "survival");
        QqBotService service = bindingBot(bound);
        PluginEvent event = leave("123456789", "group-1", "connection-1", "milky");

        service.onEvent(event);
        service.onEvent(event);

        assertTrue(bound.bySocial("123456789", 1, 20).isEmpty());
        assertTrue(bound.find("987654321", "Operator").isPresent());
        assertEquals(1, notices.active().size());
        assertTrue(String.valueOf(notices.active().getFirst().get("message")).contains("2 个游戏账号"));
    }

    @Test
    void officialLeaveDoesNotRemoveOtherConnectionOrGroupBindings() {
        enableAutoUnbind();
        settings.saveGroup(online.yudream.plugin.qqrobot.domain.GroupSettings.defaults("group-1"));
        BindingService bound = new BindingService(store, settings);
        bound.bind("member-open", "User", "Steve", "survival", "group-1", "connection-1");
        bound.bind("member-open", "User", "Alex", "creative", "group-1", "connection-2");
        bound.bind("member-open", "User", "OtherGroup", "survival", "group-2", "connection-1");
        bound.bind("member-open", "User", "Legacy", "survival");
        bindingBot(bound).onEvent(leave("member-open", "group-1", "connection-1", "official"));

        assertFalse(bound.find("member-open", "Steve").isPresent());
        assertTrue(bound.find("member-open", "Alex").isPresent());
        assertTrue(bound.find("member-open", "OtherGroup").isPresent());
        assertTrue(bound.find("member-open", "Legacy").isPresent());
        assertEquals(1, notices.active().size());
    }

    @Test
    void ignoresUnconfiguredDisabledAndIncompleteGroupLeaves() {
        enableAutoUnbind();
        settings.saveGroup(new online.yudream.plugin.qqrobot.domain.GroupSettings(
                "disabled", "", false, true, true, true, "", "", 16));
        BindingService bound = new BindingService(store, settings);
        bound.bind("member", "User", "Steve", "survival");
        QqBotService service = bindingBot(bound);
        for (String group : List.of("unconfigured", "disabled", "")) {
            service.onEvent(leave("member", group, "connection-1", "milky"));
        }
        settings.saveGroup(online.yudream.plugin.qqrobot.domain.GroupSettings.defaults("group-1"));
        service.onEvent(leave("", "group-1", "connection-1", "milky"));
        service.onEvent(leave("member", "group-1", "", "official"));
        assertTrue(bound.find("member", "Steve").isPresent());
        assertTrue(notices.active().isEmpty());
    }

    @Test
    void respectsAutoUnbindAndBotSwitchesAndIgnoresBotItself() {
        settings.saveGroup(online.yudream.plugin.qqrobot.domain.GroupSettings.defaults("group-1"));
        BindingService bound = new BindingService(store, settings);
        bound.bind("member", "User", "Steve", "survival");
        bound.bind("bot-1", "Bot", "BotPlayer", "survival");
        QqBotService service = bindingBot(bound);
        service.onEvent(leave("member", "group-1", "connection-1", "milky"));
        assertTrue(bound.find("member", "Steve").isPresent());
        enableAutoUnbind();
        service.onEvent(leave("bot-1", "group-1", "connection-1", "milky"));
        assertTrue(bound.find("bot-1", "BotPlayer").isPresent());
        var current = settings.botSettings();
        settings.saveBotSettings(new QqBotSettings(false, current.appId(), current.appSecretRef(),
                current.webhookPath(), current.apiBase(), true, true, true,
                current.friendAddMessage(), current.groupAddMessage(), true));
        service.onEvent(leave("member", "group-1", "connection-1", "milky"));
        assertTrue(bound.find("member", "Steve").isPresent());
        assertTrue(notices.active().isEmpty());
    }

    @Test
    void unboundQueriesExplicitlyShowUnboundId() throws Exception {
        BindingService bound = new BindingService(store, settings);
        QqBotService service = bindingBot(bound);
        for (String content : List.of("我的背包", "我的末影箱", "查询")) {
            Map<String, Object> result = service.handle(mapper.readTree("""
                    {"t":"C2C_MESSAGE_CREATE","openid":"NONE","content":"%s"}
                    """.formatted(content)));
            assertTrue(String.valueOf(result.get("reply")).contains("未绑定ID"));
        }
        assertEquals("未绑定ID", bound.describe("NONE"));
    }

    @Test
    void realPlayerCardRenderUsesActualBindingAndReflectsLeaveCleanup() throws Exception {
        enableAutoUnbind();
        settings.saveGroup(online.yudream.plugin.qqrobot.domain.GroupSettings.defaults("group-1"));
        BindingService bound = new BindingService(store, settings);
        java.util.List<String> htmls = new java.util.ArrayList<>();
        java.util.List<String> avatarPlayers = new java.util.ArrayList<>();
        RenderSupport renderer = new RenderSupport(null) {
            @Override
            public online.yudream.base.plugin.spi.system.render.PluginRenderedImage html(
                    String html, String selector, String scene) {
                assertEquals("#template-root", selector);
                htmls.add(html);
                return new online.yudream.base.plugin.spi.system.render.PluginRenderedImage(
                        "image/png", new byte[]{1}, 760, 400);
            }
        };
        AvatarService avatars = new AvatarService(false) {
            @Override public String head(String player) { avatarPlayers.add(player); return ""; }
            @Override public String body(String player) { avatarPlayers.add(player); return ""; }
        };
        TemplateService templates = new TemplateService(store, null, renderer, "/templates", avatars);
        templates.attachBindings(bound);
        templates.seedDefaults();
        var variables = Map.<String, Object>of("openid", "member", "bound", "已绑定 QQ");
        templates.render("builtin-player-card", variables);
        String missingHtml = htmls.getLast();
        assertTrue(missingHtml.contains("未绑定ID"));
        assertFalse(missingHtml.contains("已绑定 QQ"));
        assertFalse(missingHtml.contains("Steve"));
        assertFalse(missingHtml.contains("{{"));
        assertTrue(missingHtml.contains("data:image/svg+xml;base64,"));
        assertTrue(missingHtml.contains("class=\"uuid\">—</div>"));
        assertEquals(3, missingHtml.split("<b>—</b>", -1).length - 1);
        assertTrue(avatarPlayers.isEmpty(), "未绑定占位不能送到头像服务");
        assertEquals(Map.of("openid", "member", "bound", "已绑定 QQ"), variables);
        java.nio.file.Path evidence = java.nio.file.Path.of("target/verification/unbound-player-card.html");
        java.nio.file.Files.createDirectories(evidence.getParent());
        java.nio.file.Files.writeString(evidence, missingHtml);

        bound.bind("member", "User", "Steve", "survival", "group-1", "connection-1");
        templates.render("builtin-player-card", variables);
        assertTrue(htmls.getLast().contains("Steve"));
        assertTrue(htmls.getLast().contains("已绑定 QQ"));
        assertTrue(avatarPlayers.stream().allMatch("Steve"::equals));
        bindingBot(bound).onEvent(leave("member", "group-1", "connection-1", "milky"));
        avatarPlayers.clear();
        templates.render("builtin-player-card", variables);
        assertTrue(htmls.getLast().contains("未绑定ID"));
        assertFalse(htmls.getLast().contains("Steve"));
        assertTrue(avatarPlayers.isEmpty());

        templates.render("builtin-player-card", Map.of("player", "Alex"));
        assertTrue(htmls.getLast().contains("Alex"));
        assertTrue(htmls.getLast().contains("未绑定ID"));
        for (int index = 0; index < 101; index++) {
            bound.bind("other-" + index, "Other", "Player" + index, "survival");
        }
        bound.bind("far-member", "User", "Alex", "survival");
        templates.render("builtin-player-card", Map.of("player", "alex"));
        assertTrue(htmls.getLast().contains("已绑定 QQ"), "超过首页的绑定也应正确显示");
        templates.preview("builtin-player-card");
        assertTrue(htmls.getLast().contains("Steve"));
        assertTrue(htmls.getLast().contains("已绑定 QQ"));
    }

    @Test
    void multiAccountChoiceIsScopedToGroupAndThenOpensSelectedShulker() throws Exception {
        enableAutoUnbind();
        BindingService bindings = new BindingService(store, settings);
        bindings.bind("member", "User", "First", "survival", "group-a", "bot-a");
        bindings.bind("member", "User", "Second", "survival", "group-a", "bot-a");
        QqBotService service = bindingBot(bindings);
        java.util.List<online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest> sent = new java.util.ArrayList<>();
        var messaging = (online.yudream.base.plugin.spi.system.messaging.PluginMessagingService) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{online.yudream.base.plugin.spi.system.messaging.PluginMessagingService.class},
                (proxy, method, args) -> { sent.add((online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest) args[0]); return java.util.concurrent.CompletableFuture.completedFuture(null); });
        service.attachMessaging(messaging);
        java.util.List<String> requested = new java.util.ArrayList<>();
        service.attachBridge(new online.yudream.plugin.qqrobot.bridge.BridgeHub(null, null, null, null, null, null, null) {
            @Override public Optional<PendingRender> requestContainer(String player, String action, int index) {
                requested.add(player + ":" + action + ":" + index);
                var result = mapper.createObjectNode().put("ok", true);
                if (index == 0 && "shulker".equals(action)) result.putArray("choices").add("建材箱 · 背包 1").add("战利品箱 · 背包 5");
                else result.put("image", "base64://AQID");
                return Optional.of(new PendingRender("request", "survival", java.util.concurrent.CompletableFuture.completedFuture(result)));
            }
        });
        service.onEvent(queryEvent("group-a", "潜影盒"));
        assertTrue(sent.getLast().content().content().contains("1. First"));
        service.onEvent(queryEvent("group-b", "2"));
        assertTrue(requested.isEmpty(), "另一个群的序号不能消费当前选择");
        service.onEvent(queryEvent("group-a", "2"));
        assertTrue(sent.getLast().content().content().contains("请选择要打开的潜影盒"));
        service.onEvent(queryEvent("group-a", "1"));
        assertEquals(List.of("Second:shulker:0", "Second:shulker:1"), requested);
        assertEquals(online.yudream.base.plugin.spi.system.messaging.PluginMessageContent.Type.IMAGE, sent.getLast().content().type());
    }

    @Test
    void reviewGroupCannotIssueBindingCodes() throws Exception {
        settings.saveGroup(new online.yudream.plugin.qqrobot.domain.GroupSettings("review", "审核群", true, false, false, false,
                "", "", 16, false, false, true, "欢迎 #mention"));
        var result = bot.handle(mapper.readTree("""
                {"t":"GROUP_AT_MESSAGE_CREATE","d":{"id":"m","content":"绑定 Steve","group_openid":"review","author":{"member_openid":"member"}}}
                """));
        assertTrue(String.valueOf(result.get("reply")).contains("该群不提供账号绑定服务"));
        assertEquals(0, codes.pendingCount());
    }

    private static PluginEvent queryEvent(String group, String content) {
        return new PluginEvent("1", "message_receive", "milky", "member", group, content, null, null,
                Map.of("protocol", "official", "groupOpenid", group, "identityType", "member_openid", "appId", "app"),
                "GROUP_AT_MESSAGE_CREATE", Map.of("message_scene", "group"), "bot-a", "self", "message");
    }

    private void enableAutoUnbind() {
        var document = new LinkedHashMap<>(settings.bindSettings().toDocument());
        document.put("autoUnbindOnGroupLeave", true);
        document.put("accountsPerSocial", 10);
        settings.saveBindSettings(BindSettings.fromDocument(document));
    }

    private QqBotService bindingBot(BindingService bound) {
        return new QqBotService(settings, codes, notices, null, bound, name -> Optional.empty(), new QqApiClient());
    }

    private static PluginEvent leave(String member, String group, String connection, String protocol) {
        return new PluginEvent("1", "group_member_decrease", "milky", member, group,
                null, null, null, Map.of("protocol", protocol), "group_member_decrease",
                Map.of("group_id", group, "user_id", member, "operator_id", "987654321"),
                connection, "bot-1", "event-1");
    }

    private static final class MemoryStore implements PluginDocumentStore {
        private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

        @Override
        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            values.put(collection + "/" + id, document);
            return document;
        }

        @Override
        public Optional<Map<String, Object>> findById(String collection, String id) {
            return Optional.ofNullable(values.get(collection + "/" + id));
        }

        @Override
        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(collection + "/"))
                    .map(Map.Entry::getValue)
                    .skip((long) (Math.max(1, page) - 1) * size).limit(size).toList();
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value,
                                                     int page, int size) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(collection + "/"))
                    .map(Map.Entry::getValue)
                    .filter(document -> java.util.Objects.equals(document.get(field), value))
                    .skip((long) (Math.max(1, page) - 1) * size).limit(size).toList();
        }

        @Override
        public long count(String collection) {
            return findAll(collection, 1, 100).size();
        }

        @Override
        public void delete(String collection, String id) {
            values.remove(collection + "/" + id);
        }
    }
}
