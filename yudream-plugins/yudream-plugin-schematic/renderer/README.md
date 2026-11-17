# 原版模型浏览器渲染器

此服务与插件 JAR 独立部署。输入只有经过双重校验的 `{size, blocks:[{x,y,z,state,properties}]}` JSON，输出透明双视角 PNG，不接受原始文件、用户 HTML、脚本、模型或网络资源地址。

模型与纹理来自官方客户端 JAR。构建工具只读取 ZIP 内的 JSON/PNG，并验证指定的官方 SHA-1，不执行任何客户端类。生成的资源和浏览器 bundle 属于构建产物，未提交源码仓库。

```sh
npm ci
python prepare_assets.py /trusted/cache/minecraft-client-26.3.jar --sha1 e877b6a07acd633fb3bb475002175cec036e7b87
npm run build
npm test
docker build -t yudream-schematic-renderer:0.3.0 .
```

官方客户端元数据：`https://piston-meta.mojang.com/v1/packages/9f67e10ed013deeaad20ac3c800652765d493408/26.3.json`。生成脚本需要 Pillow；Node 依赖由 package-lock.json 锁定。基底镜像复用项目现有 Playwright 1.60.0 渲染镜像，不修改共享渲染服务。

容器名使用 `schematic-render-server`，加入后端所在的私有 Docker 网络，端口 3000 不映射公网。采用 `--read-only --cap-drop ALL --security-opt no-new-privileges`、非 root 用户、限额 tmpfs、CPU/内存/PID 上限。插件默认访问 `http://schematic-render-server:3000/render`。`GET /health` 用于就绪检查，`POST /render` 最多接受 64 MiB JSON、35 万方块，同一时间只允许一个渲染请求。

本地验收可以设置 `SCHEMATIC_CHROME_CHANNEL=chrome SCHEMATIC_RENDER_HOST=127.0.0.1 SCHEMATIC_RENDER_PORT=18837` 后运行 `npm start`。浏览器仅能请求服务内置路径，所有玩家数据通过 Playwright 的结构化参数传入，不拼接进 HTML/JavaScript。

Deepslate 采用 MIT 许可，完整许可见 LICENSE-DEEPSLATE.txt；gl-matrix 许可见 LICENSE-GL-MATRIX.txt。Minecraft 模型和纹理的权利属于 Mojang/Microsoft，资源来自经校验的客户端，不包含客户端可执行类。
