package com.fishingtime.hot.crawler;

import com.fishingtime.hot.dto.HotItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 虎扑热榜抓取器
 *
 * 抓取 https://bbs.hupu.com/topic-daily 步行街 24 小时榜
 *
 * 页面结构：
 * <li class="bbs-sl-web-post-body">
 *   <div class="post-title"><a href="/xxx.html" class="p-title">标题</a></div>
 *   <div class="post-datum">12 / 614</div>        ← 回复数 / 浏览数
 *   <div class="post-auth"><a href="...">作者</a></div>
 *   <div class="post-time">07-31 16:36</div>      ← 发布时间
 * </li>
 *
 * 注意：虎扑按回复时间排序，不设热度值（hotScore/normalizedHotScore 为 null）
 */
@Slf4j
@Component
public class HupuHotCrawler implements HotCrawler {

    private static final String HUPU_URL = "https://bbs.hupu.com/topic-daily";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    public String platform() {
        return "hupu";
    }

    @Override
    public List<HotItemDTO> fetch() {
        long start = System.currentTimeMillis();
        try {
            Document doc = Jsoup.connect(HUPU_URL)
                    .userAgent(USER_AGENT)
                    .referrer("https://bbs.hupu.com/")
                    .timeout(10_000)
                    .get();

            List<HotItemDTO> result = parseHtml(doc);
            log.info("[虎扑热榜] 解析到 {} 条数据，耗时 {}ms",
                    result.size(), System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[虎扑热榜] 抓取异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<HotItemDTO> parseHtml(Document doc) {
        List<HotItemDTO> list = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        Elements items = doc.select("li.bbs-sl-web-post-body");
        int rank = 0;

        for (Element item : items) {
            try {
                // 标题 + 链接
                Element titleA = item.selectFirst(".post-title a");
                if (titleA == null) continue;
                String title = titleA.text().trim();
                if (title.isEmpty()) continue;

                String href = titleA.attr("href");
                String url = href.startsWith("http") ? href : "https://bbs.hupu.com" + href;

                // 按 URL 去重
                if (!seenUrls.add(url)) continue;

                // 回复/浏览
                String datum = item.selectFirst(".post-datum") != null
                        ? item.selectFirst(".post-datum").text().trim() : "";
                Integer replyCount = parseCount(datum, 0);
                Integer viewCount = parseCount(datum, 1);

                // 作者
                String author = item.selectFirst(".post-auth") != null
                        ? item.selectFirst(".post-auth").text().trim() : "";

                // 发布时间
                String publishTime = item.selectFirst(".post-time") != null
                        ? item.selectFirst(".post-time").text().trim() : "";

                list.add(HotItemDTO.builder()
                        .rank(++rank)
                        .title(title)
                        .url(url)
                        .replyCount(replyCount)
                        .viewCount(viewCount)
                        .author(author)
                        .publishTime(publishTime)
                        // 不设 hotScore / normalizedHotScore（按回复时间排序）
                        .build());

                if (rank >= 30) break;
            } catch (Exception e) {
                log.debug("[虎扑热榜] 单条解析失败: {}", e.getMessage());
                // 跳过继续
            }
        }

        return list;
    }

    /**
     * 解析 "12 / 614" 格式的回复/浏览数
     * index=0 回复数，index=1 浏览数
     */
    private Integer parseCount(String datum, int index) {
        if (datum == null || datum.isEmpty()) return null;
        String[] parts = datum.split("/");
        if (parts.length <= index) return null;
        String num = parts[index].trim().replace(",", "");
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
