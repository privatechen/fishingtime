package com.fishingtime.partygame.drawguess;

/** Normalized drawing coordinate. */
public record DrawPoint(float x, float y) {
    public DrawPoint {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0f || x > 1f || y < 0f || y > 1f) {
            throw new IllegalArgumentException("绘画坐标必须是 0 到 1 之间的有限数值");
        }
    }
}
