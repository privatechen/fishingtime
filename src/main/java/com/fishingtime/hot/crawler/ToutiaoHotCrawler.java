package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.util.HotScoreParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 今日头条热榜抓取器
 *
 * API: GET https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc
 * 仅解析 data 数组，fixed_top_data（置顶）一期跳过。
 *
 * 字段映射：
 *   data 顺序  → rank（1 开始）
 *   Title      → title（空则用 QueryWord）
 *   HotValue   → hotScore（原始字符串）
 *   HotValue   → normalizedHotScore（HotScoreParser）
 *   Url        → url
 *   其余字段（summary/replyCount/viewCount/author/publishTime）置空
 */
@Slf4j
@Component
public class ToutiaoHotCrawler implements HotCrawler {

    private static final String API_URL = "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public String platform() {
        return "toutiao";
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[头条热榜] API 返回 status={}", resp.statusCode());
                return List.of();
            }

            List<HotItemDTO> result = parseResponse(resp.body());
            HotScoreParser.normalizeScores(result);
            log.info("[头条热榜] 解析到 {} 条数据，耗时 {}ms",
                    result.size(), System.currentTimeMillis() - start);
            return result.size() > 50 ? result.subList(0, 50) : result;
        } catch (Exception e) {
            log.error("[头条热榜] 抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析头条 API JSON 响应
     * 仅取 data 数组，跳过 fixed_top_data（置顶）
     */
    private List<HotItemDTO> parseResponse(String body) {
        List<HotItemDTO> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                log.warn("[头条热榜] data 字段非数组");
                return list;
            }

            int rank = 1;
            for (JsonNode item : data) {
                if (rank > 30) break;

                // title：Title 优先，空则用 QueryWord
                String title = item.path("Title").asText("");
                if (title.isEmpty()) {
                    title = item.path("QueryWord").asText("");
                }
                if (title.isEmpty()) continue;

                String hotValue = item.path("HotValue").asText("");
                String url = item.path("Url").asText("");

                list.add(HotItemDTO.builder()
                        .rank(rank++)
                        .title(title)
                        .hotScore(hotValue)
                        .url(url)
                        // summary/replyCount/viewCount/author/publishTime 置空
                        .build());
            }
        } catch (Exception e) {
            log.warn("[头条热榜] 响应解析失败: {}", e.getMessage());
        }
        return list;
    }
}
