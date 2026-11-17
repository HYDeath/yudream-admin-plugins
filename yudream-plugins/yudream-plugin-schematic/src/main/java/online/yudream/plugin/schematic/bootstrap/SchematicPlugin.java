package online.yudream.plugin.schematic.bootstrap;

import online.yudream.base.plugin.spi.annotation.*;
import online.yudream.base.plugin.spi.core.*;
import online.yudream.base.plugin.spi.system.messaging.PluginInteractionFilter;
import online.yudream.plugin.schematic.application.SchematicJobs;
import online.yudream.plugin.schematic.application.SchematicAdmission;
import online.yudream.plugin.schematic.infrastructure.*;
import online.yudream.plugin.schematic.interfaces.*;
import java.util.Set;

@PluginSpec(code="schematic", name="建筑投影", version="0.4.0", description="群文件与工具箱联动的原版模型透明建筑预览")
@PluginPermissions({
    @PluginPermission(code=SchematicPlugin.USE, name="使用建筑投影", module="平台插件", description="上传与查看自己的建筑投影"),
    @PluginPermission(code=SchematicPlugin.MANAGE, name="管理建筑投影", module="平台插件", description="查看和删除建筑投影任务")
})
@PluginFrontend(moduleName="schematic", menuTitle="建筑投影", menuIcon="i-ri:building-4-line", styles={"style.css"}, routes={
    @PluginRoute(path="/platform/plugins/schematic/me", name="schematic-workbench", title="我的建筑投影", component="schematic/Workbench", permission=SchematicPlugin.USE),
    @PluginRoute(path="/platform/plugins/schematic/admin/jobs", name="schematic-admin", title="投影任务管理", component="schematic/Admin", permission=SchematicPlugin.MANAGE)
})
public final class SchematicPlugin implements YuDreamPlugin {
    public static final String USE = "plugin:schematic:use", MANAGE = "plugin:schematic:manage";
    @Override public void onEnable(PluginContext context) {
        var renderer = new DetailedPreview();
        var jobs = new SchematicJobs(context.documents(), context.files(), renderer::render);
        var admission = new SchematicAdmission(context);
        var downloader = new QqFileDownload();
        var messages = new SchematicMessages(context, jobs, admission, downloader);
        context.onDispose(messages); context.onDispose(jobs); context.onDispose(downloader); context.onDispose(renderer);
        var http = new SchematicHttp(jobs, admission);
        context.registerHttpController(new SchematicUserController(http));
        context.registerHttpController(new SchematicAdminController(http));
        context.registerHttpController(new SchematicPublicController(http));
        // Milky reports group files as both file segments and group_file_upload. Consuming only
        // message_receive avoids duplicate render jobs and uses the existing published SPI.
        context.interactions().onMessage(new PluginInteractionFilter(Set.of("message_receive"), "milky", null, null), messages::onMessage);
    }
}
