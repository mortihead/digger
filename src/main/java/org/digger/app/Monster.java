package org.digger.app;

/**
 * Manages monster entities: spawning, AI movement, collisions, and death.
 * Up to 6 monsters can exist simultaneously (indices 0-5).
 * Monster types: Nobbin (round, can't dig) and Hobbin (can dig through dirt).
 */
class Monster {

    private static final int MAX_MONSTERS = 6;

    private final Digger dig;

    MonsterState[] mondat = new MonsterState[MAX_MONSTERS];

    int nextMonster = 0;       // next monster index to spawn
    int totalMonsters = 0;     // total monsters for this level
    int maxMonOnScreen = 0;    // max simultaneous monsters on screen
    int nextMonTime = 0;       // spawn countdown
    int monGapTime = 0;        // frames between spawns

    boolean unbonusFlag = false;   // spawn bonus after all monsters
    boolean monGotGold = false;    // monster ate gold this frame

    Monster(Digger d) {
        dig = d;
        for (int i = 0; i < MAX_MONSTERS; i++)
            mondat[i] = new MonsterState();
    }

    /** Prevents two overlapping monsters from moving in the same direction. */
    void checkcoincide(int mon, int bits) {
        for (int m = 0, b = 256; m < MAX_MONSTERS; m++, b <<= 1)
            if (((bits & b) != 0) && (mondat[mon].direction == mondat[m].direction) &&
                    (mondat[m].spawnTime == 0) && (mondat[mon].spawnTime == 0))
                mondat[m].direction = dig.reversedir(mondat[m].direction);
    }

    /** Makes downward-moving monsters reverse when a bag falls near them. */
    void checkMonScared(int h) {
        for (int m = 0; m < MAX_MONSTERS; m++)
            if ((h == mondat[m].h) && (mondat[m].direction == 2))
                mondat[m].direction = 6;
    }

    /** Spawns a new monster at the top-right corner. */
    void createMonster() {
        for (int i = 0; i < MAX_MONSTERS; i++)
            if (!mondat[i].flag) {
                mondat[i].flag = true;
                mondat[i].alive = true;
                mondat[i].type = 0;
                mondat[i].nob = true;
                mondat[i].huntTime = 0;
                mondat[i].h = 14;
                mondat[i].v = 0;
                mondat[i].x = 292;
                mondat[i].y = 18;
                mondat[i].xr = 0;
                mondat[i].yr = 0;
                mondat[i].direction = 4;
                mondat[i].horizontalDir = 4;
                nextMonster++;
                nextMonTime = monGapTime;
                mondat[i].spawnTime = 5;
                dig.sprite.movedrawspr(i + 8, mondat[i].x, mondat[i].y);
                break;
            }
    }

    /** Main per-frame update: spawning, movement AI, and death animation. */
    void doMonsters() {
        if (nextMonTime > 0)
            nextMonTime--;
        else {
            if (nextMonster < totalMonsters && getMonstersOnScreenCount() < maxMonOnScreen && dig.digonscr &&
                    !dig.bonusmode)
                createMonster();
            if (unbonusFlag && nextMonster == totalMonsters && nextMonTime == 0)
                if (dig.digonscr) {
                    unbonusFlag = false;
                    dig.createbonus();
                }
        }
        for (int i = 0; i < MAX_MONSTERS; i++)
            if (mondat[i].flag) {
                if (mondat[i].huntTime > 10 - dig.main.getLevelNumberClampedToTen()) {
                    if (mondat[i].nob) {
                        mondat[i].nob = false;
                        mondat[i].huntTime = 0;
                    }
                }
                if (mondat[i].alive)
                    if (mondat[i].type == 0) {
                        handleMonsterAi(i);
                        if (dig.main.randomNumber(15 - dig.main.getLevelNumberClampedToTen()) == 0 && mondat[i].nob)
                            handleMonsterAi(i);
                    } else
                        mondat[i].type--;
                else
                    handleMonsterDeath(i);
            }
    }

    void eraseMonsters() {
        for (int i = 0; i < MAX_MONSTERS; i++)
            if (mondat[i].flag)
                dig.sprite.erasespr(i + 8);
    }

    /** Checks if a monster can move in the given direction from cell (x, y). */
    boolean fieldClear(int dir, int x, int y) {
        switch (dir) {
            case 0:
                if (x < 14)
                    if ((getfield(x + 1, y) & 0x2000) == 0)
                        if ((getfield(x + 1, y) & 1) == 0 || (getfield(x, y) & 0x10) == 0)
                            return true;
                break;
            case 4:
                if (x > 0)
                    if ((getfield(x - 1, y) & 0x2000) == 0)
                        if ((getfield(x - 1, y) & 0x10) == 0 || (getfield(x, y) & 1) == 0)
                            return true;
                break;
            case 2:
                if (y > 0)
                    if ((getfield(x, y - 1) & 0x2000) == 0)
                        if ((getfield(x, y - 1) & 0x800) == 0 || (getfield(x, y) & 0x40) == 0)
                            return true;
                break;
            case 6:
                if (y < 9)
                    if ((getfield(x, y + 1) & 0x2000) == 0)
                        if ((getfield(x, y + 1) & 0x40) == 0 || (getfield(x, y) & 0x800) == 0)
                            return true;
        }
        return false;
    }

    int getfield(int x, int y) {
        return dig.drawing.field[y * 15 + x];
    }

    /** Adds a delay penalty to monster movement timers. */
    void increaseMonsterDelay(int n) {
        if (n > 6)
            n = 6;
        for (int m = 1; m < n; m++)
            mondat[m].type++;
    }

    void incpenalties(int bits) {
        for (int m = 0, b = 256; m < MAX_MONSTERS; m++, b <<= 1) {
            if ((bits & b) != 0)
                dig.main.incrementPenalty();
            b <<= 1;
        }
    }

    /** Initializes monster parameters for the current level. */
    void initMonsters() {
        for (int i = 0; i < MAX_MONSTERS; i++)
            mondat[i].flag = false;
        nextMonster = 0;
        monGapTime = 45 - (dig.main.getLevelNumberClampedToTen() << 1);
        totalMonsters = dig.main.getLevelNumberClampedToTen() + 5;
        switch (dig.main.getLevelNumberClampedToTen()) {
            case 1:
                maxMonOnScreen = 3;
                break;
            case 2: case 3: case 4: case 5: case 6: case 7:
                maxMonOnScreen = 4;
                break;
            case 8: case 9: case 10:
                maxMonOnScreen = 5;
        }
        nextMonTime = 10;
        unbonusFlag = true;
    }

    void killMonster(int mon) {
        if (mondat[mon].flag) {
            mondat[mon].flag = mondat[mon].alive = false;
            dig.sprite.erasespr(mon + 8);
            if (dig.bonusmode)
                totalMonsters++;
        }
    }

    /** Kills all monsters matching the bitmask. Returns the number killed. */
    int killmonsters(int bits) {
        int n = 0;
        for (int m = 0, b = 256; m < MAX_MONSTERS; m++, b <<= 1)
            if ((bits & b) != 0) {
                killMonster(m);
                n++;
            }
        return n;
    }

    /** Monster AI: decides direction and performs movement. */
    void handleMonsterAi(int mon) {
        int clbits, monox, monoy, dir, mdirp1, mdirp2, mdirp3, mdirp4, t;
        boolean push;
        monox = mondat[mon].x;
        monoy = mondat[mon].y;
        if (mondat[mon].xr == 0 && mondat[mon].yr == 0) {

            // Turn hobbin back into nobbin if hunt time exceeded
            if (mondat[mon].huntTime > 30 + (dig.main.getLevelNumberClampedToTen() << 1))
                if (!mondat[mon].nob) {
                    mondat[mon].huntTime = 0;
                    mondat[mon].nob = true;
                }

            // Set up chase priorities based on digger's position
            if (Math.abs(dig.diggery - mondat[mon].y) > Math.abs(dig.diggerx - mondat[mon].x)) {
                mdirp1 = dig.diggery < mondat[mon].y ? 2 : 6;
                mdirp4 = dig.diggery < mondat[mon].y ? 6 : 2;
                mdirp2 = dig.diggerx < mondat[mon].x ? 4 : 0;
                mdirp3 = dig.diggerx < mondat[mon].x ? 0 : 4;
            } else {
                mdirp1 = dig.diggerx < mondat[mon].x ? 4 : 0;
                mdirp4 = dig.diggerx < mondat[mon].x ? 0 : 4;
                mdirp2 = dig.diggery < mondat[mon].y ? 2 : 6;
                mdirp3 = dig.diggery < mondat[mon].y ? 6 : 2;
            }

            // In bonus mode, run away from digger
            if (dig.bonusmode) {
                t = mdirp1; mdirp1 = mdirp4; mdirp4 = t;
                t = mdirp2; mdirp2 = mdirp3; mdirp3 = t;
            }

            // Adjust priorities so monsters don't reverse unless necessary
            dir = dig.reversedir(mondat[mon].direction);
            if (dir == mdirp1) { mdirp1 = mdirp2; mdirp2 = mdirp3; mdirp3 = mdirp4; mdirp4 = dir; }
            if (dir == mdirp2) { mdirp2 = mdirp3; mdirp3 = mdirp4; mdirp4 = dir; }
            if (dir == mdirp3) { mdirp3 = mdirp4; mdirp4 = dir; }

            // Random element on easier levels: occasionally swap p1 and p3
            if (dig.main.randomNumber(dig.main.getLevelNumberClampedToTen() + 5) == 1
                    && dig.main.getLevelNumberClampedToTen() < 6) {
                t = mdirp1; mdirp1 = mdirp3; mdirp3 = t;
            }

            // Check field and find direction
            if (fieldClear(mdirp1, mondat[mon].h, mondat[mon].v))
                dir = mdirp1;
            else if (fieldClear(mdirp2, mondat[mon].h, mondat[mon].v))
                dir = mdirp2;
            else if (fieldClear(mdirp3, mondat[mon].h, mondat[mon].v))
                dir = mdirp3;
            else if (fieldClear(mdirp4, mondat[mon].h, mondat[mon].v))
                dir = mdirp4;

            // Hobbins ignore the field and go where they want
            if (!mondat[mon].nob)
                dir = mdirp1;

            // Time penalty for changing direction
            if (mondat[mon].direction != dir)
                mondat[mon].type++;

            mondat[mon].direction = dir;
        }

        // Prevent monster from going off-screen
        if ((mondat[mon].x == 292 && mondat[mon].direction == 0) ||
                (mondat[mon].x == 12 && mondat[mon].direction == 4) ||
                (mondat[mon].y == 180 && mondat[mon].direction == 6) ||
                (mondat[mon].y == 18 && mondat[mon].direction == 2))
            mondat[mon].direction = -1;

        // Update horizontal direction for hobbin
        if (mondat[mon].direction == 4 || mondat[mon].direction == 0)
            mondat[mon].horizontalDir = mondat[mon].direction;

        // Hobbins dig through dirt
        if (!mondat[mon].nob)
            dig.drawing.digTunnel(mondat[mon].x, mondat[mon].y, mondat[mon].direction);

        // Move monster and draw tunnel blobs for hobbins
        switch (mondat[mon].direction) {
            case 0:
                if (!mondat[mon].nob)
                    dig.drawing.drawTunnelEdgeRight(mondat[mon].x, mondat[mon].y);
                mondat[mon].x += 4;
                break;
            case 4:
                if (!mondat[mon].nob)
                    dig.drawing.drawTunnelEdgeLeft(mondat[mon].x, mondat[mon].y);
                mondat[mon].x -= 4;
                break;
            case 2:
                if (!mondat[mon].nob)
                    dig.drawing.drawTunnelEdgeTop(mondat[mon].x, mondat[mon].y);
                mondat[mon].y -= 3;
                break;
            case 6:
                if (!mondat[mon].nob)
                    dig.drawing.drawTunnelEdgeBottom(mondat[mon].x, mondat[mon].y);
                mondat[mon].y += 3;
                break;
        }

        // Hobbins can eat emeralds
        if (!mondat[mon].nob)
            dig.hitemerald((mondat[mon].x - 12) / 20, (mondat[mon].y - 18) / 18,
                    (mondat[mon].x - 12) % 20, (mondat[mon].y - 18) % 18, mondat[mon].direction);

        // If digger's gone, don't move
        if (!dig.digonscr) {
            mondat[mon].x = monox;
            mondat[mon].y = monoy;
        }

        // If monster just spawned, don't move yet
        if (mondat[mon].spawnTime != 0) {
            mondat[mon].spawnTime--;
            mondat[mon].x = monox;
            mondat[mon].y = monoy;
        }

        // Increase hunt counter for hobbin
        if (!mondat[mon].nob && mondat[mon].huntTime < 100)
            mondat[mon].huntTime++;

        // Draw monster and check collisions
        push = true;
        clbits = dig.drawing.drawMonster(mon, mondat[mon].nob, mondat[mon].horizontalDir, mondat[mon].x, mondat[mon].y);
        dig.main.incrementPenalty();

        // Collision with another monster
        if ((clbits & 0x3f00) != 0) {
            mondat[mon].type++;
            checkcoincide(mon, clbits);
            incpenalties(clbits);
        }

        // Collision with bag
        if ((clbits & dig.bags.bagbits()) != 0) {
            mondat[mon].type++;
            monGotGold = false;
            if (mondat[mon].direction == 4 || mondat[mon].direction == 0) {
                push = dig.bags.pushbags(mondat[mon].direction, clbits);
                mondat[mon].type++;
            } else if (!dig.bags.pushudbags(clbits))
                push = false;
            if (monGotGold)
                mondat[mon].type = 0;
            if (!mondat[mon].nob && mondat[mon].huntTime > 1)
                dig.bags.removebags(clbits);
        }

        // Increase hobbin cross counter
        if (mondat[mon].nob && ((clbits & 0x3f00) != 0) && dig.digonscr)
            mondat[mon].huntTime++;

        // Bags push monster back
        if (!push) {
            mondat[mon].x = monox;
            mondat[mon].y = monoy;
            dig.drawing.drawMonster(mon, mondat[mon].nob, mondat[mon].horizontalDir, mondat[mon].x, mondat[mon].y);
            dig.main.incrementPenalty();
            if (mondat[mon].nob)
                mondat[mon].huntTime++;
            if ((mondat[mon].direction == 2 || mondat[mon].direction == 6) && mondat[mon].nob)
                mondat[mon].direction = dig.reversedir(mondat[mon].direction);
        }

        // Collision with digger
        if (((clbits & 1) != 0) && dig.digonscr)
            if (dig.bonusmode) {
                killMonster(mon);
                dig.scores.scoreeatm();
                dig.sound.soundEatm();
            } else
                dig.killdigger(3, 0);

        // Update cell coordinates
        mondat[mon].h = (mondat[mon].x - 12) / 20;
        mondat[mon].v = (mondat[mon].y - 18) / 18;
        mondat[mon].xr = (mondat[mon].x - 12) % 20;
        mondat[mon].yr = (mondat[mon].y - 18) % 18;
    }

    /** Monster death animation: crushed by bag or fading out. */
    void handleMonsterDeath(int mon) {
        switch (mondat[mon].death) {
            case 1:
                if (dig.bags.bagy(mondat[mon].bag) + 6 > mondat[mon].y)
                    mondat[mon].y = dig.bags.bagy(mondat[mon].bag);
                dig.drawing.drawMonsterDeath(mon, mondat[mon].nob, mondat[mon].horizontalDir, mondat[mon].x, mondat[mon].y);
                dig.main.incrementPenalty();
                if (dig.bags.getbagdir(mondat[mon].bag) == -1) {
                    mondat[mon].deathTime = 1;
                    mondat[mon].death = 4;
                }
                break;
            case 4:
                if (mondat[mon].deathTime != 0)
                    mondat[mon].deathTime--;
                else {
                    killMonster(mon);
                    dig.scores.scorekill();
                }
        }
    }

    void monsterGotGold() {
        monGotGold = true;
    }

    int getMonstersLeft() {
        return getMonstersOnScreenCount() + totalMonsters - nextMonster;
    }

    int getMonstersOnScreenCount() {
        int n = 0;
        for (int i = 0; i < MAX_MONSTERS; i++)
            if (mondat[i].flag)
                n++;
        return n;
    }

    void squashmonster(int mon, int death, int bag) {
        mondat[mon].alive = false;
        mondat[mon].death = death;
        mondat[mon].bag = bag;
    }

    void squashmonsters(int bag, int bits) {
        for (int m = 0, b = 256; m < MAX_MONSTERS; m++, b <<= 1)
            if ((bits & b) != 0)
                if (mondat[m].y >= dig.bags.bagy(bag))
                    squashmonster(m, 1, bag);
    }
}
