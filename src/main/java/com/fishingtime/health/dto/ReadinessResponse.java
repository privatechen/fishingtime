package com.fishingtime.health.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 就绪检查响应
 */
@Data
public class ReadinessResponse {

    /** READY / NOT_READY */
    private String status;

    /** 各检查项结果 */
    private Map<String, String> checks = new LinkedHashMap<>();

    private long timestamp;

    public ReadinessResponse() {
        this.timestamp = System.currentTimeMillis() / 1000;
    }

    public void addCheck(String name, boolean ok) {
        checks.put(name, ok ? "UP" : "DOWN");
    }
}
