package online.yudream.plugin.aichatbot.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PublicKnowledgeClientTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T08:00:00Z"), ZoneOffset.UTC);
    private static final String MANIFEST = """
            {"latest":{"release":"26.3","snapshot":"26w46a"},"versions":[
              {"id":"26w46a","type":"snapshot","releaseTime":"2026-11-12T09:00:00Z"},
              {"id":"26.3","type":"release","releaseTime":"2026-10-01T09:00:00Z"},
              {"id":"1.20","type":"release","releaseTime":"2023-06-07T09:00:00Z"}]}
            """;

    @Test
    void searchReportsActualLinksButNeverPromotesIndexDateToPublicationDate() {
        var result = client((uri, hosts) -> {
            assertEquals("www.bing.com", uri.getHost());
            assertTrue(hosts.contains("cn.bing.com"));
            assertTrue(uri.getRawQuery().contains("9995WX"));
            return rss("CPU review", "https://example.com/review", "CPU &amp; memory", "Mon, 16 Nov 2026 01:00:00 GMT");
        }).search("9995WX 性能", "public");
        assertEquals("AVAILABLE", result.get("status"));
        assertEquals("2026-11-16T08:00:00Z", result.get("checkedAt"));
        var source = sources(result).getFirst();
        assertEquals("https://example.com/review", source.get("url"));
        assertEquals("", source.get("publishedAt"));
        assertEquals("Mon, 16 Nov 2026 01:00:00 GMT", source.get("searchIndexDate"));
        assertEquals(false, source.get("fullTextAvailable"));
        assertEquals("CPU & memory", source.get("content"));
    }

    @Test
    void noResultsDiffersFromMalformedOrUnreachableSource() {
        assertEquals("NO_RESULTS", client((uri, hosts) -> "<rss><channel/></rss>").search("query", "public").get("status"));
        assertEquals("UNAVAILABLE", client((uri, hosts) -> "<html><body>verify</body></html>").search("query", "public").get("status"));
        var failed = client((uri, hosts) -> { throw new IOException("private machine path /secret"); }).search("query", "public");
        assertEquals("UNAVAILABLE", failed.get("status"));
        assertTrue(sources(failed).isEmpty());
        assertFalse(failed.toString().contains("secret"));
    }

    @Test
    void rejectsExternalEntitiesAndDoesNotEmitLocalOrCredentialLinks() {
        String xml = "<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///not-readable'>]><rss><channel><item><title>&xxe;</title></item></channel></rss>";
        assertEquals("UNAVAILABLE", client((uri, hosts) -> xml).search("query", "public").get("status"));
        for (String url : List.of("http://127.0.0.1/test", "http://localhost/a", "http://server.local/a", "https://user:pass@example.com/a", "file:///a", "https://[::1]/a")) {
            var result = client((uri, hosts) -> rss("title", url, "text", "")).search("query", "public");
            assertEquals("NO_RESULTS", result.get("status"), url);
        }
    }

    @Test
    void rejectsInvalidQueriesBeforeAnyNetworkCall() {
        var client = client((uri, hosts) -> { fail("不应发起请求"); return ""; });
        for (String query : List.of("", " ", "x".repeat(321))) {
            assertEquals("UNAVAILABLE", client.search(query, "public").get("status"));
            assertEquals("UNAVAILABLE", client.minecraftWiki(query).get("status"));
            assertEquals("UNAVAILABLE", client.minecraftUpdates(query).get("status"));
        }
    }

    @Test
    void minecraftWikiKeepsPageRevisionAndWikitextSeparateFromServerWiki() {
        var result = client((uri, hosts) -> {
            assertEquals("zh.minecraft.wiki", uri.getHost());
            assertTrue(uri.getRawQuery().contains("revisions"));
            assertFalse(uri.getRawQuery().contains("rvlimit"), "MediaWiki 多页面 generator 不接受 rvlimit，真实接口会返回 invalidparammix");
            assertTrue(uri.getRawQuery().contains("exlimit=1"));
            return """
                    {"batchcomplete":true,"query":{"pages":[{"title":"工作台","fullurl":"https://zh.minecraft.wiki/w/工作台",
                    "extract":"四个木板合成工作台。","revisions":[{"timestamp":"2026-10-30T12:00:00Z","slots":{"main":{"content":"{{Crafting|A1=木板|B1=木板|A2=木板|B2=木板}}"}}}]}]}}
                    """;
        }).minecraftWiki("Java 工作台 合成");
        assertEquals("AVAILABLE", result.get("status"));
        assertEquals("MINECRAFT_WIKI", result.get("sourceKind"));
        assertEquals("2026-10-30T12:00:00Z", sources(result).getFirst().get("updatedAt"));
        assertTrue(sources(result).getFirst().get("wikitext").toString().contains("Crafting"));
        assertTrue(result.get("notice").toString().contains("不是本服"));
        assertEquals("NO_RESULTS", client((uri, hosts) -> "{\"batchcomplete\":true}").minecraftWiki("不存在").get("status"));
        assertEquals("UNAVAILABLE", client((uri, hosts) -> "{\"error\":{\"code\":\"badvalue\"}}").minecraftWiki("item").get("status"));
        assertEquals("UNAVAILABLE", client((uri, hosts) -> "{}").minecraftWiki("item").get("status"));
    }

    @Test
    void latestSnapshotUsesManifestAndOnlyFetchesMatchingOfficialArticle() {
        List<URI> called = new ArrayList<>();
        var result = client((uri, hosts) -> {
            called.add(uri);
            if (uri.equals(PublicKnowledgeClient.MANIFEST)) return MANIFEST;
            if (uri.getHost().equals("www.bing.com")) {
                assertTrue(uri.getRawQuery().contains("26w46a"), "最新文章的检索词必须跟随本次清单");
                return rss("Minecraft Snapshot 26w46a", "https://www.minecraft.net/en-us/article/minecraft-snapshot-26w46a", "Summary", "index date");
            }
            assertEquals("www.minecraft.net", uri.getHost());
            assertEquals(java.util.Set.of("www.minecraft.net"), hosts);
            return "<html><meta property='article:published_time' content='2026-11-12T09:00:00Z'><article><h1>Minecraft Snapshot 26w46a</h1><p>Added a block.</p><script>untrusted()</script></article><footer>Footer</footer></html>";
        }).minecraftUpdates("最新 Minecraft Java 快照更新");
        assertEquals("AVAILABLE", result.get("status"));
        assertEquals("26w46a", result.get("latestJavaSnapshot"));
        assertEquals("26.3", result.get("latestJavaRelease"));
        var article = sources(result).stream().filter(s -> "MINECRAFT_OFFICIAL_ARTICLE".equals(s.get("sourceKind"))).findFirst().orElseThrow();
        assertEquals(true, article.get("fullTextAvailable"));
        assertEquals("2026-11-12T09:00:00Z", article.get("publishedAt"));
        assertFalse(article.get("content").toString().contains("untrusted"));
        assertFalse(article.get("content").toString().contains("Footer"));
        assertEquals(3, called.size());
    }

    @Test
    void manifestOnlyIsPartialAndUnavailableArticlesCannotInventChanges() {
        var result = client((uri, hosts) -> {
            if (uri.equals(PublicKnowledgeClient.MANIFEST)) return MANIFEST;
            throw new java.net.http.HttpTimeoutException("timeout");
        }).minecraftUpdates("最新Java版本");
        assertEquals("PARTIAL", result.get("status"));
        assertEquals("26.3", result.get("latestJavaRelease"));
        assertTrue(sources(result).stream().allMatch(s -> "MOJANG_VERSION_MANIFEST".equals(s.get("sourceKind"))));
        assertFalse(((List<?>) result.get("issues")).isEmpty());
        var failed = client((uri, hosts) -> { throw new IOException("offline"); }).minecraftUpdates("最新更新");
        assertEquals("UNAVAILABLE", failed.get("status"));
        assertFalse(failed.containsKey("latestJavaRelease"));
    }

    @Test
    void officialArticleAllowlistRejectsLookalikesAndWrongVersions() {
        List<URI> called = new ArrayList<>();
        var result = client((uri, hosts) -> {
            called.add(uri);
            if (uri.equals(PublicKnowledgeClient.MANIFEST)) return MANIFEST;
            if (uri.getHost().equals("www.bing.com")) return "<rss><channel>"
                    + item("Minecraft Java 1.20", "https://www.minecraft.net/en-us/article/old", "old", "")
                    + item("Minecraft Snapshot 26w46a", "https://www.minecraft.net.evil.com/en-us/article/a", "fake", "")
                    + item("Minecraft Snapshot 26w46a", "https://evil.com/en-us/article/a", "fake", "") + "</channel></rss>";
            assertTrue(uri.getPath().endsWith(".json"), "不得读取不匹配版本或伪造域名的文章");
            return "{\"article_grid\":[]}";
        }).minecraftUpdates("最新Java快照");
        assertEquals("PARTIAL", result.get("status"));
        assertTrue(sources(result).stream().allMatch(s -> "MOJANG_VERSION_MANIFEST".equals(s.get("sourceKind"))));
        assertEquals(3, called.size());
    }

    @Test
    void newSnapshotAndPreReleaseNamesMustMatchTheirFullVersion() {
        for (var pair : Map.of("26.4-snapshot-8", "Minecraft Java 26.4 Snapshot 8",
                "1.21.11-pre1", "Minecraft Java 1.21.11 Pre-Release 1",
                "1.21.11-rc1", "Minecraft Java 1.21.11 Release Candidate 1").entrySet()) {
            List<URI> called = new ArrayList<>();
            var result = client((uri, hosts) -> {
                called.add(uri);
                if (uri.equals(PublicKnowledgeClient.MANIFEST)) return MANIFEST;
                if (uri.getHost().equals("www.bing.com")) return "<rss><channel>"
                        + item(pair.getValue().replaceAll(" [0-9]+$", " 99"), "https://www.minecraft.net/en-us/article/old", "old", "")
                        + item(pair.getValue(), "https://www.minecraft.net/en-us/article/correct", "correct", "") + "</channel></rss>";
                assertTrue(uri.getPath().endsWith("/correct"), "同系列的其他快照/预发布不能支持目标版本");
                return "<article>Matching version notes.</article>";
            }).minecraftUpdates(pair.getKey());
            assertEquals("AVAILABLE", result.get("status"));
            assertEquals("https://www.minecraft.net/en-us/article/correct", sources(result).getLast().get("url"));
            assertEquals(3, called.size());
        }
    }

    @Test
    void articleFailurePreservesOnlySnippetWithUnavailableFullText() {
        var result = client((uri, hosts) -> {
            if (uri.equals(PublicKnowledgeClient.MANIFEST)) return MANIFEST;
            if (uri.getHost().equals("www.bing.com")) return rss("Minecraft Java 26.3", "https://www.minecraft.net/en-us/article/java-26-3", "Known summary", "index date");
            throw new IOException("offline");
        }).minecraftUpdates("Minecraft Java 26.3");
        assertEquals("PARTIAL", result.get("status"));
        var article = sources(result).getLast();
        assertEquals(false, article.get("fullTextAvailable"));
        assertEquals("Known summary", article.get("content"));
        assertEquals("", article.get("publishedAt"));
    }

    @Test
    void truncatedArticleNeverClaimsFullTranslationAndBedrockIsNotJavaMetadata() {
        var result = client((uri, hosts) -> {
            if (uri.equals(PublicKnowledgeClient.MANIFEST)) return MANIFEST;
            if (uri.getHost().equals("www.bing.com")) return rss("Minecraft Bedrock 1.21.50", "https://www.minecraft.net/en-us/article/bedrock-1-21-50", "summary", "");
            return "<article>" + "long text ".repeat(2200) + "</article>";
        }).minecraftUpdates("Minecraft 基岩版 1.21.50 更新");
        assertEquals("PARTIAL", result.get("status"));
        var article = sources(result).getLast();
        assertEquals(false, article.get("fullTextAvailable"));
        assertEquals(true, article.get("articleTextAvailable"));
        assertTrue(article.get("content").toString().endsWith("（已截断）"));
        assertEquals("", article.get("publishedAt"));
        assertTrue(result.get("notice").toString().contains("只覆盖 Java"));
    }

    private PublicKnowledgeClient client(PublicKnowledgeClient.Transport transport) { return new PublicKnowledgeClient(transport, CLOCK); }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sources(Map<String, Object> result) { return (List<Map<String, Object>>) result.get("sources"); }
    private static String rss(String title, String url, String description, String date) { return "<rss><channel>" + item(title, url, description, date) + "</channel></rss>"; }
    private static String item(String title, String url, String description, String date) {
        return "<item><title>" + title + "</title><link>" + url + "</link><description>" + description + "</description><pubDate>" + date + "</pubDate></item>";
    }
}
