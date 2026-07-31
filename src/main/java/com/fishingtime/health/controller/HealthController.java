package com.fishingtime.health.controller;

import com.fishingtime.health.dto.ReadinessResponse;
import com.fishingtime.health.service.ReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查 API
 *
 * GET /api/health/readiness — 就绪探针
 *   就绪 → HTTP 200
 *   未就绪（如数据库不可用）→ HTTP 503
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final ReadinessService readinessService;

    @GetMapping("/readiness")
    public ResponseEntity<ReadinessResponse> readiness() {
        ReadinessResponse response = readinessService.buildResponse();
        HttpStatus status = "READY".equals(response.getStatus())
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}
