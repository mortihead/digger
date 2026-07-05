package org.digger.app;

import java.awt.event.KeyEvent;

/**
 * Main game loop controller: title screen, level transitions, player switching.
 */
class Main {

    private final Digger dig;

    /**
     * Sprite draw priority order (higher index = drawn first).
     */
    int[] digSprOrder = {14, 13, 7, 6, 5, 4, 3, 2, 1, 12, 11, 10, 9, 8, 15, 0};

    GameState[] gameData = {new GameState(), new GameState()};

    String playerDisplayBuffer = "";

    int currentPlayer = 0;
    int numPlayers = 0;
    int penalty = 0;
    boolean levelNotDrawn = false;
    boolean flashPlayer = false;

    int speedMul = 40;
    int scaleFactor = 2;

    int randSeed;

    /**
     * Level layout data: 8 plans × 10 rows × 15 columns.
     */
    String[][] levelData = {
        {"S   B     HHHHS",
            "V  CC  C  V B  ",
            "VB CC  C  V    ",
            "V  CCB CB V CCC",
            "V  CC  C  V CCC",
            "HH CC  C  V CCC",
            " V    B B V    ",
            " HHHH     V    ",
            "C   V     V   C",
            "CC  HHHHHHH  CC"},
        {"SHHHHH  B B  HS",
            " CC  V       V ",
            " CC  V CCCCC V ",
            "BCCB V CCCCC V ",
            "CCCC V       V ",
            "CCCC V B  HHHH ",
            " CC  V CC V    ",
            " BB  VCCCCV CC ",
            "C    V CC V CC ",
            "CC   HHHHHH    "},
        {"SHHHHB B BHHHHS",
            "CC  V C C V BB ",
            "C   V C C V CC ",
            " BB V C C VCCCC",
            "CCCCV C C VCCCC",
            "CCCCHHHHHHH CC ",
            " CC  C V C  CC ",
            " CC  C V C     ",
            "C    C V C    C",
            "CC   C H C   CC"},
        {"SHBCCCCBCCCCBHS",
            "CV  CCCCCCC  VC",
            "CHHH CCCCC HHHC",
            "C  V  CCC  V  C",
            "   HHH C HHH   ",
            "  B  V B V  B  ",
            "  C  VCCCV  C  ",
            " CCC HHHHH CCC ",
            "CCCCC CVC CCCCC",
            "CCCCC CHC CCCCC"},
        {"SHHHHHHHHHHHHHS",
            "VBCCCCBVCCCCCCV",
            "VCCCCCCV CCBC V",
            "V CCCC VCCBCCCV",
            "VCCCCCCV CCCC V",
            "V CCCC VBCCCCCV",
            "VCCBCCCV CCCC V",
            "V CCBC VCCCCCCV",
            "VCCCCCCVCCCCCCV",
            "HHHHHHHHHHHHHHH"},
        {"SHHHHHHHHHHHHHS",
            "VCBCCV V VCCBCV",
            "VCCC VBVBV CCCV",
            "VCCCHH V HHCCCV",
            "VCC V CVC V CCV",
            "VCCHH CVC HHCCV",
            "VC V CCVCC V CV",
            "VCHHBCCVCCBHHCV",
            "VCVCCCCVCCCCVCV",
            "HHHHHHHHHHHHHHH"},
        {"SHCCCCCVCCCCCHS",
            " VCBCBCVCBCBCV ",
            "BVCCCCCVCCCCCVB",
            "CHHCCCCVCCCCHHC",
            "CCV CCCVCCC VCC",
            "CCHHHCCVCCHHHCC",
            "CCCCV CVC VCCCC",
            "CCCCHH V HHCCCC",
            "CCCCCV V VCCCCC",
            "CCCCCHHHHHCCCCC"},
        {"HHHHHHHHHHHHHHS",
            "V CCBCCCCCBCC V",
            "HHHCCCCBCCCCHHH",
            "VBV CCCCCCC VBV",
            "VCHHHCCCCCHHHCV",
            "VCCBV CCC VBCCV",
            "VCCCHHHCHHHCCCV",
            "VCCCC V V CCCCV",
            "VCCCCCV VCCCCCV",
            "HHHHHHHHHHHHHHH"}};

    Main(Digger d) {
        dig = d;
    }

    void addLife(int pl) {
        gameData[pl - 1].lives++;
        dig.sound.sound1Up();
    }

    void calibrate() {
        dig.sound.volume = 1;
    }

    /**
     * Checks if the current level is completed (all emeralds collected or all monsters gone).
     */
    void checkLevelDone() {
        if ((dig.countEmeralds() == 0 || dig.monster.getMonstersLeft() == 0) && dig.digonscr)
            gameData[currentPlayer].levelDone = true;
        else
            gameData[currentPlayer].levelDone = false;
    }

    void clearTopLine() {
        dig.drawing.drawText("                          ", 0, 0, 3);
        dig.drawing.drawText(" ", 308, 0, 3);
    }

    void drawScreen() {
        dig.drawing.createAllSprites();
        dig.drawing.drawFieldAndBackground();
        dig.bags.drawBags();
        dig.drawEmeralds();
        dig.initDigger();
        dig.monster.initMonsters();
    }

    int getCurrentPlayer() {
        return currentPlayer;
    }

    int getLevelChar(int x, int y, int l) {
        if (l == 0)
            l++;
        return levelData[l - 1][y].charAt(x);
    }

    int getLives(int pl) {
        return gameData[pl - 1].lives;
    }

    void incrementPenalty() {
        penalty++;
    }

    void initChars() {
        dig.drawing.initAllSprites();
        dig.initDigger();
        dig.monster.initMonsters();
    }

    void initLevel() {
        gameData[currentPlayer].levelDone = false;
        dig.drawing.buildField();
        dig.makeEmeraldField();
        dig.bags.initBags();
        levelNotDrawn = true;
    }

    int getLevelNumber() {
        return gameData[currentPlayer].level;
    }

    int getLevelNumberClampedToTen() {
        if (gameData[currentPlayer].level > 10)
            return 10;
        return gameData[currentPlayer].level;
    }

    int getLevelPlan() {
        int l = getLevelNumber();
        if (l > 8)
            l = (l & 3) + 5; // Level plan: 12345678, 678, (5678) 247 times, 5 forever
        return l;
    }

    /**
     * Main entry point: title screen → game loop → back to title.
     */
    void main() {
        int frame, t, x = 0;
        boolean start;

        dig.time = dig.display.getCurrentTimeMillis();
        calibrate();
        dig.ftime = speedMul * 2000L;
        dig.sprite.setretr(false);
        dig.display.init();
        dig.sprite.setretr(true);
        dig.display.setPalette(0);
        dig.input.initKeyboard();
        dig.input.detectJoystick();
        dig.scores.loadScores();
        dig.sound.initSound();

        dig.scores.run();
        dig.scores._updatescores(dig.scores.scores);

        numPlayers = 1;
        do {
            dig.sound.soundStop();
            dig.sprite.setsprorder(digSprOrder);
            dig.drawing.createAllSprites();
            dig.input.detectJoystick();
            dig.display.clearScreen();
            dig.display.drawTitleScreen();
            dig.drawing.drawText("D I G G E R", 100, 0, 3);
            showPlayers();
            dig.scores.showTable();
            start = false;
            frame = 0;

            dig.time = dig.display.getCurrentTimeMillis();

            while (!start) {
                start = dig.input.testStart();
                if (dig.input.keypressed != 0) {
                    if (dig.input.keypressed == KeyEvent.VK_ESCAPE) {
                        switchPlayerCount();
                        showPlayers();
                    }
                    dig.input.akeypressed = 0;
                    dig.input.keypressed = 0;
                }
                if (frame == 0)
                    for (t = 54; t < 174; t += 12)
                        dig.drawing.drawText("            ", 164, t, 0);
                if (frame == 50) {
                    dig.sprite.movedrawspr(8, 292, 63);
                    x = 292;
                }
                if (frame > 50 && frame <= 77) {
                    x -= 4;
                    dig.drawing.drawMonster(0, true, 4, x, 63);
                }
                if (frame > 77)
                    dig.drawing.drawMonster(0, true, 0, 184, 63);
                if (frame == 83)
                    dig.drawing.drawText("NOBBIN", 216, 64, 2);
                if (frame == 90) {
                    dig.sprite.movedrawspr(9, 292, 82);
                    dig.drawing.drawMonster(1, false, 4, 292, 82);
                    x = 292;
                }
                if (frame > 90 && frame <= 117) {
                    x -= 4;
                    dig.drawing.drawMonster(1, false, 4, x, 82);
                }
                if (frame > 117)
                    dig.drawing.drawMonster(1, false, 0, 184, 82);
                if (frame == 123)
                    dig.drawing.drawText("HOBBIN", 216, 83, 2);
                if (frame == 130) {
                    dig.sprite.movedrawspr(0, 292, 101);
                    dig.drawing.drawDigger(4, 292, 101, true);
                    x = 292;
                }
                if (frame > 130 && frame <= 157) {
                    x -= 4;
                    dig.drawing.drawDigger(4, x, 101, true);
                }
                if (frame > 157)
                    dig.drawing.drawDigger(0, 184, 101, true);
                if (frame == 163)
                    dig.drawing.drawText("DIGGER", 216, 102, 2);
                if (frame == 178) {
                    dig.sprite.movedrawspr(1, 184, 120);
                    dig.drawing.drawGold(1, 0, 184, 120);
                }
                if (frame == 183)
                    dig.drawing.drawText("GOLD", 216, 121, 2);
                if (frame == 198)
                    dig.drawing.drawEmerald(184, 141);
                if (frame == 203)
                    dig.drawing.drawText("EMERALD", 216, 140, 2);
                if (frame == 218)
                    dig.drawing.drawBonus(184, 158);
                if (frame == 223)
                    dig.drawing.drawText("BONUS", 216, 159, 2);
                dig.newFrame();
                frame++;
                if (frame > 250)
                    frame = 0;
                if (!dig.running && frame > 1) {
                    return;
                }
            }
            // Clear residual key state so spacebar at title screen
            // doesn't immediately trigger pause in gameplay
            dig.input.akeypressed = 0;
            dig.input.keypressed = 0;

            gameData[0].level = 1;
            gameData[0].lives = 3;
            if (numPlayers == 2) {
                gameData[1].level = 1;
                gameData[1].lives = 3;
            } else
                gameData[1].lives = 0;
            dig.display.clearScreen();
            currentPlayer = 0;
            initLevel();
            currentPlayer = 1;
            initLevel();
            dig.scores.zeroScores();
            dig.bonusvisible = true;
            if (numPlayers == 2)
                flashPlayer = true;
            currentPlayer = 0;
            while ((gameData[0].lives != 0 || gameData[1].lives != 0) && !dig.input.escape) {
                gameData[currentPlayer].dead = false;
                while (!gameData[currentPlayer].dead && gameData[currentPlayer].lives != 0 && !dig.input.escape) {
                    dig.drawing.initAllSprites();
                    play();
                }
                if (gameData[1 - currentPlayer].lives != 0) {
                    currentPlayer = 1 - currentPlayer;
                    flashPlayer = levelNotDrawn = true;
                }
            }
            dig.input.escape = false;
        } while (true);
    }

    /**
     * Single level gameplay loop.
     */
    void play() {
        int t, c;
        if (levelNotDrawn) {
            levelNotDrawn = false;
            drawScreen();
            dig.time = dig.display.getCurrentTimeMillis();
            if (flashPlayer) {
                flashPlayer = false;
                playerDisplayBuffer = "PLAYER ";
                playerDisplayBuffer += (currentPlayer == 0) ? "1" : "2";
                clearTopLine();
                for (t = 0; t < 15; t++)
                    for (c = 1; c <= 3; c++) {
                        dig.drawing.drawText(playerDisplayBuffer, 108, 0, c);
                        dig.scores.writeCurrentScore(c);
                        dig.newFrame();
                        if (dig.input.escape)
                            return;
                    }
                dig.scores.drawScores();
                dig.scores.addScore(0);
            }
        } else
            initChars();
        dig.input.keypressed = 0;
        dig.drawing.drawText("        ", 108, 0, 3);
        dig.scores.initScores();
        dig.drawing.drawLives();
        dig.sound.music(1);
        dig.input.readDirection();
        dig.time = dig.display.getCurrentTimeMillis();
        while (!gameData[currentPlayer].dead && !gameData[currentPlayer].levelDone && !dig.input.escape) {
            penalty = 0;
            dig.doDigger();
            dig.monster.doMonsters();
            dig.bags.doBags();
            if (penalty > 8)
                dig.monster.increaseMonsterDelay(penalty - 8);
            testPause();
            checkLevelDone();
        }
        dig.eraseDigger();
        dig.sound.musicOff();
        t = 20;
        while ((dig.bags.getMovingBagsCount() != 0 || t != 0) && !dig.input.escape) {
            if (t != 0)
                t--;
            penalty = 0;
            dig.bags.doBags();
            dig.doDigger();
            dig.monster.doMonsters();
            if (penalty < 8)
                t = 0;
        }
        dig.sound.soundStop();
        dig.killFire();
        dig.eraseBonus();
        dig.bags.cleanupBags();
        dig.drawing.saveFieldSnapshot();
        dig.monster.eraseMonsters();
        dig.newFrame();
        if (gameData[currentPlayer].levelDone)
            dig.sound.soundLevDone();
        if (dig.countEmeralds() == 0) {
            gameData[currentPlayer].level++;
            if (gameData[currentPlayer].level > 1000)
                gameData[currentPlayer].level = 1000;
            initLevel();
        }
        if (gameData[currentPlayer].dead) {
            gameData[currentPlayer].lives--;
            dig.drawing.drawLives();
            if (gameData[currentPlayer].lives == 0 && !dig.input.escape)
                dig.scores.endOfGame();
        }
        if (gameData[currentPlayer].levelDone) {
            gameData[currentPlayer].level++;
            if (gameData[currentPlayer].level > 1000)
                gameData[currentPlayer].level = 1000;
            initLevel();
        }
    }

    int randomNumber(int n) {
        randSeed = randSeed * 0x15a4e35 + 1;
        return (randSeed & 0x7fffffff) % n;
    }

    void setDead(boolean bp6) {
        gameData[currentPlayer].dead = bp6;
    }

    void showPlayers() {
        if (numPlayers == 1) {
            dig.drawing.drawText("ONE", 220, 25, 3);
            dig.drawing.drawText(" PLAYER ", 192, 39, 3);
        } else {
            dig.drawing.drawText("TWO", 220, 25, 3);
            dig.drawing.drawText(" PLAYERS", 184, 39, 3);
        }
    }

    void switchPlayerCount() {
        numPlayers = 3 - numPlayers;
    }

    void testPause() {
        if (dig.input.akeypressed == 32) { // Space bar
            dig.input.akeypressed = 0;
            dig.sound.soundPause();
            dig.sound.setT2Val(40);
            dig.sound.setSoundT2();
            clearTopLine();
            dig.drawing.drawText("PAUSED", 124, 0, 1);
            dig.newFrame();
            dig.input.keypressed = 0;
            boolean interrupted = false;
            while (dig.input.keypressed == 0 && !interrupted) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted = true;
                }
            }
            clearTopLine();
            dig.scores.drawScores();
            dig.scores.addScore(0);
            dig.drawing.drawLives();
            dig.newFrame();
            dig.time = dig.display.getCurrentTimeMillis() - dig.frametime;
            dig.input.keypressed = 0;
        } else
            dig.sound.soundPauseOff();
    }
}
