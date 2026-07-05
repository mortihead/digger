package org.digger.app;

/**
 * Handles all drawing/rendering: field layout, sprites (digger, monsters, bags, fire, bonus),
 * text output, and tunnel-digging logic.
 *
 * <p>The field is a 15×10 grid stored as bitmasks in {@link #field}.
 * Each cell uses bit flags to track tunnel segments and emerald presence:
 * <ul>
 *   <li>Bits 0-4: horizontal tunnel segments (5 columns per cell)</li>
 *   <li>Bits 6-11: vertical tunnel segments (6 rows per cell)</li>
 *   <li>Bit 13 (0x2000): emerald present</li>
 *   <li>Bit 12 (0x1000): tunnel carved from side/top (drawn already)</li>
 * </ul>
 *
 * <p>Two field snapshots ({@link #field1}, {@link #field2}) support 2-player mode.
 */
class Drawing {

    private static final int FIELD_WIDTH = 15;
    private static final int FIELD_HEIGHT = 10;
    private static final int FIELD_SIZE = FIELD_WIDTH * FIELD_HEIGHT;

    private static final int BIT_EMERALD = 0x2000;
    private static final int BIT_TUNNEL_DRAWN = 0x1000;

    /**
     * Combined bitmask arrays for digTunnel: [0..4]=horizontal, [5..10]=vertical.
     */
    private final int[] bitMasks = {0xfffe, 0xfffd, 0xfffb, 0xfff7, 0xffef,
        0xffdf, 0xffbf, 0xff7f, 0xfeff, 0xfdff, 0xfbff, 0xf7ff};

    private static final int CLEAR_HORIZONTAL = 0xd03f;  // clears bits 0-4 and 12
    private static final int CLEAR_VERTICAL = 0xdfe0;    // clears bits 6-11 and 12
    private static final int ALL_HORIZONTAL = 0x1f;
    private static final int ALL_VERTICAL = 0xfc0;

    private final Digger dig;

    /**
     * Field snapshots for 2-player mode (player 1 and player 2).
     */
    int[] field1 = new int[FIELD_SIZE];
    int[] field2 = new int[FIELD_SIZE];

    /**
     * Current active field bitmask array.
     */
    int[] field = new int[FIELD_SIZE];

    // --- Sprite pixel buffers ---

    private final short[] diggerBuf = new short[480];
    private final short[] bagBuf1 = new short[480], bagBuf2 = new short[480],
        bagBuf3 = new short[480], bagBuf4 = new short[480],
        bagBuf5 = new short[480], bagBuf6 = new short[480],
        bagBuf7 = new short[480];
    private final short[] monBuf1 = new short[480], monBuf2 = new short[480],
        monBuf3 = new short[480], monBuf4 = new short[480],
        monBuf5 = new short[480], monBuf6 = new short[480];
    private final short[] bonusBuf = new short[480];
    private final short[] fireBuf = new short[128];

    // --- Animation state ---

    /**
     * Current animation frame for each monster (0, 1, or 2).
     */
    private final int[] monSpriteFrame = {0, 0, 0, 0, 0, 0};
    /**
     * Animation direction for each monster (+1 or -1).
     */
    private final int[] monSpriteDir = {0, 0, 0, 0, 0, 0};

    private int digSpriteFrame = 0;
    private int digSpriteDir = 0;
    private int fireSpriteFrame = 0;
    private final int fireHeight = 8;

    Drawing(Digger d) {
        dig = d;
    }

    // --- Sprite creation (one-time buffer allocation) ---

    /**
     * Creates sprite buffers for digger, bonus cherry, and fire bolt.
     */
    void createDiggerBonusFireSprites() {
        digSpriteDir = 1;
        digSpriteFrame = 0;
        fireSpriteFrame = 0;
        dig.sprite.createspr(0, 0, diggerBuf, 4, 15, 0, 0);
        dig.sprite.createspr(14, 81, bonusBuf, 4, 15, 0, 0);
        dig.sprite.createspr(15, 82, fireBuf, 2, fireHeight, 0, 0);
    }

    /**
     * Creates all sprite buffers: bags, monsters, digger, bonus, fire.
     */
    void createAllSprites() {
        dig.sprite.createspr(1, 62, bagBuf1, 4, 15, 0, 0);
        dig.sprite.createspr(2, 62, bagBuf2, 4, 15, 0, 0);
        dig.sprite.createspr(3, 62, bagBuf3, 4, 15, 0, 0);
        dig.sprite.createspr(4, 62, bagBuf4, 4, 15, 0, 0);
        dig.sprite.createspr(5, 62, bagBuf5, 4, 15, 0, 0);
        dig.sprite.createspr(6, 62, bagBuf6, 4, 15, 0, 0);
        dig.sprite.createspr(7, 62, bagBuf7, 4, 15, 0, 0);
        dig.sprite.createspr(8, 71, monBuf1, 4, 15, 0, 0);
        dig.sprite.createspr(9, 71, monBuf2, 4, 15, 0, 0);
        dig.sprite.createspr(10, 71, monBuf3, 4, 15, 0, 0);
        dig.sprite.createspr(11, 71, monBuf4, 4, 15, 0, 0);
        dig.sprite.createspr(12, 71, monBuf5, 4, 15, 0, 0);
        dig.sprite.createspr(13, 71, monBuf6, 4, 15, 0, 0);
        createDiggerBonusFireSprites();
        for (int i = 0; i < 6; i++) {
            monSpriteFrame[i] = 0;
            monSpriteDir[i] = 1;
        }
    }

    // --- Background & field drawing ---

    /**
     * Draws the level background dirt tiles.
     */
    void drawBackground(int levelPlan) {
        for (int y = 14; y < 200; y += 4)
            for (int x = 0; x < 320; x += 20)
                dig.sprite.drawmiscspr(x, y, 93 + levelPlan, 5, 4);
    }

    /**
     * Draws the bonus cherry at the given position.
     */
    void drawBonus(int x, int y) {
        dig.sprite.initspr(14, 81, 4, 15, 0, 0);
        dig.sprite.movedrawspr(14, x, y);
    }

    /**
     * Draws the bottom tunnel edge blob (below a cell).
     */
    void drawTunnelEdgeBottom(int x, int y) {
        dig.sprite.initmiscspr(x - 4, y + 15, 6, 6);
        dig.sprite.drawmiscspr(x - 4, y + 15, 105, 6, 6);
        dig.sprite.getis();
    }

    /**
     * Draws the digger sprite at the given position.
     *
     * @param frame animation frame (0-6 for walking, 10-15 for death)
     * @param x     pixel X position
     * @param y     pixel Y position
     * @param right true if facing right
     * @return collision bitmask
     */
    int drawDigger(int frame, int x, int y, boolean right) {
        digSpriteFrame += digSpriteDir;
        if (digSpriteFrame == 2 || digSpriteFrame == 0)
            digSpriteDir = -digSpriteDir;
        digSpriteFrame = Math.max(0, Math.min(2, digSpriteFrame));
        if (frame >= 0 && frame <= 6 && !((frame & 1) != 0)) {
            dig.sprite.initspr(0, (frame + (right ? 0 : 1)) * 3 + digSpriteFrame + 1, 4, 15, 0, 0);
            return dig.sprite.drawspr(0, x, y);
        }
        if (frame >= 10 && frame <= 15) {
            dig.sprite.initspr(0, 40 - frame, 4, 15, 0, 0);
            return dig.sprite.drawspr(0, x, y);
        }
        return 0;
    }

    /**
     * Draws a single emerald at the given pixel position.
     */
    void drawEmerald(int x, int y) {
        dig.sprite.initmiscspr(x, y, 4, 10);
        dig.sprite.drawmiscspr(x, y, 108, 4, 10);
        dig.sprite.getis();
    }

    /**
     * Redraws the tunnel edge blobs for all partially-dug cells.
     * Called after restoring the field to ensure tunnel edges are visible.
     */
    void drawField() {
        int x, y, xp, yp;
        for (x = 0; x < FIELD_WIDTH; x++)
            for (y = 0; y < FIELD_HEIGHT; y++)
                if ((field[y * FIELD_WIDTH + x] & BIT_EMERALD) == 0) {
                    xp = x * 20 + 12;
                    yp = y * 18 + 18;
                    if ((field[y * FIELD_WIDTH + x] & ALL_VERTICAL) != ALL_VERTICAL) {
                        field[y * FIELD_WIDTH + x] &= CLEAR_HORIZONTAL;
                        drawTunnelEdgeBottom(xp, yp - 15);
                        drawTunnelEdgeBottom(xp, yp - 12);
                        drawTunnelEdgeBottom(xp, yp - 9);
                        drawTunnelEdgeBottom(xp, yp - 6);
                        drawTunnelEdgeBottom(xp, yp - 3);
                        drawTunnelEdgeTop(xp, yp + 3);
                    }
                    if ((field[y * FIELD_WIDTH + x] & ALL_HORIZONTAL) != ALL_HORIZONTAL) {
                        field[y * FIELD_WIDTH + x] &= CLEAR_VERTICAL;
                        drawTunnelEdgeRight(xp - 16, yp);
                        drawTunnelEdgeRight(xp - 12, yp);
                        drawTunnelEdgeRight(xp - 8, yp);
                        drawTunnelEdgeRight(xp - 4, yp);
                        drawTunnelEdgeLeft(xp + 4, yp);
                    }
                    if (x < 14)
                        if ((field[y * FIELD_WIDTH + x + 1] & 0xfdf) != 0xfdf)
                            drawTunnelEdgeRight(xp, yp);
                    if (y < 9)
                        if ((field[(y + 1) * FIELD_WIDTH + x] & 0xfdf) != 0xfdf)
                            drawTunnelEdgeBottom(xp, yp);
                }
    }

    /**
     * Draws the fire bolt projectile.
     *
     * @param x     pixel X position
     * @param y     pixel Y position
     * @param frame 0 for animated, 1-2 for expanding tip
     * @return collision bitmask
     */
    int drawFire(int x, int y, int frame) {
        if (frame == 0) {
            fireSpriteFrame++;
            if (fireSpriteFrame > 2)
                fireSpriteFrame = 0;
            dig.sprite.initspr(15, 82 + fireSpriteFrame, 2, fireHeight, 0, 0);
        } else
            dig.sprite.initspr(15, 84 + frame, 2, fireHeight, 0, 0);
        return dig.sprite.drawspr(15, x, y);
    }

    /**
     * Draws falling dirt debris below a bag breaking through ground.
     */
    void drawBagFallDebris(int x, int y) {
        dig.sprite.initmiscspr(x - 4, y + 15, 6, 8);
        dig.sprite.drawmiscspr(x - 4, y + 15, 107, 6, 8);
        dig.sprite.getis();
    }

    /**
     * Draws a gold bag sprite.
     *
     * @param spriteIndex sprite slot (1-7)
     * @param frame       animation frame (0=still, 1-2=wobble, 3=falling, 4-6=breaking)
     * @return collision bitmask
     */
    int drawGold(int spriteIndex, int frame, int x, int y) {
        dig.sprite.initspr(spriteIndex, frame + 62, 4, 15, 0, 0);
        return dig.sprite.drawspr(spriteIndex, x, y);
    }

    /**
     * Draws the left tunnel edge blob (boundary to the right of a cell).
     */
    void drawTunnelEdgeLeft(int x, int y) {
        dig.sprite.initmiscspr(x - 8, y - 1, 2, 18);
        dig.sprite.drawmiscspr(x - 8, y - 1, 104, 2, 18);
        dig.sprite.getis();
    }

    /**
     * Draws a single life indicator icon.
     */
    void drawLife(int type, int x, int y) {
        dig.sprite.drawmiscspr(x, y, type + 110, 4, 12);
    }

    /**
     * Draws remaining lives for both players in the top bar.
     */
    void drawLives() {
        int l, n;
        n = dig.main.getLives(1) - 1;
        for (l = 1; l < 5; l++) {
            drawLife(n > 0 ? 0 : 2, l * 20 + 60, 0);
            n--;
        }
        if (dig.main.numPlayers == 2) {
            n = dig.main.getLives(2) - 1;
            for (l = 1; l < 5; l++) {
                drawLife(n > 0 ? 1 : 2, 244 - l * 20, 0);
                n--;
            }
        }
    }

    /**
     * Draws a live monster (Nobbin or Hobbin) with walking animation.
     *
     * @param index     monster index (0-5)
     * @param isNobbin  true for Nobbin (round), false for Hobbin (digs horizontally)
     * @param direction facing direction (0=right, 4=left)
     * @return collision bitmask
     */
    int drawMonster(int index, boolean isNobbin, int direction, int x, int y) {
        monSpriteFrame[index] += monSpriteDir[index];
        if (monSpriteFrame[index] == 2 || monSpriteFrame[index] == 0)
            monSpriteDir[index] = -monSpriteDir[index];
        monSpriteFrame[index] = Math.max(0, Math.min(2, monSpriteFrame[index]));
        if (isNobbin)
            dig.sprite.initspr(index + 8, monSpriteFrame[index] + 69, 4, 15, 0, 0);
        else
            switch (direction) {
                case 0:
                    dig.sprite.initspr(index + 8, monSpriteFrame[index] + 73, 4, 15, 0, 0);
                    break;
                case 4:
                    dig.sprite.initspr(index + 8, monSpriteFrame[index] + 77, 4, 15, 0, 0);
            }
        return dig.sprite.drawspr(index + 8, x, y);
    }

    /**
     * Draws a dying/squashed monster sprite.
     *
     * @param index     monster index (0-5)
     * @param isNobbin  true for Nobbin, false for Hobbin
     * @param direction facing direction at death
     * @return collision bitmask
     */
    int drawMonsterDeath(int index, boolean isNobbin, int direction, int x, int y) {
        if (isNobbin)
            dig.sprite.initspr(index + 8, 72, 4, 15, 0, 0);
        else
            switch (direction) {
                case 0:
                    dig.sprite.initspr(index + 8, 76, 4, 15, 0, 0);
                    break;
                case 4:
                    dig.sprite.initspr(index + 8, 80, 4, 14, 0, 0);
            }
        return dig.sprite.drawspr(index + 8, x, y);
    }

    /**
     * Draws the right tunnel edge blob (boundary to the left of a cell).
     */
    void drawTunnelEdgeRight(int x, int y) {
        dig.sprite.initmiscspr(x + 16, y - 1, 2, 18);
        dig.sprite.drawmiscspr(x + 16, y - 1, 102, 2, 18);
        dig.sprite.getis();
    }

    /**
     * Draws a ground crack sprite below a wobbling bag about to fall.
     */
    void drawBagGroundCrack(int x, int y) {
        dig.sprite.initmiscspr(x - 4, y + 17, 6, 6);
        dig.sprite.drawmiscspr(x - 4, y + 17, 106, 6, 6);
        dig.sprite.getis();
    }

    /**
     * Restores field from snapshot and redraws background and tunnel edges.
     */
    void drawFieldAndBackground() {
        int x, y;
        for (x = 0; x < FIELD_WIDTH; x++)
            for (y = 0; y < FIELD_HEIGHT; y++)
                if (dig.main.getCurrentPlayer() == 0)
                    field[y * FIELD_WIDTH + x] = field1[y * FIELD_WIDTH + x];
                else
                    field[y * FIELD_WIDTH + x] = field2[y * FIELD_WIDTH + x];
        dig.sprite.setretr(true);
        dig.display.setPalette(0);
        dig.display.setIntensity(0);
        drawBackground(dig.main.getLevelPlan());
        drawField();
        dig.display.currentSource.newPixels(0, 0, dig.display.width, dig.display.height);
    }

    /**
     * Draws the top tunnel edge blob (above a cell).
     */
    void drawTunnelEdgeTop(int x, int y) {
        dig.sprite.initmiscspr(x - 4, y - 6, 6, 6);
        dig.sprite.drawmiscspr(x - 4, y - 6, 103, 6, 6);
        dig.sprite.getis();
    }

    /**
     * Clears field bits in the direction of movement (tunnel digging).
     * Also clears the "tunnel drawn" bit when a sub-cell becomes fully dug.
     *
     * @param x   pixel X position of the moving entity
     * @param y   pixel Y position of the moving entity
     * @param dir movement direction (0=right, 4=left, 2=up, 6=down)
     */
    void digTunnel(int x, int y, int dir) {
        int h = (x - 12) / 20, xr = ((x - 12) % 20) / 4;
        int v = (y - 18) / 18, yr = ((y - 18) % 18) / 3;
        dig.main.incrementPenalty();
        switch (dir) {
            case 0:
                h++;
                field[v * FIELD_WIDTH + h] &= bitMasks[xr];
                if ((field[v * FIELD_WIDTH + h] & ALL_HORIZONTAL) != 0)
                    break;
                field[v * FIELD_WIDTH + h] &= ~BIT_TUNNEL_DRAWN;
                break;
            case 4:
                xr--;
                if (xr < 0) {
                    xr += 5;
                    h--;
                }
                field[v * FIELD_WIDTH + h] &= bitMasks[xr];
                if ((field[v * FIELD_WIDTH + h] & ALL_HORIZONTAL) != 0)
                    break;
                field[v * FIELD_WIDTH + h] &= ~BIT_TUNNEL_DRAWN;
                break;
            case 2:
                yr--;
                if (yr < 0) {
                    yr += 6;
                    v--;
                }
                field[v * FIELD_WIDTH + h] &= bitMasks[6 + yr];
                if ((field[v * FIELD_WIDTH + h] & ALL_VERTICAL) != 0)
                    break;
                field[v * FIELD_WIDTH + h] &= ~BIT_TUNNEL_DRAWN;
                break;
            case 6:
                v++;
                field[v * FIELD_WIDTH + h] &= bitMasks[6 + yr];
                if ((field[v * FIELD_WIDTH + h] & ALL_VERTICAL) != 0)
                    break;
                field[v * FIELD_WIDTH + h] &= ~BIT_TUNNEL_DRAWN;
        }
    }

    /**
     * Erases an emerald from the field (draws background over it).
     */
    void eraseEmerald(int x, int y) {
        dig.sprite.initmiscspr(x, y, 4, 10);
        dig.sprite.drawmiscspr(x, y, 109, 4, 10);
        dig.sprite.getis();
    }

    /**
     * Re-initializes digger, bonus, and fire sprites (without recreating buffers).
     */
    void initDiggerBonusFireSprites() {
        digSpriteDir = 1;
        digSpriteFrame = 0;
        fireSpriteFrame = 0;
        dig.sprite.initspr(0, 0, 4, 15, 0, 0);
        dig.sprite.initspr(14, 81, 4, 15, 0, 0);
        dig.sprite.initspr(15, 82, 2, fireHeight, 0, 0);
    }

    /**
     * Re-initializes all sprites (bags, monsters, digger, bonus, fire).
     */
    void initAllSprites() {
        dig.sprite.initspr(1, 62, 4, 15, 0, 0);
        dig.sprite.initspr(2, 62, 4, 15, 0, 0);
        dig.sprite.initspr(3, 62, 4, 15, 0, 0);
        dig.sprite.initspr(4, 62, 4, 15, 0, 0);
        dig.sprite.initspr(5, 62, 4, 15, 0, 0);
        dig.sprite.initspr(6, 62, 4, 15, 0, 0);
        dig.sprite.initspr(7, 62, 4, 15, 0, 0);
        dig.sprite.initspr(8, 71, 4, 15, 0, 0);
        dig.sprite.initspr(9, 71, 4, 15, 0, 0);
        dig.sprite.initspr(10, 71, 4, 15, 0, 0);
        dig.sprite.initspr(11, 71, 4, 15, 0, 0);
        dig.sprite.initspr(12, 71, 4, 15, 0, 0);
        dig.sprite.initspr(13, 71, 4, 15, 0, 0);
        initDiggerBonusFireSprites();
    }

    /**
     * Builds the field bitmask array from the level layout data.
     */
    void buildField() {
        int c, x, y;
        for (x = 0; x < FIELD_WIDTH; x++)
            for (y = 0; y < FIELD_HEIGHT; y++) {
                field[y * FIELD_WIDTH + x] = -1;
                c = dig.main.getLevelChar(x, y, dig.main.getLevelPlan());
                if (c == 'S' || c == 'V')
                    field[y * FIELD_WIDTH + x] &= CLEAR_HORIZONTAL;
                if (c == 'S' || c == 'H')
                    field[y * FIELD_WIDTH + x] &= CLEAR_VERTICAL;
                if (dig.main.getCurrentPlayer() == 0)
                    field1[y * FIELD_WIDTH + x] = field[y * FIELD_WIDTH + x];
                else
                    field2[y * FIELD_WIDTH + x] = field[y * FIELD_WIDTH + x];
            }
    }

    /**
     * Renders text at the given position.
     */
    void drawText(String text, int x, int y, int color) {
        drawText(text, x, y, color, false);
    }

    /**
     * Renders text at the given position.
     *
     * @param refresh if true, immediately refreshes the affected screen region
     */
    void drawText(String text, int x, int y, int color, boolean refresh) {
        int rx = x;
        for (int i = 0; i < text.length(); i++) {
            dig.display.drawChar(x, y, text.charAt(i), color);
            x += 12;
        }
        if (refresh)
            dig.display.currentSource.newPixels(rx, y, text.length() * 12, 12);
    }

    /**
     * Saves the current field state to the active player's snapshot.
     */
    void saveFieldSnapshot() {
        int x, y;
        for (x = 0; x < FIELD_WIDTH; x++)
            for (y = 0; y < FIELD_HEIGHT; y++)
                if (dig.main.getCurrentPlayer() == 0)
                    field1[y * FIELD_WIDTH + x] = field[y * FIELD_WIDTH + x];
                else
                    field2[y * FIELD_WIDTH + x] = field[y * FIELD_WIDTH + x];
    }
}
