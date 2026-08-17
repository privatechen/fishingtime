package com.fishingtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 《细节》管理后台配置（app.detail.*）
 *
 * image-dir：图片存放目录（生产为服务器路径，不在 jar 内 → 加图无需重新部署）
 * admin-user / admin-password：管理后台登录凭据
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.detail")
public class DetailProperties {

    /** 图片目录（不存在时自动创建） */
    private String imageDir;

    private String adminUser = "admin";
    private String adminPassword = "admin";
}
