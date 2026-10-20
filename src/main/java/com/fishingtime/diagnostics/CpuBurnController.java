package com.fishingtime.diagnostics;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, manually triggered CPU load for diagnosis validation.
 */
@RestController
@RequestMapping("/internal/diagnostics")
public class CpuBurnController {

    private static final int THREAD_COUNT = 2;
    private static final int MAX_DURATION_SECONDS = 30;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private static volatile double calculationSink;

    @PostMapping("/cpu-burn")
    public ResponseEntity<Map<String, Object>> start(
            @RequestHeader(value = "method", required = false) String method,
            @RequestParam(defaultValue = "20") int duration) {

        if (!"cpu".equals(method)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid method header"));
        }

        if (duration < 1 || duration > MAX_DURATION_SECONDS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "duration must be between 1 and 30 seconds"));
        }

        if (!running.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "CPU burn already running"));
        }

        stopRequested.set(false);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(duration);
        AtomicInteger remaining = new AtomicInteger(THREAD_COUNT);

        for (int i = 1; i <= THREAD_COUNT; i++) {
            Thread worker = new Thread(() -> {
                double value = 1.0;
                try {
                    while (!stopRequested.get() && System.nanoTime() < deadline) {
                        for (int j = 0; j < 4096; j++) {
                            value = Math.sqrt(value + j + 1.0);
                        }
                        calculationSink = value;
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        running.set(false);
                    }
                }
            }, "diagnostic-cpu-burn-" + i);
            worker.setDaemon(true);
            worker.start();
        }

        return ResponseEntity.accepted().body(Map.of(
                "status", "STARTED",
                "durationSeconds", duration,
                "threads", THREAD_COUNT
        ));
    }

    @PostMapping("/cpu-burn/stop")
    public ResponseEntity<Map<String, Object>> stop(
            @RequestHeader(value = "method", required = false) String method) {

        if (!"cpu".equals(method)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid method header"));
        }

        stopRequested.set(true);
        return ResponseEntity.ok().body(Map.of("status", "STOP_REQUESTED"));
    }
}
