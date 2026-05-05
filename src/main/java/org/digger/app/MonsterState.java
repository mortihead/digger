package org.digger.app;

/**
 * Mutable state data for a single monster entity.
 *
 * @see Monster
 */
class MonsterState {
    int x, y;           // pixel position
    int h, v;           // cell position
    int xr, yr;         // sub-pixel remainder within cell
    int direction;      // current movement direction
    int horizontalDir;  // preferred horizontal direction
    int type;           // monster behavior timer / type indicator
    int huntTime;       // chase mode timer
    int death;          // death animation stage (0 = alive)
    int bag;            // bag that killed this monster
    int deathTime;      // frames since death
    int spawnTime;      // spawn delay counter
    boolean flag;       // general-purpose flag (spawned/active)
    boolean nob;        // cannot change direction this frame
    boolean alive;      // monster is alive and visible
}
