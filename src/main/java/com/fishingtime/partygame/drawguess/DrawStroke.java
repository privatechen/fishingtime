package com.fishingtime.partygame.drawguess;

import java.util.ArrayList;
import java.util.List;

/** One bounded, server-accepted drawing stroke. Coordinates are normalized to [0,1]. */
public class DrawStroke {
    private final long strokeId;
    private final String color;
    private final float width;
    private final List<DrawPoint> points = new ArrayList<>();
    private boolean ended;
    private int nextSequence;

    public DrawStroke(long strokeId, String color, float width) {
        this.strokeId = strokeId;
        this.color = color;
        this.width = width;
    }

    public long getStrokeId() { return strokeId; }
    public String getColor() { return color; }
    public float getWidth() { return width; }
    public List<DrawPoint> getPoints() { return List.copyOf(points); }
    public boolean isEnded() { return ended; }
    public void setEnded(boolean ended) { this.ended = ended; }
    public int getNextSequence() { return nextSequence; }
    public void append(List<DrawPoint> additions) {
        points.addAll(additions);
        nextSequence++;
    }
}
