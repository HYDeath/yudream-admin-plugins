package online.yudream.plugin.qqrobot.application.service;

import online.yudream.plugin.qqrobot.domain.ImageTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置模板。变量全部由调用方注入，模板内不发起网络请求：
 * 背景与头像都要以 data URI 传入，否则渲染容器会拦截外链。
 */
final class BuiltinTemplates {
    private BuiltinTemplates() {
    }

    static List<ImageTemplate> all() {
        Instant now = Instant.now();
        return List.of(
                new ImageTemplate("builtin-server-list", "服务器在线列表", "1.0.0",
                        "按服务器分组的在线玩家名册，含头像与人数统计；支持自定义背景图和标题。",
                        "系统模板", "服务器",
                        List.of("服务器", "在线列表", "名册"), "", "", serverListHtml(),
                        900, ServerListSchema.defaults(), now, now).normalized(),
                new ImageTemplate("builtin-player-card", "玩家信息卡", "1.0.0",
                        "单玩家资料卡：头像、皮肤、在线时长与绑定状态，适合进服/查询场景。",
                        "系统模板", "玩家",
                        List.of("玩家", "信息卡", "资料"), "", "", playerCardHtml(),
                        640, Map.of(), now, now).normalized()
        );
    }

    static Map<String, Object> sampleVariables(String id) {
        return sampleVariables(id, new AvatarService(false));
    }

    static Map<String, Object> sampleVariables(String id, AvatarService avatars) {
        if ("builtin-player-card".equals(id)) {
            return Map.of(
                    "background", "",
                    "name", "Steve",
                    "uuid", "8667ba71-b85a-4004-af54-457a9734eed7",
                    "avatar", avatars.head("Steve"),
                    "skin", avatars.body("Steve"),
                    "server", "瑶光天工",
                    "online", "2 小时 14 分",
                    "bound", "已绑定 QQ",
                    "joined", "2025-08-12"
            );
        }
        return Map.of(
                "background", "",
                "title", "服务器在线列表",
                "site", "璇玑之境",
                "updated", "21:30",
                "status", "正常",
                "total", "29",
                "servers", List.of(
                        Map.of("name", "YG-大厅", "online", "0", "players", List.of()),
                        Map.of("name", "瑶光天工", "online", "27", "players", List.of(
                                Map.of("name", "MAPS____", "avatar", avatars.head("MAPS____")),
                                Map.of("name", "WemmbU", "avatar", avatars.head("WemmbU")),
                                Map.of("name", "HuaBan_v", "avatar", avatars.head("HuaBan_v")),
                                Map.of("name", "miao10621", "avatar", avatars.head("miao10621")),
                                Map.of("name", "derk", "avatar", avatars.head("derk")),
                                Map.of("name", "EFECDE", "avatar", avatars.head("EFECDE")),
                                Map.of("name", "Xiaohua23", "avatar", avatars.head("Xiaohua23")),
                                Map.of("name", "WitheredFlower", "avatar", avatars.head("WitheredFlower")),
                                Map.of("name", "JZY_001", "avatar", avatars.head("JZY_001"))
                        )),
                        Map.of("name", "模组服", "online", "2", "players", List.of(
                                Map.of("name", "SovietSecretary", "avatar", avatars.head("SovietSecretary")),
                                Map.of("name", "YJL", "avatar", avatars.head("YJL"))
                        ))
                )
        );
    }

    private static String serverListHtml() {
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:"Microsoft YaHei","Noto Sans SC",sans-serif;background:#0c0d12}
                #template-root{position:relative;width:900px;padding:36px 32px 40px;color:#f4f4f8;
                  background:linear-gradient(160deg,rgba(18,20,30,.86),rgba(10,11,16,.94)),var(--bg);
                  background-size:cover;background-position:center;overflow:hidden}
                #template-root .bg{position:absolute;inset:0;background-image:var(--bg);background-size:cover;
                  background-position:center;opacity:.55;z-index:0}
                .content{position:relative;z-index:1}
                .head{display:flex;justify-content:space-between;align-items:flex-start}
                .site{font-size:14px;letter-spacing:2px;color:rgba(255,255,255,.72)}
                .title{font-size:44px;font-weight:800;letter-spacing:2px;margin-top:6px;
                  text-shadow:0 4px 18px rgba(0,0,0,.6)}
                .online{min-width:120px;padding:14px 20px;border-radius:18px;text-align:center;
                  background:rgba(16,17,24,.72);border:1px solid rgba(255,255,255,.12)}
                .online span{display:block;font-size:12px;color:rgba(255,255,255,.6)}
                .online strong{font-size:38px;font-weight:800}
                .stats{display:flex;gap:14px;margin:18px 0 22px}
                .stat{flex:1;padding:12px 16px;border-radius:14px;background:rgba(16,17,24,.6);
                  border:1px solid rgba(255,255,255,.08)}
                .stat span{display:block;font-size:12px;color:rgba(255,255,255,.55)}
                .stat strong{font-size:20px}
                .server{margin-bottom:18px;padding:16px;border-radius:18px;background:rgba(12,13,19,.62);
                  border:1px solid rgba(255,255,255,.08)}
                .server-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
                .server-name{font-size:22px;font-weight:700}
                .badge{font-size:12px;padding:5px 12px;border-radius:999px;background:rgba(255,255,255,.1)}
                .players{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
                .player{display:flex;align-items:center;gap:10px;padding:8px 12px;border-radius:12px;
                  background:rgba(28,29,39,.78);border:1px solid rgba(255,255,255,.06)}
                .avatar{width:32px;height:32px;border-radius:6px;image-rendering:pixelated;background:#24252f}
                .avatar:not([src]),.avatar[src=""]{visibility:hidden}
                .player b{font-size:15px;font-weight:600}
                .empty{font-size:13px;color:rgba(255,255,255,.45)}
                </style></head><body>
                <div id="template-root" style="--bg:url('{{{background}}}')">
                  <div class="bg"></div>
                  <div class="content">
                    <div class="head">
                      <div><div class="site">{{site}}</div><div class="title">{{title}}</div></div>
                      <div class="online"><span>在线</span><strong>{{total}}</strong></div>
                    </div>
                    <div class="stats">
                      <div class="stat"><span>服务器</span><strong>{{serverCount}}</strong></div>
                      <div class="stat"><span>更新时间</span><strong>{{updated}}</strong></div>
                      <div class="stat"><span>状态</span><strong>{{status}}</strong></div>
                    </div>
                    {{#each servers}}
                    <div class="server">
                      <div class="server-head"><div class="server-name">{{name}}</div>
                        <div class="badge">{{online}} 人在线</div></div>
                      <div class="players">{{#each players}}<div class="player">
                        <img class="avatar" src="{{{avatar}}}" alt=""><b>{{name}}</b></div>{{/each}}</div>
                    </div>
                    {{/each}}
                  </div>
                </div></body></html>
                """;
    }

    private static String playerCardHtml() {
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:"Microsoft YaHei","Noto Sans SC",sans-serif}
                #template-root{position:relative;width:640px;padding:28px;color:#f2f2f7;overflow:hidden;
                  background:linear-gradient(150deg,rgba(20,21,30,.9),rgba(10,11,16,.96)),var(--bg);
                  background-size:cover}
                .bg{position:absolute;inset:0;background-image:var(--bg);background-size:cover;
                  background-position:center;opacity:.5}
                .content{position:relative;display:flex;gap:22px;align-items:center}
                .skin-wrap{width:150px;height:210px;display:flex;align-items:flex-end;justify-content:center;
                  border-radius:16px;background:radial-gradient(circle at 50% 30%,#262737,#12131a 75%);
                  border:1px solid rgba(255,255,255,.08)}
                .skin{height:190px;image-rendering:pixelated}
                .skin:not([src]),.skin[src=""]{visibility:hidden}
                .info{flex:1}
                .name{display:flex;align-items:center;gap:10px;font-size:30px;font-weight:800}
                .avatar{width:34px;height:34px;border-radius:6px;image-rendering:pixelated}
                .avatar:not([src]),.avatar[src=""]{visibility:hidden}
                .uuid{font-size:11px;color:rgba(255,255,255,.4);margin:6px 0 16px;word-break:break-all}
                .row{display:flex;justify-content:space-between;padding:9px 12px;border-radius:10px;
                  background:rgba(255,255,255,.05);margin-bottom:8px;font-size:14px}
                .row span{color:rgba(255,255,255,.55)}
                </style></head><body>
                <div id="template-root" style="--bg:url('{{{background}}}')">
                  <div class="bg"></div>
                  <div class="content">
                    <div class="skin-wrap"><img class="skin" src="{{{skin}}}" alt=""></div>
                    <div class="info">
                      <div class="name"><img class="avatar" src="{{{avatar}}}" alt="">{{name}}</div>
                      <div class="uuid">{{uuid}}</div>
                      <div class="row"><span>所在服务器</span><b>{{server}}</b></div>
                      <div class="row"><span>在线时长</span><b>{{online}}</b></div>
                      <div class="row"><span>QQ 绑定</span><b>{{bound}}</b></div>
                      <div class="row"><span>首次进入</span><b>{{joined}}</b></div>
                    </div>
                  </div>
                </div></body></html>
                """;
    }
}
