package com.fishingtime.diagnostics;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bounded JVM heap pressure simulation for end-to-end memory alert testing.
 *
 * Uses the same header gate as CpuBurnController: method=memory.
 */
@RestController
@RequestMapping("/internal/diagnostics")
public class MemoryPressureController {

    private static final int CHUNK_BYTES = 1024 * 1024;
    private static final long MAX_HELD_MIB = 256L;
    private static final long MIB = 1024L * 1024L;
    private static final int MAX_DURATION_SECONDS = 120;
    private static final double MIN_TARGET_RATIO = 0.81;
    private static final double MAX_TARGET_RATIO = 0.85;

    private final List<byte[]> retainedObjects = new ArrayList<>();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;

    private volatile boolean running;
    private volatile String status = "IDLE";
    private volatile String stopReason = "NOT_STARTED";
    private volatile double targetRatio = 0.82;
    private volatile int durationSeconds = 90;
    private volatile long maxHeldBytes = 256L * MIB;
    private volatile long startedAtMillis;
    private volatile long allocatedBytes;
    private volatile long heldBytes;

    @PostMapping("/memory-pressure/start")
    public ResponseEntity<Map<String, Object>> start(
            @RequestHeader(value = "method", required = false) String method,
            @RequestParam(defaultValue = "0.82") double targetRatio,
            @RequestParam(defaultValue = "90") int durationSeconds,
            @RequestParam(defaultValue = "256") long maxHeldMiB) {

        if (!"memory".equals(method)) {
            return response(HttpStatus.FORBIDDEN,
                    Map.of("error", "Invalid method header"));
        }

        if (!Double.isFinite(targetRatio)
                || targetRatio < MIN_TARGET_RATIO
                || targetRatio > MAX_TARGET_RATIO) {
            return response(HttpStatus.BAD_REQUEST,
                    Map.of("error", "targetRatio must be between 0.81 and 0.85"));
        }

        if (durationSeconds < 1 || durationSeconds > MAX_DURATION_SECONDS) {
            return response(HttpStatus.BAD_REQUEST,
                    Map.of("error", "durationSeconds must be between 1 and 120"));
        }

        if (maxHeldMiB < 1 || maxHeldMiB > MAX_HELD_MIB) {
            return response(HttpStatus.BAD_REQUEST,
                    Map.of("error", "maxHeldMiB must be between 1 and 256"));
        }

        synchronized (this) {
            if (running) {
                return response(HttpStatus.CONFLICT,
                        Map.of("error", "Memory pressure simulation already running",
                                "status", status));
            }

            Map<String, Object> heapBefore = heapSnapshot();
            double currentRatio = ((Number) heapBefore.get("usedRatio")).doubleValue();

            if (currentRatio >= targetRatio) {
                return response(HttpStatus.CONFLICT,
                        Map.of("error", "Current heap usage already meets target",
                                "heap", heapBefore));
            }

            this.targetRatio = targetRatio;
            this.durationSeconds = durationSeconds;
            this.maxHeldBytes = maxHeldMiB * MIB;
            this.startedAtMillis = System.currentTimeMillis();
            this.allocatedBytes = 0L;
            this.heldBytes = 0L;
            this.retainedObjects.clear();
            this.stopReason = "RUNNING";
            this.status = "RUNNING";
            this.running = true;

            this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "diagnostic-memory-pressure");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduledTask = executor.scheduleWithFixedDelay(
                    this::allocateStepSafely, 0, 250, TimeUnit.MILLISECONDS);

            Map<String, Object> body = new HashMap<>();
            body.put("status", status);
            body.put("targetRatio", targetRatio);
            body.put("durationSeconds", durationSeconds);
            body.put("maxHeldMiB", maxHeldMiB);
            body.put("maxHeldBytes", maxHeldBytes);
            body.put("heapBefore", heapBefore);
            body.put("message", "Memory pressure simulation started");
            return response(HttpStatus.ACCEPTED, body);
        }
    }

    @PostMapping("/memory-pressure/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestHeader(value = "method", required = false) String method) {

        if (!"memory".equals(method)) {
            return response(HttpStatus.FORBIDDEN,
                    Map.of("error", "Invalid method header"));
        }

        synchronized (this) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", status);
            body.put("running", running);
            body.put("stopReason", stopReason);
            body.put("targetRatio", targetRatio);
            body.put("durationSeconds", durationSeconds);
            body.put("maxHeldMiB", maxHeldBytes / MIB);
            body.put("maxHeldBytes", maxHeldBytes);
            body.put("allocatedBytes", allocatedBytes);
            body.put("heldBytes", heldBytes);
            body.put("maxHeldBytes", maxHeldBytes);
            body.put("heap", heapSnapshot());
            return ResponseEntity.ok(body);
        }
    }

    @PostMapping("/memory-pressure/stop")
    public ResponseEntity<Map<String, Object>> stop(
            @RequestHeader(value = "method", required = false) String method) {

        if (!"memory".equals(method)) {
            return response(HttpStatus.FORBIDDEN,
                    Map.of("error", "Invalid method header"));
        }

        synchronized (this) {
            if (running) {
                stopInternal("MANUAL_STOP");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("status", status);
            body.put("stopReason", stopReason);
            body.put("allocatedBytes", allocatedBytes);
            body.put("heldBytes", heldBytes);
            body.put("heap", heapSnapshot());
            body.put("message",
                    "References released; JVM GC determines when heap usage falls");
            return ResponseEntity.ok(body);
        }
    }

    private void allocateStepSafely() {
        try {
            synchronized (this) {
                if (!running) {
                    return;
                }

                long elapsedSeconds =
                        (System.currentTimeMillis() - startedAtMillis) / 1000L;
                if (elapsedSeconds >= durationSeconds) {
                    stopInternal("TIME_LIMIT_REACHED");
                    return;
                }

                double currentRatio =
                        ((Number) heapSnapshot().get("usedRatio")).doubleValue();
                if (currentRatio >= targetRatio) {
                    stopInternal("TARGET_REACHED");
                    return;
                }

                if (heldBytes + CHUNK_BYTES > maxHeldBytes) {
                    stopInternal("ALLOCATION_CAP_REACHED");
                    return;
                }

                byte[] block = new byte[CHUNK_BYTES];
                Arrays.fill(block, (byte) 1);
                retainedObjects.add(block);
                heldBytes += block.length;
                allocatedBytes += block.length;
            }
        } catch (RuntimeException ex) {
            synchronized (this) {
                stopInternal("ALLOCATION_ERROR");
            }
        } catch (OutOfMemoryError error) {
            synchronized (this) {
                stopInternal("OUT_OF_MEMORY");
            }
        }
    }

    private void stopInternal(String reason) {
        running = false;
        stopReason = reason;
        status = "STOPPED";

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        retainedObjects.clear();
        heldBytes = 0L;
    }

    private Map<String, Object> heapSnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long maxBytes = runtime.maxMemory();

        Map<String, Object> body = new HashMap<>();
        body.put("usedBytes", usedBytes);
        body.put("committedBytes", runtime.totalMemory());
        body.put("maxBytes", maxBytes);
        body.put("usedRatio", maxBytes > 0 ? (double) usedBytes / maxBytes : 0.0);
        return body;
    }

    private ResponseEntity<Map<String, Object>> response(
            HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).body(body);
    }
}
