package org.digger.app;

/**
 * Mutable state data for a single gold bag in the game field.
 */
class BagState {
    int x, y, h, v, xr, yr, dir, wt, gt, fallh;
    boolean wobbling, unfallen, exist;

    void copyFrom(BagState t) {
        x = t.x;
        y = t.y;
        h = t.h;
        v = t.v;
        xr = t.xr;
        yr = t.yr;
        dir = t.dir;
        wt = t.wt;
        gt = t.gt;
        fallh = t.fallh;
        wobbling = t.wobbling;
        unfallen = t.unfallen;
        exist = t.exist;
    }
}
