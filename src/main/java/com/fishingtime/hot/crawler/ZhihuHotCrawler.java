package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.util.HotScoreParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 知乎热榜抓取器
 *
 * API: GET https://api.zhihu.com/topstory/hot-list?limit=50
 *
 * JSON 结构：
 * {
 *   "data": [
 *     {
 *       "target": {
 *         "title": "标题",
 *         "url": "https://api.zhihu.com/questions/xxx",
 *         "excerpt": "摘要"
 *       },
 *       "detail_text": "1473 万热度"    ← 热度，已是可读格式
 *     }
 *   ]
 * }
 *
 * 字段映射：
 *   rank      → data 数组下标 + 1
 *   title     → target.title
 *   hotScore  → detail_text（如 "1473 万热度"）
 *   url       → target.url（替换 api.zhihu.com → www.zhihu.com）
 *   summary   → target.excerpt
 */
@Slf4j
@Component
public class ZhihuHotCrawler implements HotCrawler {

    private static final String API_URL = "https://api.zhihu.com/topstory/hot-list?limit=50";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public String platform() {
        return "zhihu";
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.zhihu.com/hot")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("[知乎热榜] API 返回 status={}", resp.statusCode());
                return List.of();
            }

            List<HotItemDTO> result = parseResponse(resp.body());
            HotScoreParser.normalizeScores(result);
            log.info("[知乎热榜] 解析到 {} 条数据，耗时 {}ms",
                    result.size(), System.currentTimeMillis() - start);
            return result.size() > 30 ? result.subList(0, 30) : result;
        } catch (Exception e) {
            log.error("[知乎热榜] 请求异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 API 响应
     *
     * 思路：按 "target" 拆分数组，对每段 JSON 提取所需字段
     */
    private List<HotItemDTO> parseResponse(String body) {
        List<HotItemDTO> list = new ArrayList<>();

        // 定位 data 数组
        int dataStart = body.indexOf("\"data\"");
        if (dataStart < 0) return list;

        int arrStart = body.indexOf('[', dataStart);
        int arrEnd = body.lastIndexOf(']');
        if (arrStart < 0 || arrEnd < 0) return list;

        String dataArr = body.substring(arrStart, arrEnd + 1);

        // 按 "target": 拆分成独立条目
        String[] parts = dataArr.split("\"target\"\\s*:\\s*");
        // parts[0] 是 data 数组开头，跳过
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (i > 50) break;

            try {
                // 找到 target 对象的完整范围：从第一个 { 到匹配的 }
                int objStart = part.indexOf('{');
                if (objStart < 0) continue;

                int depth = 0;
                int objEnd = -1;
                for (int j = objStart; j < part.length(); j++) {
                    char c = part.charAt(j);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) { objEnd = j; break; }
                    }
                }
                if (objEnd < 0) continue;

                String targetJson = part.substring(objStart, objEnd + 1);

                // 提取字段
                String title = extractStr(targetJson, "\"title\"");
                String url = extractStr(targetJson, "\"url\"");
                String excerpt = extractStr(targetJson, "\"excerpt\"");

                if (title == null || title.length() < 2) continue;

                // 从 target 后面的剩余部分找 detail_text
                String afterTarget = part.substring(objEnd + 1);
                String detailText = extractStr(afterTarget, "\"detail_text\"");

                // 转换 url：api.zhihu.com → www.zhihu.com
                if (url != null && url.contains("api.zhihu.com")) {
                    url = url.replace("api.zhihu.com", "www.zhihu.com");
                }

                String rawHot = detailText != null ? detailText : "";
                list.add(HotItemDTO.builder()
                        .rank(list.size() + 1)
                        .title(title)
                        .hotScore(rawHot)
                        .url(url)
                        .summary(excerpt)
                        .build());
            } catch (Exception e) {
                log.debug("[知乎热榜] 条目 {} 解析失败: {}", i, e.getMessage());
            }
        }

        return list;
    }

    /** 从 JSON 中提取字符串值 */
    private String extractStr(String json, String key) {
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
}
