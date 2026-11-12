package com.fishingtime.partygame.config;

import com.fishingtime.partygame.room.RoomManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PartyGameCleanup {
    private final RoomManager rooms;
    public PartyGameCleanup(RoomManager rooms) { this.rooms = rooms; }

    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredRooms() { rooms.cleanup(); }
}
