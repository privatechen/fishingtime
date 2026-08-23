package com.fishingtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信小程序多应用配置（wechat.apps）
 *
 * 支持多个小程序共用一套后端：客户端上报自己的 appId，后端按 appId 用对应 secret 换 openid。
 * 各 appid 的 secret 用环境变量注入（不写死在版本库）。
 */
@Component
@ConfigurationProperties(prefix = "wechat")
@Data
public class WechatProperties {

    private List<App> apps = new ArrayList<>();

    @Data
    public static class App {
        private String appid;
        private String secret;
    }

    /** 按 appid 查对应 secret；未配置返回 null */
    public String secretFor(String appid) {
        if (appid == null) {
            return null;
        }
        return apps.stream()
                .filter(a -> appid.equals(a.getAppid()))
                .map(App::getSecret)
                .findFirst()
                .orElse(null);
    }
}
