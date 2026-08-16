package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.util.HotScoreParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 抖音热榜抓取器（uapis.cn，月度调用额度 → 独立限流调度，不参与常规 10 分钟刷新）
 *
 * API: GET {uapis.hotboard-api}?type=douyin（Authorization: Bearer {uapis.key}）
 *
 * 字段映射：
 *   list 顺序      → rank（1 开始）
 *   list[].title   → title
 *   list[].url     → url
 *   list[].hot_value → hotScore（原始字符串）+ normalizedHotScore（HotScoreParser）
 *   其余字段（summary/replyCount/viewCount/author/publishTime）置空
 */
@Slf4j
@Component
public class DouyinHotCrawler implements HotCrawler {

    private final String apiUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public DouyinHotCrawler(@Value("${uapis.hotboard-api}") String apiUrl,
                            @Value("${uapis.key}") String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String platform() {
        return "douyin";
    }

    /** 有月度额度，标记为限流平台，由独立限流调度器刷新 */
    @Override
    public boolean quotaLimited() {
        return true;
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "?type=douyin"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[抖音热榜] API 返回 status={}", resp.statusCode());
                return List.of();
            }

            List<HotItemDTO> result = parseResponse(resp.body());
            HotScoreParser.normalizeScores(result);
            log.info("[抖音热榜] 解析到 {} 条数据，耗时 {}ms",
                    result.size(), System.currentTimeMillis() - start);
            return result.size() > 50 ? result.subList(0, 50) : result;
        } catch (Exception e) {
            log.error("[抖音热榜] 抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<HotItemDTO> parseResponse(String body) {
        List<HotItemDTO> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("list");
            if (!data.isArray()) {
                log.warn("[抖音热榜] list 字段非数组");
                return list;
            }

            int rank = 1;
            for (JsonNode item : data) {
                if (rank > 50) break;
                String title = item.path("title").asText("");
                if (title.isEmpty()) continue;

                String hotValue = item.path("hot_value").asText("");
                String url = item.path("url").asText("");

                list.add(HotItemDTO.builder()
                        .rank(rank++)
                        .title(title)
                        .hotScore(hotValue)
                        .url(url)
                        .build());
            }
        } catch (Exception e) {
            log.warn("[抖音热榜] 响应解析失败: {}", e.getMessage());
        }
        return list;
    }
}
