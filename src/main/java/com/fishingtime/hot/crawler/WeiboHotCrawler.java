package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.util.HotScoreParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 微博热搜抓取器
 *
 * 流程：
 * 1. POST passport.weibo.com/visitor/genvisitor2 → 获取 Visitor Cookie（SUB / SUBP）
 * 2. 带上 Cookie GET s.weibo.com/top/summary?cate=realtimehot
 * 3. Jsoup 解析 HTML 中的热搜 table
 * 4. 转换 HotItemDTO
 */
@Slf4j
@Component
public class WeiboHotCrawler implements HotCrawler {

    private static final String VISITOR_URL = "https://passport.weibo.com/visitor/genvisitor2";
    private static final String HOT_URL = "https://s.weibo.com/top/summary?cate=realtimehot";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 缓存当前有效的 Visitor Cookie */
    private volatile String subCookie = "";
    private volatile String subpCookie = "";

    @Override
    public String platform() {
        return "weibo";
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();

        // 1. 如果 Cookie 为空，先获取 Visitor Cookie
        if (subCookie.isEmpty() || subpCookie.isEmpty()) {
            if (!refreshVisitorCookie()) {
                log.warn("[微博热榜] 获取 Visitor Cookie 失败");
                return List.of();
            }
        }

        // 2. 请求热搜页面
        String html = fetchHotPage();
        if (html == null) {
            // Cookie 可能失效，尝试刷新一次
            if (refreshVisitorCookie()) {
                html = fetchHotPage();
            }
            if (html == null) {
                log.warn("[微博热榜] 获取页面失败");
                return List.of();
            }
        }

        // 3. 解析 HTML
        List<HotItemDTO> result = parseHtml(html);
        HotScoreParser.normalizeScores(result);
        log.info("[微博热榜] 解析到 {} 条数据，耗时 {}ms",
                result.size(), System.currentTimeMillis() - start);
        return result.size() > 30 ? result.subList(0, 30) : result;
    }

    // ─────────────────────────────────────────────
    // 1. 获取 Visitor Cookie
    // ─────────────────────────────────────────────

    /**
     * POST passport.weibo.com/visitor/genvisitor2
     *
     * 响应体格式（JSONP）：
     * window.visitor_gray_callback && visitor_gray_callback({
     *   "retcode":20000000,
     *   "data": { "sub":"...", "subp":"..." }
     * });
     *
     * 从响应体中解析 JSON，提取 sub / subp 作为 Cookie 值。
     */
    private boolean refreshVisitorCookie() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(VISITOR_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Origin", "https://passport.weibo.com")
                    .header("Referer", "https://passport.weibo.com/visitor/visitor")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofString("cb=visitor_gray_callback&from=weibo&tid="))
                    .build();

            HttpClient noRedirectClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpResponse<String> resp = noRedirectClient.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();

            log.info("[微博热榜] genvisitor2 返回 status={}", resp.statusCode());

            if (body == null || body.isEmpty()) {
                log.warn("[微博热榜] genvisitor2 响应体为空");
                return false;
            }

            // 从 JSONP 回调中提取 JSON 部分
            int jsonStart = body.indexOf('{');
            int jsonEnd = body.lastIndexOf('}');
            if (jsonStart < 0 || jsonEnd < 0) {
                log.warn("[微博热榜] 响应体不含 JSON");
                return false;
            }
            String json = body.substring(jsonStart, jsonEnd + 1);

            // 提取 sub（即 SUB cookie）
            String sub = extractJsonValue(json, "\"sub\"");
            // 提取 subp（即 SUBP cookie）
            String subp = extractJsonValue(json, "\"subp\"");

            if (sub != null) subCookie = sub;
            if (subp != null) subpCookie = subp;

            boolean success = !subCookie.isEmpty() && !subpCookie.isEmpty();
            log.info("[微博热榜] Visitor Cookie 获取{} — SUB={}, SUBP={}",
                    success ? "成功" : "失败",
                    subCookie.isEmpty() ? "空" : "ok",
                    subpCookie.isEmpty() ? "空" : "ok");
            return success;
        } catch (Exception e) {
            log.error("[微博热榜] 获取 Visitor Cookie 异常: {}", e.getMessage());
            return false;
        }
    }

    /** 从 JSON 中提取字符串值（简化版，不依赖 JSON 库） */
    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '\\') end += 2;
                else if (json.charAt(end) == '"') break;
                else end++;
            }
            return json.substring(start + 1, end);
        }
        return null;
    }

    // ─────────────────────────────────────────────
    // 2. 请求热搜页面
    // ─────────────────────────────────────────────

    /**
     * GET s.weibo.com/top/summary?cate=realtimehot
     * 携带 SUB; SUBP Cookie
     */
    private String fetchHotPage() {
        try {
            String cookie = "SUB=" + subCookie + "; SUBP=" + subpCookie;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(HOT_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", cookie)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("[微博热榜] 页面返回 status={}", resp.statusCode());
                return null;
            }

            return resp.body();
        } catch (Exception e) {
            log.error("[微博热榜] 请求页面异常: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────
    // 3. Jsoup 解析 HTML
    // ─────────────────────────────────────────────

    /**
     * 解析 div#pl_top_realtimehot > table > tbody > tr
     *
     * - 第一条 tr 为置顶，跳过
     * - rank 必须为数字，否则跳过（如广告）
     */
    private List<HotItemDTO> parseHtml(String html) {
        List<HotItemDTO> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Element table = doc.selectFirst("div#pl_top_realtimehot table");
        if (table == null) {
            log.warn("[微博热榜] 未找到 table 节点，页面结构可能已变更");
            return list;
        }

        var rows = table.select("tbody > tr");
        log.info("[微博热榜] 找到 {} 行", rows.size());

        for (int i = 0; i < rows.size(); i++) {
            Element tr = rows.get(i);

            // 第一条是置顶，跳过
            if (i == 0) continue;

            // 排名
            Element rankTd = tr.selectFirst("td.td-01");
            if (rankTd == null) continue;
            String rankText = rankTd.text().trim();

            // 非数字排名（广告）跳过
            int rank;
            try {
                rank = Integer.parseInt(rankText);
            } catch (NumberFormatException e) {
                continue;
            }

            // 标题 + 链接
            Element titleTd = tr.selectFirst("td.td-02");
            if (titleTd == null) continue;
            Element aTag = titleTd.selectFirst("a");
            if (aTag == null) continue;

            String title = aTag.text().trim();
            if (title.isEmpty()) continue;

            String href = aTag.attr("href");
            // 补全链接
            if (href.startsWith("/")) {
                href = "https://s.weibo.com" + href;
            }

            // 热度
            Element span = titleTd.selectFirst("span");
            String hotScore = (span != null) ? span.text().trim() : "";

            // 标签（热/爆/新/荐）
            Element tagTd = tr.selectFirst("td.td-03");
            String tag = "";
            if (tagTd != null) {
                Element iTag = tagTd.selectFirst("i");
                if (iTag != null) {
                    tag = iTag.text().trim();
                }
            }

            list.add(HotItemDTO.builder()
                    .rank(rank)
                    .title(title)
                    .hotScore(hotScore)
                    .url(href)
                    .summary(tag)
                    .build());
        }

        return list;
    }
}
