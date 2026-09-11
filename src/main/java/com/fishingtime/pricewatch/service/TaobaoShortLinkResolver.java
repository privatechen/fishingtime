package com.fishingtime.pricewatch.service;

import org.springframework.stereotype.Service;

@Service
public class TaobaoShortLinkResolver {

    private final TaobaoShortLinkPriceClient priceClient;

    public TaobaoShortLinkResolver(TaobaoShortLinkPriceClient priceClient) {
        this.priceClient = priceClient;
    }

    public ResolveResult resolve(String input) {
        TaobaoShortLinkPriceClient.ResolveResult resolved = priceClient.resolveAndCollect(input);
        String itemId = resolved.getItemId();
        String sourceUrl = resolved.getShortUrl();
        String normalizedUrl = "https://item.taobao.com/item.htm?id=" + itemId;
        return new ResolveResult(itemId, sourceUrl, normalizedUrl, normalizedUrl, 200);
    }

    public static class ResolveResult {
        private final String itemId;
        private final String sourceUrl;
        private final String finalUrl;
        private final String normalizedUrl;
        private final int statusCode;

        public ResolveResult(String itemId, String sourceUrl, String finalUrl, String normalizedUrl, int statusCode) {
            this.itemId = itemId;
            this.sourceUrl = sourceUrl;
            this.finalUrl = finalUrl;
            this.normalizedUrl = normalizedUrl;
            this.statusCode = statusCode;
        }

        public String getItemId() { return itemId; }
        public String getSourceUrl() { return sourceUrl; }
        public String getFinalUrl() { return finalUrl; }
        public String getNormalizedUrl() { return normalizedUrl; }
        public int getStatusCode() { return statusCode; }
    }
}
