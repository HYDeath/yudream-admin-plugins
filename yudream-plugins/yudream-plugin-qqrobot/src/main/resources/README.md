# 内置纹理

`builtin-textures.zip` 是原版 `assets/minecraft/textures/{item,block}/*.png` 的打包副本，
只为后台「物品/背包渲染」在没有服务端资源包时提供图标，不参与任何玩法逻辑。

抽取命令（服务端或本地有客户端 jar 时都可重复执行）：

```bash
python tools/extract-textures.py path/to/client.jar src/main/resources/builtin-textures.zip
```

CraftEngine / 服务器资源包纹理仍由服务端插件 `PackTextureIndex` 索引后随物品上报，
优先级高于这里的原版图。
