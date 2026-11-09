package online.yudream.plugin.qqrobot.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板可以使用的后端变量目录。
 *
 * <p>模板渲染容器不允许自行发 HTTP 请求，因此这里把可用的数据源明确列给
 * 编辑器；真正的数据仍由插件桥接、服务器台账和绑定服务在渲染前注入。</p>
 */
public final class TemplateApiCatalog {
    private TemplateApiCatalog() {
    }

    public static List<Map<String, Object>> view() {
        return List.of(
                item("servers", "服务器在线状态", "minecraft-server", "服务器名称、在线人数、版本、玩家列表"),
                item("player", "玩家信息", "qq-binding / minecraft-server", "当前玩家名、头像、皮肤与绑定状态"),
                item("inventory", "背包与容器", "minecraft-server", "背包、末影箱、潜影盒物品及数量"),
                item("textures", "物品纹理", "minecraft-server / 资源包", "服务端资源包、CraftEngine 或内置材质"),
                item("skin", "皮肤来源", "yudream-skin", "正版皮肤、披风和配置的皮肤提供方"),
                item("binding", "消息身份绑定", "qq-binding", "QQ OpenID 与站点账号的绑定状态"),
                item("papi.*", "PlaceholderAPI", "服务端桥接", "按玩家查询已授权的服务端占位符"),
                item("time", "渲染上下文", "内置", "更新时间、版本和资源来源等静态字段")
        );
    }

    private static Map<String, Object> item(String key, String label, String provider, String description) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("key", key);
        value.put("label", label);
        value.put("provider", provider);
        value.put("description", description);
        return value;
    }
}
