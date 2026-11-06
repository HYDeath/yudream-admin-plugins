package online.yudream.plugin.easybot.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.plugin.easybot.bootstrap.EasyBotPlugin;

public class EasyBotController {
    private static final String MANAGE = EasyBotPlugin.MANAGE_PERMISSION;
    private final EasyBotHttpFacade http;

    public EasyBotController(EasyBotHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/bind", permission = MANAGE)
    public PluginHttpResponse bindSettings() {
        return http.bindSettings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings/bind", permission = MANAGE)
    public PluginHttpResponse saveBindSettings(PluginHttpRequest request) {
        return http.saveBindSettings(request);
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
    public PluginHttpResponse previewSingle() {
        return http.previewSingle();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/render/status", permission = MANAGE)
    public PluginHttpResponse renderStatus() {
        return http.renderStatus();
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/templates", permission = MANAGE)
    public PluginHttpResponse templates(PluginHttpRequest request) {
        return http.templates(request);
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
