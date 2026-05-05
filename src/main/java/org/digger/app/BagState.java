package org.digger.app;

/**
 * Mutable state data for a single gold bag on the game field.
 *
 * @see Bags
 */
class BagState {
    int x, y;           // pixel position
    int h, v;           // cell position (horizontal/vertical)
    int xr, yr;         // sub-pixel remainder within cell
    int direction;      // movement direction (0=right, 4=left, etc.)
    int wobbleTime;     // frames remaining in wobble state
    int goldTime;       // time counter for gold pickup
    int fallHeight;     // how many cells the bag has fallen
    boolean wobbling;   // bag is wobbling before falling
    boolean unfallen;   // bag hasn't fallen yet (sits on ground)
    boolean exist;      // bag is active on the field

    void copyFrom(BagState other) {
        x = other.x;
        y = other.y;
        h = other.h;
        v = other.v;
        xr = other.xr;
        yr = other.yr;
        direction = other.direction;
        wobbleTime = other.wobbleTime;
        goldTime = other.goldTime;
        fallHeight = other.fallHeight;
        wobbling = other.wobbling;
        unfallen = other.unfallen;
        exist = other.exist;
    }
}
