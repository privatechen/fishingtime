package com.fishingtime.partygame.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.partygame.drawguess.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DrawGuessWebSocketRouter {
    private static final Logger log = LoggerFactory.getLogger(DrawGuessWebSocketRouter.class);
    private final DrawGuessManager manager;
    private final DrawGuessGameService game;
    private final DrawGuessDrawingService drawing;
    private final DrawGuessReconnectService reconnect;
    private final ObjectMapper mapper;

    public DrawGuessWebSocketRouter(DrawGuessManager manager, DrawGuessGameService game,
            DrawGuessDrawingService drawing, DrawGuessReconnectService reconnect, ObjectMapper mapper) {
        this.manager=manager; this.game=game; this.drawing=drawing; this.reconnect=reconnect; this.mapper=mapper;
    }
    public boolean supports(String type) { return type != null && type.startsWith("DG_"); }

    public void handle(WebSocketSession session, JsonNode body, String type) throws Exception {
        try {
            switch(type) {
                case "DG_CREATE_ROOM" -> {
                    DrawGuessRoom room=manager.create(session,text(body,"nickname","玩家"));
                    DrawGuessPlayer p=manager.playerForSession(session);
                    send(session,"DG_ROOM_CREATED",Map.of("room",manager.snapshot(room),"playerId",p.getId(),"reconnectToken",p.getReconnectToken()));
                }
                case "DG_JOIN_ROOM" -> {
                    DrawGuessRoom room=manager.join(session,required(body,"roomId"),text(body,"nickname","玩家"));
                    DrawGuessPlayer p=manager.playerForSession(session);
                    broadcast(room,"DG_ROOM_UPDATED",manager.snapshot(room));
                    send(session,"DG_ROOM_JOINED",Map.of("room",manager.snapshot(room),"playerId",p.getId(),"reconnectToken",p.getReconnectToken()));
                }
                case "DG_RECONNECT" -> {
                    var result=reconnect.reconnect(session,required(body,"roomId"),required(body,"playerId"),required(body,"reconnectToken"),System.currentTimeMillis());
                    send(session,"DG_RECONNECTED",result);
                    broadcastExcept(manager.get(result.roomId()),session,"DG_ROOM_UPDATED",manager.snapshot(manager.get(result.roomId())));
                }
                case "DG_START", "DG_NEXT_ROUND" -> {
                    String rid=roomId(session);
                    var setup=type.equals("DG_START")?game.startGame(rid,playerId(session),System.currentTimeMillis()):game.nextRound(rid,playerId(session),System.currentTimeMillis());
                    DrawGuessRoom room=manager.get(rid);
                    broadcast(room,"DG_ROUND_SELECTING",Map.of("round",setup.round(),"artistId",setup.artistId(),"artistNickname",setup.artistNickname(),"selectionDeadline",setup.selectionDeadline()));
                    sendArtistOptions(room);
                }
                case "DG_REMATCH_REQUEST" -> {
                    String rid=roomId(session), pid=playerId(session);
                    DrawGuessRoom room=manager.get(rid);
                    synchronized(room) {
                        if(!pid.equals(room.getHostPlayerId())) throw new IllegalArgumentException("只有房主可以发起再来一局");
                        if(room.getState()!=DrawGuessState.GAME_OVER) throw new IllegalArgumentException("当前不能发起再来一局");
                        long online=room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline).count();
                        if(online<DrawGuessManager.MIN_PLAYERS) throw new IllegalArgumentException("至少需要两名在线玩家");
                        room.clearRematch();
                        room.getRematchVotes().put(pid,true);
                        room.setRematchDeadline(System.currentTimeMillis()+30_000L);
                        room.touch();
                    }
                    DrawGuessPlayer inviter=room.getPlayers().get(pid);
                    Map<String,Object> invite=Map.of("hostPlayerId",pid,"hostNickname",inviter.getNickname(),
                            "deadline",room.getRematchDeadline(),"roomId",rid);
                    for(WebSocketSession peer:manager.sessions(room))
                        if(!playerId(peer).equals(pid)) send(peer,"DG_REMATCH_INVITE",invite);
                    send(session,"DG_REMATCH_PENDING",Map.of("deadline",room.getRematchDeadline()));
                }
                case "DG_REMATCH_VOTE" -> {
                    String rid=roomId(session), pid=playerId(session);
                    DrawGuessRoom room=manager.get(rid);
                    boolean accept=body.path("accept").asBoolean(false);
                    synchronized(room) {
                        if(room.getState()!=DrawGuessState.GAME_OVER || room.getRematchDeadline()==0
                                || System.currentTimeMillis()>=room.getRematchDeadline())
                            throw new IllegalArgumentException("再来一局邀请已失效");
                        if(pid.equals(room.getHostPlayerId())) throw new IllegalArgumentException("房主无需确认");
                        if(!room.getRematchVotes().containsKey(room.getHostPlayerId()))
                            throw new IllegalArgumentException("邀请状态无效");
                        if(room.getRematchVotes().containsKey(pid))
                            throw new IllegalArgumentException("你已经选择过是否继续");
                        room.getRematchVotes().put(pid,accept);
                        room.touch();
                    }
                    boolean allResponded;
                    synchronized(room) {
                        allResponded=room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline)
                                .allMatch(p->room.getRematchVotes().containsKey(p.getId()));
                    }
                    if(allResponded) resolveRematch(room,false);
                    else broadcast(room,"DG_REMATCH_VOTE_UPDATED",Map.of("playerId",pid,"accepted",accept));
                }
                case "DG_SELECT_WORD" -> {
                    String rid=roomId(session);
                    var selected=game.selectWord(rid,playerId(session),body.path("wordId").asInt(-1),System.currentTimeMillis());
                    DrawGuessRoom room=manager.get(rid);
                    broadcast(room,"DG_DRAWING_STARTED",Map.of("round",selected.round(),"artistId",room.getArtistId(),"roundDeadline",selected.roundDeadline()));
                    send(session,"DG_WORD_SELECTED",Map.of("category",selected.category(),"answer",selected.answer(),"roundDeadline",selected.roundDeadline()));
                }
                case "DG_STROKE_START" -> {
                    var v=drawing.begin(roomId(session),playerId(session),required(body,"color"),(float)body.path("width").asDouble(3),System.currentTimeMillis());
                    broadcast(manager.get(roomId(session)),"DG_STROKE_STARTED",v);
                }
                case "DG_STROKE_POINTS" -> {
                    JsonNode arr=body.path("points");
                    if(!arr.isArray()||arr.isEmpty()||arr.size()>DrawGuessDrawingService.MAX_POINTS_PER_PACKET) throw new IllegalArgumentException("绘画点数据无效");
                    List<DrawPoint> points=new ArrayList<>();
                    for(JsonNode p:arr) points.add(new DrawPoint((float)p.path("x").asDouble(Double.NaN),(float)p.path("y").asDouble(Double.NaN)));
                    var v=drawing.append(roomId(session),playerId(session),body.path("strokeId").asLong(-1),body.path("sequence").asInt(-1),points,System.currentTimeMillis());
                    broadcast(manager.get(roomId(session)),"DG_STROKE_POINTS",v);
                }
                case "DG_STROKE_END" -> {
                    var v=drawing.end(roomId(session),playerId(session),body.path("strokeId").asLong(-1),System.currentTimeMillis());
                    broadcast(manager.get(roomId(session)),"DG_STROKE_ENDED",v);
                }
                case "DG_UNDO" -> {
                    var v=drawing.undo(roomId(session),playerId(session),System.currentTimeMillis());
                    if(v.changed()) broadcast(manager.get(roomId(session)),"DG_DRAWING_UNDONE",v);
                }
                case "DG_CLEAR" -> {
                    var v=drawing.clear(roomId(session),playerId(session),System.currentTimeMillis());
                    broadcast(manager.get(roomId(session)),"DG_DRAWING_CLEARED",v);
                }
                case "DG_SUBMIT_GUESS" -> {
                    var v=game.submitGuess(roomId(session),playerId(session),body.path("answer").asText(null),System.currentTimeMillis());
                    send(session,v.correct()?"DG_GUESS_CORRECT":"DG_GUESS_INCORRECT",v);
                    if(v.correct()) {
                        DrawGuessRoom room=manager.get(roomId(session));
                        broadcastExcept(room,session,"DG_PLAYER_GUESSED",Map.of("playerId",v.playerId(),"rank",v.rank(),"points",v.points()));
                        if(v.allGuessed()) finishRound(room,"ALL_GUESSED");
                    }
                }
                case "DG_LEAVE_ROOM" -> {
                    String rid=roomId(session);
                    String pid=playerId(session);
                    DrawGuessRoom current=manager.get(rid);
                    boolean artist=pid.equals(current.getArtistId());
                    DrawGuessState state=current.getState();
                    if(artist && state==DrawGuessState.DRAWING) {
                        broadcast(current,"DG_ROUND_RESULT",
                                game.endRound(rid,"ARTIST_DISCONNECTED",System.currentTimeMillis()));
                    } else if(artist && state==DrawGuessState.SELECTING) {
                        synchronized(current) {
                            current.setState(current.getCurrentRound()>=current.getTotalRounds()
                                    ? DrawGuessState.GAME_OVER : DrawGuessState.ROUND_RESULT);
                            current.setSelectionDeadline(0);
                            current.touch();
                        }
                        broadcast(current,"DG_ROUND_ABORTED",manager.snapshot(current));
                    }
                    DrawGuessRoom room=manager.leave(session);
                    send(session,"DG_LEFT_ROOM",Map.of());
                    if(room!=null && !manager.sessions(room).isEmpty())
                        broadcast(room,"DG_ROOM_UPDATED",manager.snapshot(room));
                }
                default -> throw new IllegalArgumentException("未知的你画我猜消息类型");
            }
        } catch(IllegalArgumentException ex) {
            send(session,"DG_ERROR",Map.of("message",ex.getMessage()==null?"请求无效":ex.getMessage()));
        } catch(Exception ex) {
            log.error("[DrawGuess] message failed, session={}",session.getId(),ex);
            send(session,"DG_ERROR",Map.of("message","服务器处理失败"));
        }
    }

    public void disconnected(WebSocketSession session) {
        try {
            var v=reconnect.disconnect(session,System.currentTimeMillis());
            if(v==null) return;
            DrawGuessRoom room=manager.get(v.roomId());
            broadcast(room,"DG_PLAYER_DISCONNECTED",Map.of("playerId",v.playerId(),"artist",v.artist(),"room",v.roomSnapshot()));
            if(v.roundEnd()!=null) broadcast(room,"DG_ROUND_RESULT",v.roundEnd());
            else if(v.artist()) broadcast(room,"DG_ROUND_ABORTED",v.roomSnapshot());
        } catch(Exception ex) { log.warn("[DrawGuess] disconnect failed",ex); }
    }

    @Scheduled(fixedDelay = 500)
    public void tick() {
        long now=System.currentTimeMillis();
        for(DrawGuessRoom room:manager.allRooms()) {
            try {
                boolean rematchExpired;
                synchronized(room) {
                    rematchExpired=room.getRematchDeadline()>0 && now>=room.getRematchDeadline();
                }
                if(rematchExpired) {
                    resolveRematch(room,true);
                    continue;
                }
                synchronized(room) {
                    if(room.getState()==DrawGuessState.SELECTING && now>=room.getSelectionDeadline()) {
                        var v=game.processSelectionTimeout(room.getRoomId(),now);
                        if(v!=null) {
                            broadcast(room,"DG_DRAWING_STARTED",Map.of("round",v.round(),"artistId",room.getArtistId(),"roundDeadline",v.roundDeadline()));
                            WebSocketSession artist=sessionFor(room,room.getArtistId());
                            if(artist!=null) send(artist,"DG_WORD_SELECTED",Map.of("category",v.category(),"answer",v.answer(),"roundDeadline",v.roundDeadline(),"autoSelected",true));
                        }
                    } else if(room.getState()==DrawGuessState.DRAWING && now>=room.getRoundDeadline()) finishRound(room,"TIMEOUT");
                }
            } catch(Exception ex) { log.warn("[DrawGuess] timer failed room={}",room.getRoomId(),ex); }
        }
    }

    /**
     * Accepted players continue together in a new room. Declined or timed-out
     * players remain in the original completed room with their result intact.
     */
    private void resolveRematch(DrawGuessRoom room, boolean timeout) throws Exception {
        java.util.Set<String> accepted = new java.util.LinkedHashSet<>();
        int onlineCount;
        boolean allAccepted;
        synchronized(room) {
            if(room.getState()!=DrawGuessState.GAME_OVER || room.getRematchDeadline()==0) return;
            if(!timeout && room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline)
                    .anyMatch(p->!room.getRematchVotes().containsKey(p.getId()))) return;
            onlineCount=(int)room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline).count();
            room.getPlayers().values().stream()
                    .filter(DrawGuessPlayer::isOnline)
                    .filter(p->Boolean.TRUE.equals(room.getRematchVotes().get(p.getId())))
                    .map(DrawGuessPlayer::getId).forEach(accepted::add);
            allAccepted=accepted.size()==onlineCount;
        }
        if(accepted.size()<DrawGuessManager.MIN_PLAYERS) {
            synchronized(room) { room.clearRematch(); room.touch(); }
            broadcast(room,"DG_REMATCH_CANCELLED",Map.of("reason","NOT_ENOUGH_ACCEPTED"));
            return;
        }

        DrawGuessRoom playingRoom=room;
        if(!allAccepted) {
            playingRoom=manager.splitForRematch(room,accepted);
        }
        var setup=game.startRematch(playingRoom.getRoomId(),System.currentTimeMillis());
        Map<String,Object> started=Map.of("roomId",playingRoom.getRoomId(),"round",setup.round(),
                "artistId",setup.artistId(),"artistNickname",setup.artistNickname(),
                "selectionDeadline",setup.selectionDeadline());
        broadcast(playingRoom,"DG_REMATCH_STARTED",started);
        sendArtistOptions(playingRoom);
        if(!allAccepted) {
            broadcast(room,"DG_REMATCH_CANCELLED",Map.of("reason","SPLIT"));
            broadcast(room,"DG_ROOM_UPDATED",manager.snapshot(room));
        }
    }

    private void finishRound(DrawGuessRoom room,String reason) throws Exception {
        broadcast(room,"DG_ROUND_RESULT",game.endRound(room.getRoomId(),reason,System.currentTimeMillis()));
    }
    private void sendArtistOptions(DrawGuessRoom room) throws Exception {
        WebSocketSession artist=sessionFor(room,room.getArtistId());
        if(artist!=null) send(artist,"DG_WORD_OPTIONS",game.privateArtistOptions(room.getRoomId(),room.getArtistId()));
    }
    private WebSocketSession sessionFor(DrawGuessRoom room,String pid) {
        return manager.sessions(room).stream().filter(s->pid.equals(s.getAttributes().get("drawGuessPlayerId"))).findFirst().orElse(null);
    }
    private String roomId(WebSocketSession s) {
        Object v=s.getAttributes().get("drawGuessRoomId"); if(!(v instanceof String)) throw new IllegalArgumentException("请先加入你画我猜房间"); return (String)v;
    }
    private String playerId(WebSocketSession s) {
        Object v=s.getAttributes().get("drawGuessPlayerId"); if(!(v instanceof String)) throw new IllegalArgumentException("玩家身份无效"); return (String)v;
    }
    private String required(JsonNode b,String f) { String v=text(b,f,null); if(v==null||v.isBlank()) throw new IllegalArgumentException("缺少参数："+f); return v; }
    private String text(JsonNode b,String f,String d) { JsonNode n=b.get(f); return n==null||n.isNull()?d:n.asText(); }
    private void broadcast(DrawGuessRoom r,String t,Object d) throws Exception { for(WebSocketSession s:manager.sessions(r)) send(s,t,d); }
    private void broadcastExcept(DrawGuessRoom r,WebSocketSession x,String t,Object d) throws Exception { for(WebSocketSession s:manager.sessions(r)) if(!s.getId().equals(x.getId())) send(s,t,d); }
    private void send(WebSocketSession s,String t,Object d) throws Exception {
        if(s==null||!s.isOpen()) return;
        Map<String,Object> e=new LinkedHashMap<>(); e.put("type",t); e.put("data",d);
        synchronized(s) { s.sendMessage(new TextMessage(mapper.writeValueAsString(e))); }
    }
}
