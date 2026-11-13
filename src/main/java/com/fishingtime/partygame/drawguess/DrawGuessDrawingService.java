package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates and stores incremental drawing operations in bounded room memory.
 * A WebSocket handler can broadcast the returned accepted operation to peers.
 */
@Service
public class DrawGuessDrawingService {
    public static final int MAX_STROKES_PER_ROUND = 300;
    public static final int MAX_POINTS_PER_PACKET = 64;
    public static final int MAX_POINTS_PER_ROUND = 6000;
    public static final int MAX_POINTS_PER_STROKE = 1000;
    public static final int MAX_PACKETS_PER_SECOND = 20;
    public static final float MIN_BRUSH_WIDTH = 1f;
    public static final float MAX_BRUSH_WIDTH = 24f;
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private final DrawGuessManager manager;

    public DrawGuessDrawingService(DrawGuessManager manager) { this.manager = manager; }

    public StrokeStarted begin(String roomId, String playerId, String color, float width, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireArtistDrawing(room, playerId, now);
            if (room.getStrokes().size() >= MAX_STROKES_PER_ROUND) throw new IllegalArgumentException("本轮笔画数量已达到上限");
            if (color == null || !COLOR_PATTERN.matcher(color).matches()) throw new IllegalArgumentException("画笔颜色格式无效");
            if (!Float.isFinite(width) || width < MIN_BRUSH_WIDTH || width > MAX_BRUSH_WIDTH) {
                throw new IllegalArgumentException("画笔粗细超出允许范围");
            }
            DrawStroke stroke = new DrawStroke(room.nextStrokeId(), color.toUpperCase(), width);
            room.getStrokes().add(stroke);
            room.touch();
            return new StrokeStarted(stroke.getStrokeId(), stroke.getColor(), stroke.getWidth());
        }
    }

    public PointsAccepted append(String roomId, String playerId, long strokeId, int sequence,
                                  List<DrawPoint> points, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireArtistDrawing(room, playerId, now);
            if (points == null || points.isEmpty() || points.size() > MAX_POINTS_PER_PACKET) {
                throw new IllegalArgumentException("单次绘画点数必须为 1 到 " + MAX_POINTS_PER_PACKET);
            }
            DrawStroke stroke = findActiveStroke(room, strokeId);
            if (sequence != stroke.getNextSequence()) throw new IllegalArgumentException("绘画数据序号不连续");
            if (stroke.getPoints().size() + points.size() > MAX_POINTS_PER_STROKE
                    || room.getTotalDrawingPoints() + points.size() > MAX_POINTS_PER_ROUND) {
                throw new IllegalArgumentException("本轮绘画数据量已达到上限");
            }
            stroke.append(List.copyOf(points));
            room.addDrawingPoints(points.size());
            room.touch();
            return new PointsAccepted(strokeId, sequence, List.copyOf(points));
        }
    }

    public StrokeEnded end(String roomId, String playerId, long strokeId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireArtistDrawing(room, playerId, now);
            DrawStroke stroke = findActiveStroke(room, strokeId);
            stroke.setEnded(true);
            room.touch();
            return new StrokeEnded(strokeId);
        }
    }

    public UndoResult undo(String roomId, String playerId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireArtistDrawing(room, playerId, now);
            if (room.getStrokes().isEmpty()) return new UndoResult(false, null);
            DrawStroke removed = room.getStrokes().remove(room.getStrokes().size() - 1);
            room.addDrawingPoints(-removed.getPoints().size());
            room.touch();
            return new UndoResult(true, removed.getStrokeId());
        }
    }

    public ClearResult clear(String roomId, String playerId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireArtistDrawing(room, playerId, now);
            int removed = room.getStrokes().size();
            room.resetDrawing();
            room.touch();
            return new ClearResult(removed);
        }
    }

    /** Returns a defensive snapshot suitable for reconnect or late room sync. */
    public DrawingSnapshot snapshot(String roomId) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            List<StrokeSnapshot> strokes = room.getStrokes().stream()
                    .map(s -> new StrokeSnapshot(s.getStrokeId(), s.getColor(), s.getWidth(),
                            s.getPoints(), s.isEnded(), s.getNextSequence()))
                    .toList();
            return new DrawingSnapshot(room.getCurrentRound(), strokes, room.getTotalDrawingPoints());
        }
    }

    private void requireArtistDrawing(DrawGuessRoom room, String playerId, long now) {
        if (room.getState() != DrawGuessState.DRAWING) throw new IllegalArgumentException("当前不在绘画阶段");
        if (now >= room.getRoundDeadline()) throw new IllegalArgumentException("绘画时间已结束");
        if (playerId == null || !playerId.equals(room.getArtistId())) throw new IllegalArgumentException("只有当前画师可以操作画板");
        DrawGuessPlayer artist = room.getPlayers().get(playerId);
        if (artist == null || !artist.isOnline()) throw new IllegalArgumentException("画师当前连接不可用");
    }

    private DrawStroke findActiveStroke(DrawGuessRoom room, long strokeId) {
        for (int i = room.getStrokes().size() - 1; i >= 0; i--) {
            DrawStroke stroke = room.getStrokes().get(i);
            if (stroke.getStrokeId() == strokeId) {
                if (stroke.isEnded()) throw new IllegalArgumentException("该笔画已经结束");
                return stroke;
            }
        }
        throw new IllegalArgumentException("笔画不存在");
    }

    public record StrokeStarted(long strokeId, String color, float width) {}
    public record PointsAccepted(long strokeId, int sequence, List<DrawPoint> points) {}
    public record StrokeEnded(long strokeId) {}
    public record UndoResult(boolean changed, Long removedStrokeId) {}
    public record ClearResult(int removedStrokes) {}
    public record StrokeSnapshot(long strokeId, String color, float width, List<DrawPoint> points,
                                 boolean ended, int nextSequence) {}
    public record DrawingSnapshot(int round, List<StrokeSnapshot> strokes, int totalPoints) {}
}
