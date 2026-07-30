package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.util.HotScoreParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 百度热榜抓取器
 *
 * 抓取 https://top.baidu.com/board?tab=realtime
 * 解析 HTML 中嵌入的 JSON 数据，兜底解析 DOM 元素
 */
@Slf4j
@Component
public class BaiduHotCrawler implements HotCrawler {

    private static final String BAIDU_URL = "https://top.baidu.com/board?tab=realtime";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    public String platform() {
        return "baidu";
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();
        try {
            Document doc = Jsoup.connect(BAIDU_URL)
                    .userAgent(USER_AGENT)
                    .timeout(10_000)
                    .get();

            // 方案一：尝试从 <script> 中提取 JSON 数据
            List<HotItemDTO> result = tryParseFromScript(doc);
            if (result != null && !result.isEmpty()) {
                HotScoreParser.normalizeScores(result);
                log.info("[百度热榜] 从 script 解析到 {} 条数据，耗时 {}ms",
                        result.size(), System.currentTimeMillis() - start);
                return result.size() > 30 ? result.subList(0, 30) : result;
            }

            // 方案二：兜底从 DOM 元素解析
            result = tryParseFromDom(doc);
            if (result != null && !result.isEmpty()) {
                HotScoreParser.normalizeScores(result);
                log.info("[百度热榜] 从 DOM 解析到 {} 条数据，耗时 {}ms",
                        result.size(), System.currentTimeMillis() - start);
                return result.size() > 30 ? result.subList(0, 30) : result;
            }

            log.warn("[百度热榜] 页面解析失败，未找到热榜数据");
            return List.of();
        } catch (Exception e) {
            log.error("[百度热榜] 抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从页面内嵌的 script JSON 中提取数据
     */
    private List<HotItemDTO> tryParseFromScript(Document doc) {
        try {
            // 查找包含热搜数据的 script 标签
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                String data = script.html();
                if (data.contains("\"board\"") && data.contains("\"title\"")) {
                    return parseJsonData(data);
                }
            }
        } catch (Exception e) {
            log.debug("[百度热榜] script 解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 从 JSON 字符串中提取热榜条目
     */
    private List<HotItemDTO> parseJsonData(String json) {
        List<HotItemDTO> list = new ArrayList<>();
        try {
            // 提取所有 title 和 hotScore 的简单正则匹配
            // 更健壮的做法：反序列化完整 JSON
            String[] items = json.split("\\{\"index\":");
            int rank = 1;
            for (int i = 1; i < items.length && rank <= 30; i++) {
                String item = items[i];
                String title = extractJsonString(item, "\"word\"");
                String hotScore = extractJsonString(item, "\"hotScore\"");
                String url = extractJsonString(item, "\"url\"");
                if (title != null) {
                    list.add(HotItemDTO.builder()
                            .rank(rank++)
                            .title(title)
                            .hotScore(hotScore != null ? hotScore : "")
                            .url(url)
                            .build());
                }
            }
        } catch (Exception e) {
            log.debug("[百度热榜] JSON 解析失败: {}", e.getMessage());
        }
        return list;
    }

    private String extractJsonString(String text, String key) {
        int idx = text.indexOf(key);
        if (idx < 0) return null;
        int start = text.indexOf('"', idx + key.length() + 2);
        if (start < 0) {
            int numStart = text.indexOf(':', idx + key.length());
            if (numStart < 0) return null;
            int numEnd = text.indexOf(',', numStart);
            if (numEnd < 0) numEnd = text.indexOf('}', numStart);
            if (numEnd < 0) return null;
            return text.substring(numStart + 1, numEnd).trim();
        }
        int end = text.indexOf('"', start + 1);
        if (end < 0) return null;
        return text.substring(start + 1, end);
    }

    /**
     * 从 HTML DOM 元素中提取数据
     */
    private List<HotItemDTO> tryParseFromDom(Document doc) {
        List<HotItemDTO> list = new ArrayList<>();
        try {
            Elements items = doc.select("[class*=category-wrap]");
            if (items.isEmpty()) {
                items = doc.select(".content_1YWBm").parents();
            }
            if (items.isEmpty()) {
                return List.of();
            }

            int rank = 1;
            for (Element item : items) {
                String title = item.select("[class*=content]").text();
                String hotScore = item.select("[class*=hot-index]").text();
                String url = item.select("a").attr("href");

                if (title.isEmpty()) continue;

                if (!url.isEmpty() && !url.startsWith("http")) {
                    url = "https://top.baidu.com" + url;
                }

                list.add(HotItemDTO.builder()
                        .rank(rank++)
                        .title(title)
                        .hotScore(hotScore)
                        .url(url)
                        .build());

                if (rank > 30) break;
            }
        } catch (Exception e) {
            log.debug("[百度热榜] DOM 解析失败: {}", e.getMessage());
        }
        return list;
    }
}
