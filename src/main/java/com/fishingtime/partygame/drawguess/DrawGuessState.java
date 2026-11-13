package com.fishingtime.partygame.drawguess;

/**
 * Server-authoritative lifecycle states for Draw & Guess.
 */
public enum DrawGuessState {
    WAITING,
    SELECTING,
    DRAWING,
    ARTIST_RECONNECTING,
    ROUND_RESULT,
    GAME_OVER
}
