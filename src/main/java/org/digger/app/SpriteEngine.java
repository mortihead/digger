package org.digger.app;

/**
 * Sprite engine: manages up to 16 sprites with collision detection,
 * save/restore background, and draw ordering.
 *
 * <p>Sprites 0-7 are bags, 8-13 are monsters, 14 is bonus, 15 is fire,
 * 0 is also the digger. Slot 16 is used as a temporary for misc sprites.
 */
class SpriteEngine {

    Digger dig;

    boolean[] sprDrawFlag = new boolean[17];
    boolean[] sprRecFlag = new boolean[17];
    boolean[] sprEnabled = new boolean[16];

    int[] sprChar = new int[17];
    short[][] sprBackground = new short[16][];
    int[] sprX = new int[17];
    int[] sprY = new int[17];
    int[] sprWidth = new int[17];
    int[] sprHeight = new int[17];
    int[] sprBWidth = new int[16];
    int[] sprBHeight = new int[16];
    int[] sprNewChar = new int[16];
    int[] sprNewWidth = new int[16];
    int[] sprNewHeight = new int[16];
    int[] sprNewBWidth = new int[16];
    int[] sprNewBHeight = new int[16];

    int[] defaultSprOrder = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    int[] sprOrder = defaultSprOrder;

    SpriteEngine(Digger d) {
        dig = d;
    }

    boolean bcollide(int bx, int si) {
        if (sprX[bx] >= sprX[si]) {
            if (sprX[bx] + sprBWidth[bx] > sprWidth[si] * 4 + sprX[si] - sprBWidth[si] - 1)
                return false;
        } else if (sprX[si] + sprBWidth[si] > sprWidth[bx] * 4 + sprX[bx] - sprBWidth[bx] - 1)
            return false;
        if (sprY[bx] >= sprY[si]) {
            if (sprY[bx] + sprBHeight[bx] <= sprHeight[si] + sprY[si] - sprBHeight[si] - 1)
                return true;
            return false;
        }
        if (sprY[si] + sprBHeight[si] <= sprHeight[bx] + sprY[bx] - sprBHeight[bx] - 1)
            return true;
        return false;
    }

    int bcollides(int bx) {
        int si = bx, ax = 0, dx = 0;
        bx = 0;
        do {
            if (sprEnabled[bx] && bx != si) {
                if (bcollide(bx, si))
                    ax |= 1 << dx;
                sprX[bx] += 320;
                sprY[bx] -= 2;
                if (bcollide(bx, si))
                    ax |= 1 << dx;
                sprX[bx] -= 640;
                sprY[bx] += 4;
                if (bcollide(bx, si))
                    ax |= 1 << dx;
                sprX[bx] += 320;
                sprY[bx] -= 2;
            }
            bx++;
            dx++;
        } while (dx != 16);
        return ax;
    }

    void clearDrawFlags() {
        clearRecFlags();
        for (int i = 0; i < 17; i++)
            sprDrawFlag[i] = false;
    }

    void clearRecFlags() {
        for (int i = 0; i < 17; i++)
            sprRecFlag[i] = false;
    }

    boolean collide(int bx, int si) {
        if (sprX[bx] >= sprX[si]) {
            if (sprX[bx] > sprWidth[si] * 4 + sprX[si] - 1)
                return false;
        } else if (sprX[si] > sprWidth[bx] * 4 + sprX[bx] - 1)
            return false;
        if (sprY[bx] >= sprY[si]) {
            if (sprY[bx] <= sprHeight[si] + sprY[si] - 1)
                return true;
            return false;
        }
        if (sprY[si] <= sprHeight[bx] + sprY[bx] - 1)
            return true;
        return false;
    }

    void createSprite(int n, int ch, short[] mov, int wid, int hei, int bwid, int bhei) {
        sprNewChar[n & 15] = sprChar[n & 15] = ch;
        sprBackground[n & 15] = mov;
        sprNewWidth[n & 15] = sprWidth[n & 15] = wid;
        sprNewHeight[n & 15] = sprHeight[n & 15] = hei;
        sprNewBWidth[n & 15] = sprBWidth[n & 15] = bwid;
        sprNewBHeight[n & 15] = sprBHeight[n & 15] = bhei;
        sprEnabled[n & 15] = false;
    }

    void drawMiscSprite(int x, int y, int ch, int wid, int hei) {
        sprX[16] = x & -4;
        sprY[16] = y;
        sprChar[16] = ch;
        sprWidth[16] = wid;
        sprHeight[16] = hei;
        dig.display.drawSpriteMasked(sprX[16], sprY[16], sprChar[16], sprWidth[16], sprHeight[16]);
    }

    int redrawSprite(int n, int x, int y) {
        int bx, t1, t2, t3, t4;
        bx = n & 15;
        x &= -4;
        clearDrawFlags();
        setRedrawFlags(bx);
        t1 = sprX[bx];
        t2 = sprY[bx];
        t3 = sprWidth[bx];
        t4 = sprHeight[bx];
        sprX[bx] = x;
        sprY[bx] = y;
        sprWidth[bx] = sprNewWidth[bx];
        sprHeight[bx] = sprNewHeight[bx];
        clearRecFlags();
        setRedrawFlags(bx);
        sprHeight[bx] = t4;
        sprWidth[bx] = t3;
        sprY[bx] = t2;
        sprX[bx] = t1;
        sprDrawFlag[bx] = true;
        restoreBackgrounds();
        sprX[bx] = x;
        sprY[bx] = y;
        sprChar[bx] = sprNewChar[bx];
        sprWidth[bx] = sprNewWidth[bx];
        sprHeight[bx] = sprNewHeight[bx];
        sprBWidth[bx] = sprNewBWidth[bx];
        sprBHeight[bx] = sprNewBHeight[bx];
        dig.display.readSpritePixels(sprX[bx], sprY[bx], sprBackground[bx], sprWidth[bx], sprHeight[bx]);
        drawMaskedSprites();
        return bcollides(bx);
    }

    void eraseSprite(int n) {
        int bx = n & 15;
        dig.display.drawSprite(sprX[bx], sprY[bx], sprBackground[bx], sprWidth[bx], sprHeight[bx]);
        sprEnabled[bx] = false;
        clearDrawFlags();
        setRedrawFlags(bx);
        drawMaskedSprites();
    }

    void captureAndDrawSprites() {
        for (int i = 0; i < 16; i++)
            if (sprDrawFlag[i])
                dig.display.readSpritePixels(sprX[i], sprY[i], sprBackground[i], sprWidth[i], sprHeight[i]);
        drawMaskedSprites();
    }

    void initMiscSprite(int x, int y, int wid, int hei) {
        sprX[16] = x;
        sprY[16] = y;
        sprWidth[16] = wid;
        sprHeight[16] = hei;
        clearDrawFlags();
        setRedrawFlags(16);
        restoreBackgrounds();
    }

    void initSprite(int n, int ch, int wid, int hei, int bwid, int bhei) {
        sprNewChar[n & 15] = ch;
        sprNewWidth[n & 15] = wid;
        sprNewHeight[n & 15] = hei;
        sprNewBWidth[n & 15] = bwid;
        sprNewBHeight[n & 15] = bhei;
    }

    int moveDrawSprite(int n, int x, int y) {
        int bx = n & 15;
        sprX[bx] = x & -4;
        sprY[bx] = y;
        sprChar[bx] = sprNewChar[bx];
        sprWidth[bx] = sprNewWidth[bx];
        sprHeight[bx] = sprNewHeight[bx];
        sprBWidth[bx] = sprNewBWidth[bx];
        sprBHeight[bx] = sprNewBHeight[bx];
        clearDrawFlags();
        setRedrawFlags(bx);
        restoreBackgrounds();
        dig.display.readSpritePixels(sprX[bx], sprY[bx], sprBackground[bx], sprWidth[bx], sprHeight[bx]);
        sprEnabled[bx] = true;
        sprDrawFlag[bx] = true;
        drawMaskedSprites();
        return bcollides(bx);
    }

    /** Draws all flagged sprites using their masks (transparent backgrounds). */
    private void drawMaskedSprites() {
        for (int i = 0; i < 16; i++) {
            int j = sprOrder[i];
            if (sprDrawFlag[j])
                dig.display.drawSpriteMasked(sprX[j], sprY[j], sprChar[j], sprWidth[j], sprHeight[j]);
        }
    }

    /** Restores background for all flagged sprites (non-masked overwrite). */
    private void restoreBackgrounds() {
        for (int i = 0; i < 16; i++)
            if (sprDrawFlag[i])
                dig.display.drawSprite(sprX[i], sprY[i], sprBackground[i], sprWidth[i], sprHeight[i]);
    }

    void setRedrawFlags(int n) {
        if (!sprRecFlag[n]) {
            sprRecFlag[n] = true;
            for (int i = 0; i < 16; i++)
                if (sprEnabled[i] && i != n) {
                    if (collide(i, n)) {
                        sprDrawFlag[i] = true;
                        setRedrawFlags(i);
                    }
                    sprX[i] += 320;
                    sprY[i] -= 2;
                    if (collide(i, n)) {
                        sprDrawFlag[i] = true;
                        setRedrawFlags(i);
                    }
                    sprX[i] -= 640;
                    sprY[i] += 4;
                    if (collide(i, n)) {
                        sprDrawFlag[i] = true;
                        setRedrawFlags(i);
                    }
                    sprX[i] += 320;
                    sprY[i] -= 2;
                }
        }
    }


    void setSpriteOrder(int[] newsprorder) {
        if (newsprorder == null)
            sprOrder = defaultSprOrder;
        else
            sprOrder = newsprorder;
    }
}
