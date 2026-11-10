package online.yudream.plugin.qqrobot.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.plugin.qqrobot.bootstrap.QqRobotPlugin;

public class QqRobotController {
    private static final String MANAGE = QqRobotPlugin.MANAGE_PERMISSION;
    private final QqRobotHttpFacade http;

    public QqRobotController(QqRobotHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/bind", permission = MANAGE)
    public PluginHttpResponse bindSettings() {
        return http.bindSettings();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/bot", permission = MANAGE)
    public PluginHttpResponse botSettings() {
        return http.botSettings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings/bot", permission = MANAGE)
    public PluginHttpResponse saveBotSettings(PluginHttpRequest request) {
        return http.saveBotSettings(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/settings/bot/test", permission = MANAGE)
    public PluginHttpResponse testBot(PluginHttpRequest request) {
        return http.testBot(request);
    }

    /** 机器人接入状态：连接来自宿主「QQ 消息平台」，插件不重复保存凭证。 */
    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/bot/connections", permission = MANAGE)
    public PluginHttpResponse botConnections() {
        return http.botConnections();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings/bind", permission = MANAGE)
    public PluginHttpResponse saveBindSettings(PluginHttpRequest request) {
        return http.saveBindSettings(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/commands", permission = MANAGE)
    public PluginHttpResponse commandSettings() {
        return http.commandSettings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings/commands", permission = MANAGE)
    public PluginHttpResponse saveCommandSettings(PluginHttpRequest request) {
        return http.saveCommandSettings(request);
    }

    /** 指令模拟：只做匹配，不触发绑定/出图等副作用，方便在没有真实 QQ 时验证触发词。 */
    @PluginHttpEndpoint(method = "POST", path = "/admin/settings/commands/test", permission = MANAGE)
    public PluginHttpResponse testCommand(PluginHttpRequest request) {
        return http.testCommand(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/integrations", permission = MANAGE)
    public PluginHttpResponse integrationSettings() {
        return http.integrationSettings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings/integrations", permission = MANAGE)
    public PluginHttpResponse saveIntegrationSettings(PluginHttpRequest request) {
        return http.saveIntegrationSettings(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/groups", permission = MANAGE)
    public PluginHttpResponse groups(PluginHttpRequest request) {
        return http.groups(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/groups", permission = MANAGE)
    public PluginHttpResponse saveGroup(PluginHttpRequest request) {
        return http.saveGroup(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/groups/{groupId}", permission = MANAGE)
    public PluginHttpResponse deleteGroup(PluginHttpRequest request) {
        return http.deleteGroup(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/connections", permission = MANAGE)
    public PluginHttpResponse connections() {
        return http.connections();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/options/groups", permission = MANAGE)
    public PluginHttpResponse optionsGroups(PluginHttpRequest request) {
        return http.optionsGroups(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/resources", permission = "")
    public PluginHttpResponse cards(PluginHttpRequest request) {
        return http.cards(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/resources", permission = MANAGE)
    public PluginHttpResponse saveCard(PluginHttpRequest request) {
        return http.saveCard(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/resources/{id}", permission = MANAGE)
    public PluginHttpResponse deleteCard(PluginHttpRequest request) {
        return http.deleteCard(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/resources/{id}/refresh", permission = MANAGE)
    public PluginHttpResponse refreshCard(PluginHttpRequest request) {
        return http.refreshCard(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/resources/{id}/card.png", permission = "")
    public PluginHttpResponse cardImage(PluginHttpRequest request) {
        return http.cardImage(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/binding-codes", permission = MANAGE)
    public PluginHttpResponse bindingCodes(PluginHttpRequest request) {
        return http.bindingCodes(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/bindings", permission = MANAGE)
    public PluginHttpResponse bindings(PluginHttpRequest request) {
        return http.bindings(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/bindings/{socialId}/{player}", permission = MANAGE)
    public PluginHttpResponse deleteBinding(PluginHttpRequest request) {
        return http.deleteBinding(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/bridge", permission = MANAGE)
    public PluginHttpResponse bridgeState() {
        return http.bridgeState();
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/bridge/rotate-token", permission = MANAGE)
    public PluginHttpResponse rotateToken() {
        return http.rotateToken();
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/bridge/kick", permission = MANAGE)
    public PluginHttpResponse kick(PluginHttpRequest request) {
        return http.kick(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/preview/inventory.png", permission = MANAGE)
    public PluginHttpResponse previewInventory() {
        return http.previewInventory();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/preview/item.png", permission = MANAGE)
    public PluginHttpResponse previewSingle(PluginHttpRequest request) {
        return http.previewItem(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/preview/containers.png", permission = MANAGE)
    public PluginHttpResponse previewContainers() {
        return http.previewContainers();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/render/status", permission = MANAGE)
    public PluginHttpResponse renderStatus() {
        return http.renderStatus();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/templates", permission = MANAGE)
    public PluginHttpResponse templates(PluginHttpRequest request) {
        return http.templates(request);
    }

    /** 模板配置里的服务器筛选：直接列出 minecraft-server 的服务器。 */
    @PluginHttpEndpoint(method = "GET", path = "/admin/servers", permission = MANAGE)
    public PluginHttpResponse servers() {
        return http.servers();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/templates/{id}", permission = MANAGE)
    public PluginHttpResponse template(PluginHttpRequest request) {
        return http.template(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/templates", permission = MANAGE)
    public PluginHttpResponse saveTemplate(PluginHttpRequest request) {
        return http.saveTemplate(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/templates/{id}", permission = MANAGE)
    public PluginHttpResponse deleteTemplate(PluginHttpRequest request) {
        return http.deleteTemplate(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/templates/{id}/cover", permission = MANAGE)
    public PluginHttpResponse uploadTemplateCover(PluginHttpRequest request) {
        return http.uploadTemplateCover(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/templates/{id}/cover", permission = "")
    public PluginHttpResponse templateCover(PluginHttpRequest request) {
        return http.templateCover(request);
    }

    /** 模板出图：传 variables（JSON 对象），返回 PNG。 */
    @PluginHttpEndpoint(method = "POST", path = "/templates/{id}/render", permission = "")
    public PluginHttpResponse renderTemplate(PluginHttpRequest request) {
        return http.renderTemplate(request);
    }

    /** 同上，供后台编辑页用 GET 预览（草稿配置走查询参数不现实，仅支持无草稿预览）。 */
    @PluginHttpEndpoint(method = "GET", path = "/templates/{id}/render", permission = MANAGE)
    public PluginHttpResponse renderTemplatePreview(PluginHttpRequest request) {
        return http.renderTemplatePreview(request);
    }

    /** 桥接服务端上报模板渲染结果（如群在线列表），供 QQ 侧取图片地址。 */
    @PluginHttpEndpoint(method = "POST", path = "/bridge/template-render", permission = MANAGE)
    public PluginHttpResponse bridgeTemplateRender(PluginHttpRequest request) {
        return http.bridgeTemplateRender(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/notices", permission = "")
    public PluginHttpResponse notices() {
        return http.notices();
    }
}
