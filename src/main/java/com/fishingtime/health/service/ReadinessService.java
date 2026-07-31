package com.fishingtime.health.service;

import com.fishingtime.health.dto.ReadinessResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 就绪检查服务
 *
 * 验证应用是否真正就绪：
 * - app: 应用进程存活
 * - database: MySQL 连接可用（SELECT 1）
 *
 * 外部服务（热榜/天气）不作为就绪判断依据——它们挂了只是功能降级，应用仍可服务。
 */
@Slf4j
@Service
public class ReadinessService {

    private final DataSource dataSource;

    public ReadinessService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行就绪检查
     *
     * @return true = 就绪，false = 未就绪
     */
    public boolean check() {
        ReadinessResponse response = buildResponse();
        return "READY".equals(response.getStatus());
    }

    /**
     * 构建就绪检查响应
     */
    public ReadinessResponse buildResponse() {
        ReadinessResponse response = new ReadinessResponse();

        // app 检查（进程存活，天然通过）
        response.addCheck("app", true);

        // database 检查
        boolean dbOk = checkDatabase();
        response.addCheck("database", dbOk);

        // 总体状态
        response.setStatus(dbOk ? "READY" : "NOT_READY");

        return response;
    }

    /**
     * 检查数据库连接是否可用
     */
    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            log.warn("[就绪检查] 数据库连接失败: {}", e.getMessage());
            return false;
        }
    }
}
