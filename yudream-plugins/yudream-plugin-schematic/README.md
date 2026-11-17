# 建筑投影插件

独立插件 `schematic`，通过已发布 SPI 2.38.0 调用平台消息渲染服务。群文件与网站工具箱使用同一套解析、任务、预览和分享码；不依赖 QQ 机器人业务插件的内部实现。

## 使用

1. 构建并安装插件 JAR，在平台启用「消息渲染」能力及 Chromium 渲染服务。
2. 按需分配 `plugin:schematic:use`（个人上传/历史）与 `plugin:schematic:manage`（全局任务管理）。个人端始终只显示当前账号的记录。
3. 在支持文件消息的 Milky QQ 连接所在群发送 Litematic 或 Java 结构 NBT 文件（名称、后缀不限，按实际内容识别）。插件取文件 ID，通过 `get_group_file_download_url` 获取附件地址，下载后排队生成图片并回复元数据。重复事件去重，同一发送者同时处理一个任务。群内发送 `/投影` 可看说明。
4. 网站工具箱入口 `/tools.html?tool=schematic`。登录后可上传和查看本人记录；QQ 身份已绑定相同站点账号时，新的群投影自动归入本人记录。未绑定身份的历史结果可通过群回复中的分享码查阅。
5. 也可打开 `/tools.html?tool=schematic&projection=<分享码>`。分享码属于持有即可查看的随机能力地址，不提供公开列表。删除任务同时撤销分享码和图片。

## 平台与渲染边界

- 腾讯当前公开的[文件接收文档](https://github.com/tencent-connect/bot-docs/blob/master/docs/develop/api-v2/server-inter/message/type/file.md)仅列出单聊 pdf/doc/txt，未开放群聊任意文件接收。**官方 QQ OpenAPI 无法保证直接收取 litematic/nbt 群文件**；此情况下使用网站上传。插件不伪造官方文件事件，也不抓取聊天正文中的任意 URL。
- [Milky 协议](https://milky.ntqqrev.org/struct/Event.html)要求文件上传同时上报带 `file` 段的消息；本插件消费宿主已有的 `message_receive` 事件，无需修改宿主或发布新 SPI。文件链接 API 见[官方协议](https://milky.ntqqrev.org/api/file)。群自动出图的部署前提是已连接支持该事件与 API 的协议端。
- 参考 [Litematica-Nova](https://github.com/LitematicaViewer/Litematica-Nova) 的元数据及双视角交互。其公开 Community 版本[不包含 Standard / Full 渲染核心](https://github.com/LitematicaViewer/Litematica-Nova/blob/main/CONTRIBUTING.md)。本插件没有复制 Nova 源码或私有运行组件。
- 当前为**体素概览**：根据真实方块位置构建可见面，使用近似方块色、定向明暗与两个相反视角，不包含原版纹理、流体透明度、半砖/楼梯/红石等特殊模型。不是 Nova 全材质渲染器；不会自动识别建筑用途或编造 Minecraft 版本名。文件中提供的数据版本号原样显示。
- SVG 场景由插件生成，Chromium 关闭脚本并阻断外部资源进行栅格化。因已发布渲染 SPI 未暴露透明参数，每视角渲染黑白两张底图后还原 Alpha；保留白/黑建筑方块及抗锯齿边缘，不使用删色抠图。

## 解析、资源与存储

支持未压缩、gzip、zlib 的大端 Java NBT；Litematic 多区域、负尺寸、连续跨 long 位索引与区域重叠；Java 结构的 palette/blocks 与多 palette 首个变体。不支持基岩小端 NBT、旧版数字方块 ID schematic 或 Sponge schem。空气和 structure_void 不渲染；创建时间/作者缺失时显示未提供，不从上传时间伪造。

上限：文件 16 MiB、解压 NBT 96 MiB、嵌套 48 层、256 个区域、单轴 4096、包围盒跨度 8192、区域总体积 1600 万、35 万非空气方块。复杂建筑自动合并预览体素，使每个视角保持在 28000 个可见面与 190 万字符以内；方块数、材料数与原始尺寸保持精确。不能处理的输入明确失败并提示拆分。单线程渲染、最多四个排队任务，每个身份最多保存 100 个结果。任务失败可删后重传，服务重启中断的任务在查询时标记失败。

识别依赖 NBT/gzip/zlib 文件头、完整 NBT 根节点与 Litematic/Java 结构 schema，不信任名称、后缀或 MIME；拒绝根节点后的尾随数据。可执行文件、脚本及压缩后伪装的程序不能通过仅改后缀变成投影。原始文件仅在内存作数据解析，不进行程序执行、动态类加载、脚本求值或按原文件名写入可执行目录，下载链接不持久化。插件自属 document store 保存任务/统计/分享索引，file store 保存 PNG。附件下载仅允许 QQ CDN 后缀并检查公网解析地址、拒绝跳转、限制下载时长和流式字节数。`/me/**` 的 owner 来自可信 principal；管理员访问个人接口也不能越权。`/admin/**` 单独鉴权。

## 构建与验收

```sh
pnpm --dir yudream-frontend --filter @yudream/plugin-schematic run typecheck
pnpm --dir yudream-frontend --filter @yudream/plugin-schematic run build
mvn -pl yudream-plugins/yudream-plugin-schematic -am test package
```

前端打包进入 JAR 的 `META-INF/yudream-plugin/frontend/schematic/`。后端测试覆盖 NBT 压缩、跨 long 方块索引、负区域、越界、个人/管理员范围、分享撤销、队列、重启恢复、群文件下载到回复流程及 Alpha 保真。可选真实浏览器集成测试通过 `-Dschematic.browser=http://127.0.0.1:<port>` 启用；测试桥须实现 `POST /render`，接收 `{html, selector}` 返回实际 PNG。浏览器集成 fixture 与图片只写入 `target/browser-verification/`。

官网需要同时包含工具箱 `SchematicTool.vue` 集成与支持 FormData 的现有同源登录请求封装；不向旧后端发送新版会话。上线前需在实际 QQ 连接进行群文件收发验收；自动化协议测试不等同于真实 QQ 验收。

## 参考与接入边界

- [QQ 机器人官方文档](https://q.qq.com/wiki/) 与[腾讯官方文件类型说明](https://github.com/tencent-connect/bot-docs/blob/master/docs/develop/api-v2/server-inter/message/type/file.md)：官方文件接收声明限单聊 pdf/doc/txt，未开放群文件接收；不能把 Milky 的任意群文件能力当作官方 OpenAPI 能力。官方连接请使用工具箱上传。
- [Milky 文件 API](https://milky.ntqqrev.org/api/file) 与[官方常见问题](https://github.com/SaltifyDev/milky/issues/46)：文件消息段与群上传通知同时上报。本插件消费宿主已有的 message_receive 文件段，按消息标识去重，再调用 get_group_file_download_url；无需扩展宿主 SPI。
- [Litematica-Nova](https://github.com/LitematicaViewer/Litematica-Nova) 的公开 Community 说明：完整 Standard/Full 核心不在公开版中。本插件独立实现受限体素概览，没有复制其渲染核心。另以该项目公开 stats_water_fixture.litematic 核对解析，实读 27 个方块，与其 Metadata.TotalBlocks 一致；参考文件仅用于本地验收，未打入 JAR。
