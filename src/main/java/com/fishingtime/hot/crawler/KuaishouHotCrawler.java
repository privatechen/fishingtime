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
 * 快手热榜抓取器（api.tcslw.cn，普通 10 分钟刷新）
 *
 * API: GET {kuaishou.hotlist-api}
 *
 * 响应 data[]：
 *   数组顺序  → rank（1 开始）
 *   title     → title
 *   heat      → hotScore（原始字符串，如 "1346.6万"）+ normalizedHotScore（HotScoreParser）
 *   url       → url
 *   其余字段（summary/replyCount/viewCount/author/publishTime）置空
 */
@Slf4j
@Component
public class KuaishouHotCrawler implements HotCrawler {

    private final String apiUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public KuaishouHotCrawler(@Value("${kuaishou.hotlist-api}") String apiUrl) {
        this.apiUrl = apiUrl;
    }

    @Override
    public String platform() {
        return "kuaishou";
    }

    /** 普通平台：走常规 10 分钟定时刷新 */
    @Override
    public boolean quotaLimited() {
        return false;
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[快手热榜] API 返回 status={}", resp.statusCode());
                return List.of();
            }

            List<HotItemDTO> result = parseResponse(resp.body());
            HotScoreParser.normalizeScores(result);
            log.info("[快手热榜] 解析到 {} 条数据，耗时 {}ms",
                    result.size(), System.currentTimeMillis() - start);
            return result.size() > 50 ? result.subList(0, 50) : result;
        } catch (Exception e) {
            log.error("[快手热榜] 抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<HotItemDTO> parseResponse(String body) {
        List<HotItemDTO> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                log.warn("[快手热榜] data 字段非数组");
                return list;
            }

            int rank = 1;
            for (JsonNode item : data) {
                if (rank > 50) break;
                String title = item.path("title").asText("");
                if (title.isEmpty()) continue;

                String heat = item.path("heat").asText("");
                String url = item.path("url").asText("");

                list.add(HotItemDTO.builder()
                        .rank(rank++)
                        .title(title)
                        .hotScore(heat)
                        .url(url)
                        .build());
            }
        } catch (Exception e) {
            log.warn("[快手热榜] 响应解析失败: {}", e.getMessage());
        }
        return list;
    }
}
