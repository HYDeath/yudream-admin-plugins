package online.yudream.plugin.aichatbot.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 固定公开来源的只读适配；不访问宿主私有 HTTP、凭据或其他插件的数据库。 */
public final class PublicKnowledgeClient implements AutoCloseable {
    public static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String MC_ORIGIN = "https://www.minecraft.net";
    private static final int MAX_BYTES = 1_048_576;
    private static final Set<String> SEARCH_HOSTS = Set.of("www.bing.com", "cn.bing.com");
    private static final Pattern VERSION = Pattern.compile("\\d{2}w\\d{2}[a-z]|\\d+(?:\\.\\d+){1,2}(?:[- ]+(?:snapshot|pre(?:[- ]?release)?|rc|release[- ]candidate)[- ]?\\d+)?");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Transport transport;
    private final Clock clock;
    private final HttpClient http;

    @FunctionalInterface
    public interface Transport { String get(URI uri, Set<String> allowedHosts) throws Exception; }

    public PublicKnowledgeClient() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        transport = this::read;
        clock = Clock.systemUTC();
    }

    public PublicKnowledgeClient(Transport transport, Clock clock) {
        this.transport = transport;
        this.clock = clock;
        this.http = null;
    }

    public Map<String, Object> search(String query, String scope) {
        String kind = "minecraft_updates".equals(scope) ? "MINECRAFT_OFFICIAL_SEARCH" : "PUBLIC_WEB_SEARCH";
        Map<String, Object> result = result(kind, query);
        try {
            String actual = query(query);
            if ("minecraft_updates".equals(scope)) actual += " site:minecraft.net/en-us/article/";
            URI endpoint = URI.create("https://www.bing.com/search?format=rss&q=" + encode(actual));
            List<Map<String, Object>> sources = rss(transport.get(endpoint, SEARCH_HOSTS));
            if ("minecraft_updates".equals(scope)) sources = sources.stream()
                    .filter(source -> officialArticle(String.valueOf(source.get("url")))).toList();
            result.put("sources", sources);
            result.put("status", sources.isEmpty() ? "NO_RESULTS" : "AVAILABLE");
            result.put("notice", "搜索摘要可能不完整或不相关；searchIndexDate 为搜索服务提供的索引日期，不能当作文章发布日期。只引用能支持当前问题的结果。");
        } catch (Exception error) {
            failure(result, error);
        }
        return result;
    }

    public Map<String, Object> minecraftWiki(String query) {
        Map<String, Object> result = result("MINECRAFT_WIKI", query);
        try {
            URI endpoint = URI.create("https://zh.minecraft.wiki/api.php?action=query&generator=search&gsrsearch="
                    + encode(query(query)) + "&gsrnamespace=0&gsrlimit=3&prop=extracts%7Cinfo%7Crevisions"
                    + "&explaintext=1&exlimit=1&inprop=url&rvprop=timestamp%7Ccontent&rvslots=main&format=json&formatversion=2");
            JsonNode root = mapper.readTree(transport.get(endpoint, Set.of("zh.minecraft.wiki")));
            if (root.has("error")) throw new IOException("Minecraft Wiki API 返回错误");
            if (!root.has("query") && !root.has("batchcomplete")) throw new IOException("Minecraft Wiki 响应结构无效");
            List<Map<String, Object>> sources = new ArrayList<>();
            for (JsonNode page : root.path("query").path("pages")) {
                String url = page.path("fullurl").asText("");
                if (!httpsHost(url, "zh.minecraft.wiki")) continue;
                JsonNode revision = page.path("revisions").path(0);
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("title", page.path("title").asText(""));
                source.put("url", url);
                source.put("content", clip(page.path("extract").asText(""), 8000));
                source.put("wikitext", clip(revision.path("slots").path("main").path("content").asText(""), 10000));
                source.put("updatedAt", revision.path("timestamp").asText(""));
                source.put("sourceKind", "MINECRAFT_WIKI");
                sources.add(source);
            }
            result.put("sources", sources);
            result.put("status", sources.isEmpty() ? "NO_RESULTS" : "AVAILABLE");
            result.put("notice", "这是 Minecraft 社区百科，不是本服玩家 Wiki。页面可能覆盖多个游戏版本；未包含的模板或配方表不得自行补全。updatedAt 是页面修订时间。");
        } catch (Exception error) {
            failure(result, error);
        }
        return result;
    }

    public Map<String, Object> minecraftUpdates(String query) {
        Map<String, Object> result = result("MINECRAFT_OFFICIAL", query);
        List<Map<String, Object>> sources = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        try {
            query = query(query);
        } catch (IllegalArgumentException error) {
            failure(result, error);
            return result;
        }
        String articleQuery = query;
        try {
            JsonNode root = mapper.readTree(transport.get(MANIFEST, Set.of("piston-meta.mojang.com")));
            String release = root.path("latest").path("release").asText("");
            String snapshot = root.path("latest").path("snapshot").asText("");
            if (release.isBlank() || snapshot.isBlank() || !root.path("versions").isArray())
                throw new IOException("Mojang 版本清单结构无效");
            result.put("latestJavaRelease", release);
            result.put("latestJavaSnapshot", snapshot);
            String requested = query.toLowerCase(Locale.ROOT);
            if (!bedrock(requested) && (requested.contains("最新") || requested.contains("今天")
                    || requested.contains("目前") || requested.contains("latest") || requested.contains("newest"))) {
                String latest = requested.contains("快照") || requested.contains("snapshot") ? snapshot : release;
                articleQuery = "Minecraft Java " + latest + " changelog";
            }
            for (JsonNode version : root.path("versions")) {
                String id = version.path("id").asText("");
                if (!id.equals(release) && !id.equals(snapshot) && (id.isBlank()
                        || !Pattern.compile("(?<![a-z0-9.])" + Pattern.quote(id.toLowerCase(Locale.ROOT)) + "(?![a-z0-9.])").matcher(requested).find())) continue;
                if (sources.size() >= 5) break;
                sources.add(new LinkedHashMap<>(Map.of("title", "Minecraft Java " + id,
                        "version", id, "versionType", version.path("type").asText(""),
                        "publishedAt", version.path("releaseTime").asText(""), "url", MANIFEST.toString(),
                        "content", "官方版本清单仅证明版本号、类型和发布时间，不含更新日志。",
                        "sourceKind", "MOJANG_VERSION_MANIFEST")));
            }
        } catch (Exception error) {
            issues.add("Mojang Java 版本清单暂不可用（" + errorKind(error) + "）");
        }
        Map<String, Object> searched = search(articleQuery, "minecraft_updates");
        String matchingQuery = articleQuery;
        List<Map<String, Object>> articles = sourceRows(searched).stream()
                .filter(source -> matchesUpdate(String.valueOf(source.get("title")), matchingQuery))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (articles.isEmpty()) issues.add("未取得匹配的官方文章搜索结果");
        // 搜索不可用或没有命中时，尝试官网公开新闻列表；只选与请求/版本有关的文章。
        if (articles.isEmpty()) {
            try {
                URI feed = URI.create(MC_ORIGIN + "/content/minecraftnet/language-masters/en-us/_jcr_content.articles.page-1.json");
                JsonNode root = mapper.readTree(transport.get(feed, Set.of("www.minecraft.net")));
                if (!root.path("article_grid").isArray()) throw new IOException("官网新闻列表结构无效");
                for (JsonNode node : root.path("article_grid")) {
                    String title = node.path("default_tile").path("title").asText("");
                    String url = MC_ORIGIN + node.path("article_url").asText("");
                    if (!officialArticle(url) || !matchesUpdate(title, articleQuery)) continue;
                    articles.add(new LinkedHashMap<>(Map.of("title", title, "url", url,
                            "content", node.path("default_tile").path("sub_header").asText(""), "publishedAt", "")));
                    if (articles.size() >= 3) break;
                }
            } catch (Exception error) {
                issues.add("Minecraft 官网新闻列表暂不可用（" + errorKind(error) + "）");
            }
        }
        boolean fullArticle = false;
        for (int i = 0; i < articles.size() && i < 3; i++) {
            Map<String, Object> article = new LinkedHashMap<>(articles.get(i));
            article.put("sourceKind", "MINECRAFT_OFFICIAL_ARTICLE");
            article.put("fullTextAvailable", false);
            // 最多读取一篇正文；其余是搜索摘要，不能用于声称完成整篇翻译。
            if (i == 0) {
                try {
                    String html = transport.get(URI.create(String.valueOf(article.get("url"))), Set.of("www.minecraft.net"));
                    String content = articleText(html);
                    if (content.isBlank()) throw new IOException("官网文章正文不可识别");
                    article.put("content", clip(content, 18000));
                    article.put("articleTextAvailable", true);
                    article.put("fullTextAvailable", content.length() <= 18000);
                    article.put("publishedAt", publishedDate(html));
                    fullArticle = content.length() <= 18000;
                } catch (Exception error) {
                    issues.add("官方文章正文暂不可用（" + errorKind(error) + "）；仅保留搜索摘要");
                }
            }
            sources.add(article);
        }
        result.put("sources", sources);
        result.put("status", sources.isEmpty() ? "UNAVAILABLE" : fullArticle && issues.isEmpty() ? "AVAILABLE" : "PARTIAL");
        result.put("issues", issues);
        result.put("notice", "版本清单只覆盖 Java 版，latest.snapshot 可能与 latest.release 相同。基岩版或历史特定版本需以匹配官方文章为准。正文不可用时只能说明已核实的版本与摘要，不能编造变更或声称已翻译全文。");
        return result;
    }

    private Map<String, Object> result(String kind, String query) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceKind", kind);
        value.put("query", query == null ? "" : query);
        value.put("checkedAt", Instant.now(clock).toString());
        value.put("sources", List.of());
        value.put("status", "UNAVAILABLE");
        return value;
    }

    private void failure(Map<String, Object> result, Exception error) {
        result.put("status", "UNAVAILABLE");
        result.put("notice", "公开来源暂时无法检索（" + errorKind(error) + "）；本次没有可核实的来源，不能据此断言最新信息。");
    }

    private static String errorKind(Exception error) {
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        // 不回显任意远端错误正文或异常中的环境信息。
        return error.getClass().getSimpleName();
    }

    private static String query(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("检索词不能为空");
        String text = value.trim();
        if (text.length() > 320) throw new IllegalArgumentException("检索词过长");
        return text;
    }

    private List<Map<String, Object>> rss(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
        var doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        if (!"rss".equals(doc.getDocumentElement().getTagName())) throw new IOException("搜索服务未返回 RSS");
        List<Map<String, Object>> rows = new ArrayList<>();
        var items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength() && rows.size() < 5; i++) {
            Element item = (Element) items.item(i);
            String url = child(item, "link");
            if (!publicLink(url)) continue;
            rows.add(new LinkedHashMap<>(Map.of("title", child(item, "title"), "url", url,
                    "content", clip(plain(child(item, "description")), 1400),
                    "publishedAt", "", "searchIndexDate", child(item, "pubDate"), "fullTextAvailable", false)));
        }
        return rows;
    }

    private static String child(Element item, String name) {
        var nodes = item.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sourceRows(Map<String, Object> result) {
        return new ArrayList<>((List<Map<String, Object>>) result.getOrDefault("sources", List.of()));
    }

    private static boolean matchesUpdate(String title, String query) {
        String text = title.toLowerCase(Locale.ROOT);
        String requested = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean wantsBedrock = bedrock(requested);
        if (wantsBedrock && !bedrock(text)) return false;
        if (!wantsBedrock && bedrock(text)) return false;
        var versions = VERSION.matcher(requested);
        if (versions.find()) {
            String version = normalizeVersion(versions.group());
            var candidates = VERSION.matcher(text);
            while (candidates.find()) if (version.equals(normalizeVersion(candidates.group()))) return true;
            return false;
        }
        return wantsBedrock || text.contains("snapshot") || text.contains("minecraft java")
                || text.contains("pre-release") || text.contains("release candidate");
    }

    private static String normalizeVersion(String value) {
        return value.replaceAll("[- ]", "").replace("prerelease", "pre").replace("releasecandidate", "rc");
    }

    private static boolean bedrock(String text) {
        return text.contains("基岩") || text.contains("bedrock") || text.contains("preview");
    }

    private static boolean officialArticle(String url) {
        return httpsHost(url, "www.minecraft.net") && URI.create(url).getPath().matches("/(?:en-us|zh-hans)/article/[a-zA-Z0-9_-]+/?");
    }

    private static boolean httpsHost(String value, String host) {
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && host.equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443); }
        catch (IllegalArgumentException error) { return false; }
    }

    private static boolean publicLink(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return Set.of("http", "https").contains(uri.getScheme()) && uri.getRawUserInfo() == null && host != null
                    && host.contains(".") && !host.equalsIgnoreCase("localhost") && !host.endsWith(".local")
                    && !host.matches("[0-9.]+") && !host.contains(":");
        } catch (IllegalArgumentException error) { return false; }
    }

    private static String articleText(String html) {
        var start = Pattern.compile("(?is)<(?:article\\b[^>]*>|section\\b[^>]*class=[\"'][^\"']*article[^\"']*[\"'][^>]*>|div\\b[^>]*class=[\"'][^\"']*article-section[^\"']*[\"'][^>]*>)").matcher(html);
        if (!start.find()) return "";
        int end = html.toLowerCase(Locale.ROOT).indexOf("<footer", start.end());
        return plain(html.substring(start.end(), end < 0 ? html.length() : end));
    }

    private static String publishedDate(String html) {
        var matcher = Pattern.compile("(?is)<meta[^>]*(?:property|name)=[\"']article:published_time[\"'][^>]*content=[\"']([^\"']+)[\"']").matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String plain(String html) {
        return html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<[^>]+>", " ").replace("&amp;", "&").replace("&nbsp;", " ")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    private String read(URI initial, Set<String> allowedHosts) throws IOException, InterruptedException {
        URI uri = initial;
        for (int hop = 0; hop <= 3; hop++) {
            if (!allowedHosts.contains(uri.getHost()) || !httpsHost(uri.toString(), uri.getHost()))
                throw new IOException("公开来源地址不在许可范围");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "YuDream-GroupChatbot/1.7 public-knowledge")
                    .header("Accept", "application/json,application/rss+xml,application/xml,text/html;q=0.8")
                    .GET().build();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArrayConsumer(chunk -> chunk.ifPresent(bytes -> {
                if (body.size() + bytes.length > MAX_BYTES)
                    throw new UncheckedIOException(new IOException("公开来源响应过大"));
                body.writeBytes(bytes);
            })));
            HttpResponse<Void> response;
            try {
                // 覆盖完整正文；仅给 ofInputStream 设置请求超时不能约束后续阻塞读取。
                response = pending.get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException error) {
                throw new java.net.http.HttpTimeoutException("公开来源读取超时");
            } catch (java.util.concurrent.ExecutionException error) {
                throw new IOException("公开来源读取失败", error.getCause());
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                if (hop == 3) throw new IOException("公开来源重定向过多");
                uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(() -> new IOException("缺少重定向地址")));
                continue;
            }
            if (response.statusCode() != 200) throw new IOException("公开来源 HTTP " + response.statusCode());
            String text = body.toString(StandardCharsets.UTF_8);
            if (text.isBlank()) throw new IOException("公开来源响应为空");
            return text;
        }
        throw new IOException("公开来源读取失败");
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String clip(String value, int limit) { return value.length() > limit ? value.substring(0, limit) + "（已截断）" : value; }

    @Override public void close() { if (http != null) http.close(); }
}
