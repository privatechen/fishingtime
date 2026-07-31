package com.fishingtime.weather.util;

import javax.servlet.http.HttpServletRequest;

/**
 * 真实客户端 IP 解析工具
 *
 * 从代理头中取真实 IP，过滤 unknown、空值、内网地址。
 */
public final class IpUtils {

    private static final String UNKNOWN = "unknown";

    private IpUtils() {}

    /**
     * 获取真实客户端 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return null;

        // 依次从代理头中取
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // X-Forwarded-For 可能是逗号分隔的多个 IP，取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIp(ip)) {
                    return ip;
                }
            }
        }

        // 兜底：取连接 IP
        return request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        if (UNKNOWN.equalsIgnoreCase(ip)) return false;
        return true;
    }
}
