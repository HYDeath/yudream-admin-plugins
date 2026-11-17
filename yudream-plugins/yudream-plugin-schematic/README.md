# 建筑投影插件

独立插件 `schematic`，通过已发布 SPI 2.38.0 接收群文件和保存任务，向独立的浏览器渲染服务发送已校验的方块数据。群文件与网站工具箱使用同一套解析、任务、预览和分享码；不依赖 QQ 机器人业务插件的内部实现。

## 使用

1. 构建并安装插件 JAR，同时部署 `renderer/` 的独立 Chromium 模型渲染服务。默认内部地址 `http://schematic-render-server:3000/render`，可由服务端环境变量 `SCHEMATIC_RENDER_URL` 指定；不向公网暴露渲染端口。
2. 按需分配 `plugin:schematic:use`（个人上传/历史）与 `plugin:schematic:manage`（全局任务管理）。个人端始终只显示当前账号的记录。
3. 在支持文件消息的 Milky QQ 连接所在群发送 Litematic 或 Java 结构 NBT 文件（名称、后缀不限，按实际内容识别）。插件取文件 ID，通过 `get_group_file_download_url` 获取附件地址，下载后排队生成图片并回复元数据。重复事件去重，同一发送者同时处理一个任务。群内发送 `/投影` 可看说明。
4. 网站工具箱入口 `/tools.html?tool=schematic`。登录后可上传和查看本人记录；QQ 身份已绑定相同站点账号时，新的群投影自动归入本人记录。未绑定身份的历史结果可通过群回复中的分享码查阅。
5. 也可打开 `/tools.html?tool=schematic&projection=<分享码>`。分享码属于持有即可查看的随机能力地址，不提供公开列表。删除任务同时撤销分享码和图片。

## 平台与渲染边界

- 当前[官方消息类型文档](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/type/overview.html)列出群聊文件收发支持，附件由 attachments 提供，引用内容位于 msg_elements。宿主需保留文件元数据并展开引用附件；插件兼容无文件名的标准文件消息段。群内开启「接收所有消息」（recv_msg_setting=all）后直接发送文件，无需 @。仅接收 @ 时，如 QQ 客户端支持引用文件，可先发文件，再另发引用该文件并 @机器人的文字消息；不能把文件后单独 @ 当成引用。插件不抓取聊天正文中的任意 URL。
- [Milky 协议](https://milky.ntqqrev.org/struct/Event.html)要求文件上传同时上报带 `file` 段的消息；本插件消费宿主已有的 `message_receive` 事件，无需修改宿主或发布新 SPI。文件链接 API 见[官方协议](https://milky.ntqqrev.org/api/file)。群自动出图的部署前提是已连接支持该事件与 API 的协议端。
- 参考 [Litematica-Nova](https://github.com/LitematicaViewer/Litematica-Nova) 的元数据及双视角交互。其公开 Community 版本[不包含 Standard / Full 渲染核心](https://github.com/LitematicaViewer/Litematica-Nova/blob/main/CONTRIBUTING.md)。本插件没有复制 Nova 源码或私有运行组件。
- 当前通过 [Deepslate](https://github.com/misode/deepslate) 渲染原版方块模型与纹理，资源来自校验过 SHA-1 的 Minecraft 26.3 官方客户端。保留 `Properties`，支持朝向、铁轨坡度、台阶形状和含水状态；输出 1600×640 透明双视角 PNG。玻璃保留纹理 Alpha，模型按深度遮挡。动画纹理固定首帧，不模拟红石运行、游戏光照传播或实体运动；文件的数据版本原样显示。未知模组方块明确失败，不用普通立方体冒充。旧历史图片标记为体素概览，重新上传才生成详细预览。
- SVG 场景由插件生成，Chromium 关闭脚本并阻断外部资源进行栅格化。因已发布渲染 SPI 未暴露透明参数，每视角渲染黑白两张底图后还原 Alpha；保留白/黑建筑方块及抗锯齿边缘，不使用删色抠图。

## 解析、资源与存储

支持未压缩、gzip、zlib 的大端 Java NBT；Litematic 多区域、负尺寸、连续跨 long 位索引与区域重叠；Java 结构的 palette/blocks 与多 palette 首个变体。不支持基岩小端 NBT、旧版数字方块 ID schematic 或 Sponge schem。空气和 structure_void 不渲染；创建时间/作者缺失时显示未提供，不从上传时间伪造。

上限：文件 16 MiB、解压 NBT 96 MiB、嵌套 48 层、256 个区域、单轴 4096、包围盒跨度 8192、区域总体积 1600 万、35 万非空气方块。不自动合并或丢弃方块，浏览器资源不足时任务明确失败。单线程渲染、最多四个排队任务，每个身份最多保存 100 个结果。任务失败可删后重传，服务重启中断的任务在查询时标记失败。

识别依赖 NBT/gzip/zlib 文件头、完整 NBT 根节点与 Litematic/Java 结构 schema，不信任名称、后缀或 MIME；拒绝根节点后的尾随数据。可执行文件、脚本及压缩后伪装的程序不能通过仅改后缀变成投影。原始文件仅在内存作数据解析，不进行程序执行、动态类加载、脚本求值或按原文件名写入可执行目录，下载链接不持久化。插件自属 document store 保存任务/统计/分享索引，file store 保存 PNG。附件下载仅允许 QQ CDN 后缀并检查公网解析地址、拒绝跳转、限制下载时长和流式字节数。`/me/**` 的 owner 来自可信 principal；管理员访问个人接口也不能越权。`/admin/**` 单独鉴权。

## 构建与验收

```sh
pnpm --dir yudream-frontend --filter @yudream/plugin-schematic run typecheck
pnpm --dir yudream-frontend --filter @yudream/plugin-schematic run build
mvn -pl yudream-plugins/yudream-plugin-schematic -am test package
```

前端打包进入 JAR 的 `META-INF/yudream-plugin/frontend/schematic/`。后端测试覆盖 NBT 压缩、跨 long 方块索引、完整属性保留、负区域、越界、个人/管理员范围、分享撤销、队列、重启恢复、群文件下载到回复流程及 Alpha 保真。详细模型真实浏览器测试使用 `-Dschematic.modelRenderer=http://127.0.0.1:<port>`，可另传 `-Dschematic.source=<本地投影路径>` 验证实际文件；验收数据只写入 `target/model-verification/`。

独立渲染器构建见 [renderer/README.md](renderer/README.md)。渲染服务只接收尺寸、坐标、方块 ID 和属性，不接收玩家原始 NBT、HTML、脚本或任意 URL；浏览器只访问自身的固定资源。运行时采用非 root、只读根目录、资源上限、禁止提权与外部网络请求拦截。

官网需要同时包含工具箱 `SchematicTool.vue` 集成与支持 FormData 的现有同源登录请求封装；不向旧后端发送新版会话。上线前需在实际 QQ 连接进行群文件收发验收；自动化协议测试不等同于真实 QQ 验收。

## 参考与接入边界

- [QQ 官方群消息全量模式](https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/group_message_create.html)：开启「接收所有消息」后无需 @；[群内机器人状态接口](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_bot_state.get.html)可查询 recv_msg_setting，接口权限受平台白名单限制。[群 @消息附件示例](https://bot.q.qq.com/wiki/develop/api-v2/autogen/event/group_at_message_create.html)使用 multimedia.nt.qq.com.cn 下载地址；插件仅增加该精确域名，不放宽到任意 qq.com.cn 子域。
- [Milky 文件 API](https://milky.ntqqrev.org/api/file) 与[官方常见问题](https://github.com/SaltifyDev/milky/issues/46)：文件消息段与群上传通知同时上报。本插件消费宿主已有的 message_receive 文件段，按消息标识去重，再调用 get_group_file_download_url；无需扩展宿主 SPI。
- [Litematica-Nova](https://github.com/LitematicaViewer/Litematica-Nova) 的公开 Community 说明：完整 Standard/Full 核心不在公开版中。本插件独立实现受限体素概览，没有复制其渲染核心。另以该项目公开 stats_water_fixture.litematic 核对解析，实读 27 个方块，与其 Metadata.TotalBlocks 一致；参考文件仅用于本地验收，未打入 JAR。
