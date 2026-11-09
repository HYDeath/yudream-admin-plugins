package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.core.PluginContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code minecraft-server} 插件读取服务器台账。
 *
 * <p>插件之间是软依赖：本插件不把它的 API 打进依赖（宿主可能在没装服务器插件时
 * 单独运行本插件），而是通过服务端口 + 反射读取 {@code minecraftServers(boolean)}。
 * 契约方法名与返回记录是稳定的；provider 缺省或版本过旧时返回空列表，页面显示
 * 「去 Minecraft 服务器添加」。</p>
 */
public final class MinecraftServerDirectory implements ServerListData.ServerDirectory {
    private static final String PLUGIN_CODE = "minecraft-server";

    private final PluginContext context;

    public MinecraftServerDirectory(PluginContext context) {
        this.context = context;
    }

    @Override
    public List<ServerListData.ServerEntry> servers() {
        if (context == null || !context.dependencyAvailable(PLUGIN_CODE)) {
            return List.of();
        }
        Object service = locate();
        if (service == null) {
            return List.of();
        }
        try {
            Method list = service.getClass().getMethod("minecraftServers", boolean.class);
            Object value = list.invoke(service, true);
            if (!(value instanceof List<?> rows)) {
                return List.of();
            }
            List<ServerListData.ServerEntry> result = new ArrayList<>(rows.size());
            for (Object row : rows) {
                ServerListData.ServerEntry entry = entry(row);
                if (entry != null) {
                    result.add(entry);
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // 旧版 provider 或插件正在重载：按没有服务器处理，不影响其它功能。
            return List.of();
        }
    }

    /** 服务端口需要 API 类型才能解析，这里按已知接口名逐个尝试，避免编译期强依赖。 */
    private Object locate() {
        for (String type : List.of(
                "online.yudream.base.plugin.minecraft.api.PluginMinecraftService")) {
            try {
                Class<?> api = Class.forName(type, false, context.getClass().getClassLoader());
                Object service = context.service(PLUGIN_CODE, api).orElse(null);
                if (service != null) {
                    return service;
                }
            } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                // 换下一个接口名。
            }
        }
        return null;
    }

    private static ServerListData.ServerEntry entry(Object row) {
        if (row == null) {
            return null;
        }
        try {
            String id = text(row, "id");
            String name = text(row, "name");
            boolean enabled = bool(row, "enabled");
            String description = text(row, "descriptionMarkdown");
            List<ServerListData.SubServer> subServers = new ArrayList<>();
            Object subs = call(row, "subServers");
            if (subs instanceof List<?> list) {
                for (Object sub : list) {
                    subServers.add(new ServerListData.SubServer(text(sub, "name"), integer(sub, "online")));
                }
            }
            return new ServerListData.ServerEntry(id, name, edition(description), enabled, subServers);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 基岩/Java 标识：服务条目没有单独字段，描述里写「基岩」时按基岩版展示。 */
    private static String edition(String description) {
        String value = description == null ? "" : description;
        return value.contains("基岩") || value.toLowerCase().contains("bedrock") ? "基岩版" : "Java";
    }

    private static Object call(Object target, String getter) {
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static String text(Object target, String getter) {
        Object value = call(target, getter);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Object target, String getter) {
        return Boolean.TRUE.equals(call(target, getter));
    }

    private static int integer(Object target, String getter) {
        return call(target, getter) instanceof Number number ? number.intValue() : 0;
    }
}
