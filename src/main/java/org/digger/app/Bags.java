package org.digger.app;

/**
 * Manages gold bags: placement, falling, wobbling, pushing, and gold collection.
 * Up to 7 bags can exist simultaneously (indices 1-7).
 */
class Bags {

    private static final int MAX_BAGS = 8;      // array size (index 0 unused)
    private static final int GOLD_BASE_TIME = 150;

    private final Digger dig;

    /** Bag state snapshots for 2-player mode (player 1 and player 2). */
    BagState[] bagdat1 = new BagState[MAX_BAGS],
            bagdat2 = new BagState[MAX_BAGS],
            bagdat = new BagState[MAX_BAGS];

    int pushCount = 0;
    int goldTime = 0;    // frames before gold disappears after bag breaks

    /** Wobble animation frame indices: still, left, still, right. */
    private final int[] wobbleAnim = {2, 0, 1, 0};

    Bags(Digger d) {
        dig = d;
        for (int i = 0; i < MAX_BAGS; i++) {
            bagdat[i] = new BagState();
            bagdat1[i] = new BagState();
            bagdat2[i] = new BagState();
        }
    }

    /** Returns a bitmask of which bags exist (bits 1-7). */
    int bagbits() {
        int bags = 0;
        for (int bag = 1, bit = 2; bag < MAX_BAGS; bag++, bit <<= 1)
            if (bagdat[bag].exist)
                bags |= bit;
        return bags;
    }

    /** Called when a falling bag hits the ground. */
    void baghitground(int bag) {
        int clbits;
        if (bagdat[bag].direction == 6 && bagdat[bag].fallHeight > 1)
            bagdat[bag].goldTime = 1;
        else
            bagdat[bag].fallHeight = 0;
        bagdat[bag].direction = -1;
        bagdat[bag].wobbleTime = 15;
        bagdat[bag].wobbling = false;
        clbits = dig.drawing.drawGold(bag, 0, bagdat[bag].x, bagdat[bag].y);
        dig.main.incrementPenalty();
        for (int bn = 1, b = 2; bn < MAX_BAGS; bn++, b <<= 1)
            if ((b & clbits) != 0)
                removebag(bn);
    }

    int bagy(int bag) {
        return bagdat[bag].y;
    }

    /** Saves current bag state for the current player and removes stray bags. */
    void cleanupBags() {
        dig.sound.soundFallOff();
        for (int bpa = 1; bpa < MAX_BAGS; bpa++) {
            if (bagdat[bpa].exist && ((bagdat[bpa].h == 7 && bagdat[bpa].v == 9) ||
                    bagdat[bpa].xr != 0 || bagdat[bpa].yr != 0 || bagdat[bpa].goldTime != 0 ||
                    bagdat[bpa].fallHeight != 0 || bagdat[bpa].wobbling)) {
                bagdat[bpa].exist = false;
                dig.sprite.eraseSprite(bpa);
            }
            if (dig.main.getCurrentPlayer() == 0)
                bagdat1[bpa].copyFrom(bagdat[bpa]);
            else
                bagdat2[bpa].copyFrom(bagdat[bpa]);
        }
    }

    /** Main per-frame update for all bags. */
    void doBags() {
        int bag;
        boolean soundFallOff = true, soundWobbleOff = true;
        for (bag = 1; bag < MAX_BAGS; bag++)
            if (bagdat[bag].exist) {
                if (bagdat[bag].goldTime != 0) {
                    if (bagdat[bag].goldTime == 1) {
                        dig.sound.soundBreak();
                        dig.drawing.drawGold(bag, 4, bagdat[bag].x, bagdat[bag].y);
                        dig.main.incrementPenalty();
                    }
                    if (bagdat[bag].goldTime == 3) {
                        dig.drawing.drawGold(bag, 5, bagdat[bag].x, bagdat[bag].y);
                        dig.main.incrementPenalty();
                    }
                    if (bagdat[bag].goldTime == 5) {
                        dig.drawing.drawGold(bag, 6, bagdat[bag].x, bagdat[bag].y);
                        dig.main.incrementPenalty();
                    }
                    bagdat[bag].goldTime++;
                    if (bagdat[bag].goldTime == goldTime)
                        removebag(bag);
                    else if (bagdat[bag].v < 9 && bagdat[bag].goldTime < goldTime - 10)
                        if ((dig.monster.getfield(bagdat[bag].h, bagdat[bag].v + 1) & 0x2000) == 0)
                            bagdat[bag].goldTime = goldTime - 10;
                } else
                    updatebag(bag);
            }
        for (bag = 1; bag < MAX_BAGS; bag++) {
            if (bagdat[bag].direction == 6 && bagdat[bag].exist)
                soundFallOff = false;
            if (bagdat[bag].direction != 6 && bagdat[bag].wobbling && bagdat[bag].exist)
                soundWobbleOff = false;
        }
        if (soundFallOff)
            dig.sound.soundFallOff();
        if (soundWobbleOff)
            dig.sound.soundWobbleOff();
    }

    /** Restores bag state from the current player's snapshot and draws them. */
    void drawBags() {
        for (int bag = 1; bag < MAX_BAGS; bag++) {
            if (dig.main.getCurrentPlayer() == 0)
                bagdat[bag].copyFrom(bagdat1[bag]);
            else
                bagdat[bag].copyFrom(bagdat2[bag]);
            if (bagdat[bag].exist)
                dig.sprite.moveDrawSprite(bag, bagdat[bag].x, bagdat[bag].y);
        }
    }

    int getbagdir(int bag) {
        if (bagdat[bag].exist)
            return bagdat[bag].direction;
        return -1;
    }

    /** Collects gold from a broken bag. */
    void getgold(int bag) {
        int clbits = dig.drawing.drawGold(bag, 6, bagdat[bag].x, bagdat[bag].y);
        dig.main.incrementPenalty();
        if ((clbits & 1) != 0) {
            dig.scores.scoreGold();
            dig.sound.soundGold();
            dig.digtime = 0;
        } else
            dig.monster.monsterGotGold();
        removebag(bag);
    }

    /** Counts bags that are currently moving (falling or wobbling). */
    int getMovingBagsCount() {
        int n = 0;
        for (int bag = 1; bag < MAX_BAGS; bag++)
            if (bagdat[bag].exist && bagdat[bag].goldTime < 10 &&
                    (bagdat[bag].goldTime != 0 || bagdat[bag].wobbling))
                n++;
        return n;
    }

    /** Places bags on the field according to the level plan. */
    void initBags() {
        pushCount = 0;
        goldTime = GOLD_BASE_TIME - dig.main.getLevelNumberClampedToTen() * 10;
        for (int bag = 1; bag < MAX_BAGS; bag++)
            bagdat[bag].exist = false;
        int bag = 1;
        for (int x = 0; x < 15; x++)
            for (int y = 0; y < 10; y++)
                if (dig.main.getLevelChar(x, y, dig.main.getLevelPlan()) == 'B')
                    if (bag < MAX_BAGS) {
                        bagdat[bag].exist = true;
                        bagdat[bag].goldTime = 0;
                        bagdat[bag].fallHeight = 0;
                        bagdat[bag].direction = -1;
                        bagdat[bag].wobbling = false;
                        bagdat[bag].wobbleTime = 15;
                        bagdat[bag].unfallen = true;
                        bagdat[bag].x = x * 20 + 12;
                        bagdat[bag].y = y * 18 + 18;
                        bagdat[bag].h = x;
                        bagdat[bag].v = y;
                        bagdat[bag].xr = 0;
                        bagdat[bag].yr = 0;
                        bag++;
                    }
        if (dig.main.getCurrentPlayer() == 0)
            for (int i = 1; i < MAX_BAGS; i++)
                bagdat1[i].copyFrom(bagdat[i]);
        else
            for (int i = 1; i < MAX_BAGS; i++)
                bagdat2[i].copyFrom(bagdat[i]);
    }

    /** Attempts to push a bag in the given direction. Returns true if push succeeded. */
    boolean pushbag(int bag, int dir) {
        int x, y, h, v, ox, oy, clbits;
        boolean push = true;
        ox = x = bagdat[bag].x;
        oy = y = bagdat[bag].y;
        h = bagdat[bag].h;
        v = bagdat[bag].v;
        if (bagdat[bag].goldTime != 0) {
            getgold(bag);
            return true;
        }
        if (bagdat[bag].direction == 6 && (dir == 4 || dir == 0)) {
            clbits = dig.drawing.drawGold(bag, 3, x, y);
            dig.main.incrementPenalty();
            if (((clbits & 1) != 0) && (dig.diggery >= y))
                dig.killdigger(1, bag);
            if ((clbits & 0x3f00) != 0)
                dig.monster.squashmonsters(bag, clbits);
            return true;
        }
        if ((x == 292 && dir == 0) || (x == 12 && dir == 4) || (y == 180 && dir == 6) ||
                (y == 18 && dir == 2))
            push = false;
        if (push) {
            switch (dir) {
                case 0:
                    x += 4;
                    break;
                case 4:
                    x -= 4;
                    break;
                case 6:
                    if (bagdat[bag].unfallen) {
                        bagdat[bag].unfallen = false;
dig.drawing.drawBagGroundCrack(x, y);
dig.drawing.drawTunnelEdgeTop(x, y + 21);
                    } else
dig.drawing.drawBagFallDebris(x, y);
dig.drawing.digTunnel(x, y, dir);
                    dig.killemerald(h, v);
                    y += 6;
            }
            switch (dir) {
                case 6:
                    clbits = dig.drawing.drawGold(bag, 3, x, y);
                    dig.main.incrementPenalty();
                    if (((clbits & 1) != 0) && dig.diggery >= y)
                        dig.killdigger(1, bag);
                    if ((clbits & 0x3f00) != 0)
                        dig.monster.squashmonsters(bag, clbits);
                    break;
                case 0:
                case 4:
                    bagdat[bag].wobbleTime = 15;
                    bagdat[bag].wobbling = false;
                    clbits = dig.drawing.drawGold(bag, 0, x, y);
                    dig.main.incrementPenalty();
                    pushCount = 1;
                    if ((clbits & 0xfe) != 0)
                        if (!pushbags(dir, clbits)) {
                            x = ox;
                            y = oy;
                            dig.drawing.drawGold(bag, 0, ox, oy);
                            dig.main.incrementPenalty();
                            push = false;
                        }
                    if (((clbits & 1) != 0) || ((clbits & 0x3f00) != 0)) {
                        x = ox;
                        y = oy;
                        dig.drawing.drawGold(bag, 0, ox, oy);
                        dig.main.incrementPenalty();
                        push = false;
                    }
            }
            if (push)
                bagdat[bag].direction = dir;
            else
                bagdat[bag].direction = dig.reversedir(dir);
            bagdat[bag].x = x;
            bagdat[bag].y = y;
            bagdat[bag].h = (x - 12) / 20;
            bagdat[bag].v = (y - 18) / 18;
            bagdat[bag].xr = (x - 12) % 20;
            bagdat[bag].yr = (y - 18) % 18;
        }
        return push;
    }

    /** Pushes all bags that collide with the given bitmask. */
    boolean pushbags(int dir, int bits) {
        boolean push = true;
        for (int bag = 1, bit = 2; bag < MAX_BAGS; bag++, bit <<= 1)
            if ((bits & bit) != 0)
                if (!pushbag(bag, dir))
                    push = false;
        return push;
    }

    /** Checks if vertical push is possible (bags with gold auto-collect). */
    boolean pushudbags(int bits) {
        boolean push = true;
        for (int bag = 1, b = 2; bag < MAX_BAGS; bag++, b <<= 1)
            if ((bits & b) != 0)
                if (bagdat[bag].goldTime != 0)
                    getgold(bag);
                else
                    push = false;
        return push;
    }

    void removebag(int bag) {
        if (bagdat[bag].exist) {
            bagdat[bag].exist = false;
            dig.sprite.eraseSprite(bag);
        }
    }

    void removebags(int bits) {
        for (int bag = 1, b = 2; bag < MAX_BAGS; bag++, b <<= 1)
            if ((bagdat[bag].exist) && ((bits & b) != 0))
                removebag(bag);
    }

    /** Per-frame update for a single bag: handles wobbling and falling transitions. */
    void updatebag(int bag) {
        int x, h, xr, y, v, yr, wbl;
        x = bagdat[bag].x;
        h = bagdat[bag].h;
        xr = bagdat[bag].xr;
        y = bagdat[bag].y;
        v = bagdat[bag].v;
        yr = bagdat[bag].yr;
        switch (bagdat[bag].direction) {
            case -1:
                if (y < 180 && xr == 0) {
                    if (bagdat[bag].wobbling) {
                        if (bagdat[bag].wobbleTime == 0) {
                            bagdat[bag].direction = 6;
                            dig.sound.soundFall();
                            break;
                        }
                        bagdat[bag].wobbleTime--;
                        wbl = bagdat[bag].wobbleTime % 8;
                        if (!((wbl & 1) != 0)) {
                            dig.drawing.drawGold(bag, wobbleAnim[wbl >> 1], x, y);
                            dig.main.incrementPenalty();
                            dig.sound.soundWobble();
                        }
                    } else if ((dig.monster.getfield(h, v + 1) & 0xfdf) != 0xfdf)
                        if (!dig.isDiggerUnderBag(h, v + 1))
                            bagdat[bag].wobbling = true;
                } else {
                    bagdat[bag].wobbleTime = 15;
                    bagdat[bag].wobbling = false;
                }
                break;
            case 0:
            case 4:
                if (xr == 0)
                    if (y < 180 && (dig.monster.getfield(h, v + 1) & 0xfdf) != 0xfdf) {
                        bagdat[bag].direction = 6;
                        bagdat[bag].wobbleTime = 0;
                        dig.sound.soundFall();
                    } else
                        baghitground(bag);
                break;
            case 6:
                if (yr == 0)
                    bagdat[bag].fallHeight++;
                if (y >= 180)
                    baghitground(bag);
                else if ((dig.monster.getfield(h, v + 1) & 0xfdf) == 0xfdf)
                    if (yr == 0)
                        baghitground(bag);
                dig.monster.checkMonScared(bagdat[bag].h);
        }
        if (bagdat[bag].direction != -1)
            if (bagdat[bag].direction != 6 && pushCount != 0)
                pushCount--;
            else
                pushbag(bag, bagdat[bag].direction);
    }
}
