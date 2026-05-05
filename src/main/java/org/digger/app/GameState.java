package org.digger.app;

/**
 * Mutable state data for a single game (per player).
 *
 * @see Main
 */
class GameState {
    int lives;          // remaining lives
    int level;          // current level number (1-based)
    boolean dead;       // player died this round
    boolean levelDone;  // current level is completed
}
