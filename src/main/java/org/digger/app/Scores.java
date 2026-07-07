package org.digger.app;

import java.util.Arrays;
import java.util.prefs.Preferences;

class Scores {
    private static final int MAX_SCORES = 11;
    private static final int PLAYER_ONE = 0;
    private static final int PLAYER_TWO = 1;
    private static Preferences prefs = Preferences.userNodeForPackage(Scores.class);

    Digger dig;
    String substr;

    char highbuf[] = new char[10];
    long scoreHigh[] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};    // [12]
    String scoreinit[] = new String[MAX_SCORES];
    long finalScore = 0, player1Score = 0, player2Score = 0, player1NextLifeScore = 0, player2NextLifeScore = 0;
    String hsbuf;
    int bonusScore = 20000;
    boolean gotInitFlag = false;

    Scores(Digger d) {
        dig = d;
    }



    void addScore(int score) {
        if (dig.main.getCurrentPlayer() == PLAYER_ONE) {
            player1Score += score;
            if (player1Score > 999999l)
                player1Score = 0;
            writenum(player1Score, 0, 0, 6, 1);
            if (player1Score >= player1NextLifeScore) {
                if (dig.main.getLives(1) < 5) {
                    dig.main.addLife(1);
                    dig.drawing.drawLives();
                }
                player1NextLifeScore += bonusScore;
            }
        } else {
            player2Score += score;
            if (player2Score > 999999l)
                player2Score = 0;
            if (player2Score < 100000l)
                writenum(player2Score, 236, 0, 6, 1);
            else
                writenum(player2Score, 248, 0, 6, 1);
            if (player2Score > player2NextLifeScore) {   /* Player 2 doesn't get the life until >20,000 ! */
                if (dig.main.getLives(2) < 5) {
                    dig.main.addLife(2);
                    dig.drawing.drawLives();
                }
                player2NextLifeScore += bonusScore;
            }
        }
        dig.main.incrementPenalty();
        dig.main.incrementPenalty();
        dig.main.incrementPenalty();
    }

    void drawScores() {
        writenum(player1Score, 0, 0, 6, 3);
        if (dig.main.numPlayers == 2)
            if (player2Score < 100000L)
                writenum(player2Score, 236, 0, 6, 3);
            else
                writenum(player2Score, 248, 0, 6, 3);
    }

    void endOfGame() {
        int i, j;
        addScore(0);
        if (dig.main.getCurrentPlayer() == PLAYER_ONE)
            finalScore = player1Score;
        else
            finalScore = player2Score;
        // scorehigh[11] holds the lowest score in the top-10 table (10th place).
        // If current score is higher, the player qualifies for the high score list.
        if (finalScore > scoreHigh[11]) {
            dig.display.clearScreen();
            drawScores();
            dig.main.playerDisplayBuffer = "PLAYER ";
            if (dig.main.getCurrentPlayer() == PLAYER_ONE)
                dig.main.playerDisplayBuffer += "1";
            else
                dig.main.playerDisplayBuffer += "2";
            dig.drawing.drawText(dig.main.playerDisplayBuffer, 108, 0, 2, true);
            getInitials();
            shuffleHigh();
            // scoreinit[0]
            // finalScore, player1Score
            //
            saveScores();
        } else {
            dig.main.clearTopLine();
            dig.drawing.drawText("GAME OVER", 104, 0, 3, true);
            dig.sound.killSound();
            for (j = 0; j < 20; j++) /* Number of times screen flashes * 2 */
                for (i = 0; i < 2; i++) { //i<8;i++) {
                    dig.display.setPalette(1 - (j & 1));
                    flashyWait(1);  // Brief pause between palette changes
                    dig.display.setPalette(0);
                    dig.display.setIntensity(1 - i & 1);
                    dig.newFrame();
                }
            dig.sound.setupSound();
            dig.drawing.drawText("         ", 104, 0, 3, true);
        }
    }


    void flashyWait(int n) {
        try {
            Thread.sleep(n * 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks if the key code is a regular printable character.
     * Function keys (F1-F10) have the 0x80 bit set and are excluded.
     *
     * @param kp key code from Input.lastKeyCode
     * @return true if it's a regular character (A-Z, digits, etc.), false for function keys
     */
    private boolean isRegularKey(int kp) {
        return kp != 0 && (kp & 0x80) == 0;
    }

    int getInitial(int x, int y) {
        int i, j;
        dig.input.lastKeyCode = 0;
        dig.display.drawChar(x, y, '_', 3, true);
        for (j = 0; j < 5; j++) {
            for (i = 0; i < 40; i++) {
                if (isRegularKey(dig.input.lastKeyCode))
                    return dig.input.lastKeyCode;
                flashyWait(15);
            }
            for (i = 0; i < 40; i++) {
                if (isRegularKey(dig.input.lastKeyCode)) {
                    dig.display.drawChar(x, y, '_', 3, true);
                    return dig.input.lastKeyCode;
                }
                flashyWait(15);
            }
        }
        gotInitFlag = true;
        return 0;
    }

    void getInitials() {
        int k, i;
        dig.drawing.drawText("ENTER YOUR", 100, 70, 3, true);
        dig.drawing.drawText(" INITIALS", 100, 90, 3, true);
        dig.drawing.drawText("_ _ _", 128, 130, 3, true);
        scoreinit[0] = "...";
        dig.sound.killSound();
        gotInitFlag = false;
        for (i = 0; i < 3; i++) {
            k = 0;
            while (k == 0 && !gotInitFlag) {
                k = getInitial(i * 24 + 128, 130);
                if (i != 0 && k == 8)
                    i--;
                k = dig.input.getAsciiKey(dig.input.lastKeyCode);
            }
            if (k != 0) {
                dig.display.drawChar(i * 24 + 128, 130, k, 3, true);
                StringBuffer sb = new StringBuffer(scoreinit[0]);
                sb.setCharAt(i, (char) k);
                scoreinit[0] = sb.toString();
            }
        }
        dig.input.lastKeyCode = 0;
        for (i = 0; i < 20; i++)
            flashyWait(15);
        dig.sound.setupSound();
        dig.display.clearScreen();
        dig.display.setPalette(0);
        dig.display.setIntensity(0);
        dig.newFrame();    // needed by Java version!!
    }

    void initScores() {
        addScore(0);
    }

    void loadScores() {
        readScores();
    }

    private void saveScores() {
        for (int i = 0; i < MAX_SCORES; i++) {
            prefs.put("name_" + i, scoreinit[i] != null ? scoreinit[i] : "Player");
            prefs.putLong("score_" + i, scoreHigh[i]);
        }
        System.out.println("Score saved");
    }

    private void readScores() {
        // Заполняем массивы значениями по умолчанию
        Arrays.fill(scoreinit, "---");
        Arrays.fill(scoreHigh, 0L);

        // Загружаем сохраненные значения
        for (int i = 0; i < MAX_SCORES; i++) {
            scoreinit[i] = prefs.get("name_" + i, "---");
            scoreHigh[i] = prefs.getLong("score_" + i, 0L);
        }
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║       DIGGER - Game Controls         ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.println("║                                      ║");
        System.out.println("║  Movement:                           ║");
        System.out.println("║    ← → ↑ ↓ / Arrow keys              ║");
        System.out.println("║                                      ║");
        System.out.println("║  Actions:                            ║");
        System.out.println("║    Enter    - Start game             ║");
        System.out.println("║    Space    - Pause/Resume           ║");
        System.out.println("║    Escape   - Switch between 1       ║");
        System.out.println("║               and 2 players (title)  ║");
        System.out.println("║    F1       - Fire (shoot)           ║");
        System.out.println("║    F10      - Return to title screen ║");
        System.out.println("║    +/-      - Adjust game speed      ║");
        System.out.println("║                                      ║");
        System.out.println("║  Sound:                              ║");
        System.out.println("║    F7          - Toggle music        ║");
        System.out.println("║    F9          - Toggle SFX          ║");
        System.out.println("║                                      ║");
        System.out.println("║  Game modes:                         ║");
        System.out.println("║    1 Player  - Single player         ║");
        System.out.println("║    2 Players - Versus mode           ║");
        System.out.println("║                                      ║");
        System.out.println("╚══════════════════════════════════════╝\n");
        System.out.println("Score loaded: " + prefs.toString() + "\n");
    }

    String numberToString(long n) {
        int x;
        String p = "";
        for (x = 0; x < 6; x++) {
            p = String.valueOf(n % 10) + p;
            n /= 10;
            if (n == 0) {
                x++;
                break;
            }
        }
        for (; x < 6; x++)
            p = ' ' + p;
        return p;
    }


    /**
     * Awards 1000 points for eating a bonus.
     */
    void scoreBonus() {
        addScore(1000);
    }

    /**
     * Award points for eating a monster while bonus mode is active.
     * Each eaten monster doubles the reward for the next one.
     * Base score is eatmsc * 200.
     */
    void scoreEatMonster() {
        addScore(dig.monsterEatMultiplier * 200);
        dig.monsterEatMultiplier <<= 1;
    }

    /**
     * Awards 25 points for eating an emerald.
     */
    void scoreEmerald() {
        addScore(25);
    }

    /**
     * Awards 500 points for eating a bag of gold.
     */
    void scoreGold() {
        addScore(500);
    }

    /**
     * Awards 250 points for killing a monster.
     */
    void scoreKillMonster() {
        addScore(250);
    }

    /**
     * Awards 250 points for eating an 'O' (octave bonus).
     */
    void scoreOctave() {
        addScore(250);
    }

    void showTable() {
        int i, col;
        dig.drawing.drawText("HIGH SCORES", 16, 25, 3);
        col = 2;
        for (i = 1; i < 11; i++) {
            hsbuf = scoreinit[i] + "  " + numberToString(scoreHigh[i + 1]);
            dig.drawing.drawText(hsbuf, 16, 31 + 13 * i, col);
            col = 1;
        }
    }

    void shuffleHigh() {
        int i, j;
        for (j = 10; j > 1; j--)
            if (finalScore < scoreHigh[j])
                break;
        for (i = 10; i > j; i--) {
            scoreHigh[i + 1] = scoreHigh[i];
            scoreinit[i] = scoreinit[i - 1];
        }
        scoreHigh[j + 1] = finalScore;
        scoreinit[j] = scoreinit[0];
    }

    void writeCurrentScore(int bp6) {
        if (dig.main.getCurrentPlayer() == PLAYER_ONE)
            writenum(player1Score, 0, 0, 6, bp6);
        else if (player2Score < 100000l)
            writenum(player2Score, 236, 0, 6, bp6);
        else
            writenum(player2Score, 248, 0, 6, bp6);
    }

    void writenum(long n, int x, int y, int w, int c) {
        int d, xp = (w - 1) * 12 + x;
        while (w > 0) {
            d = (int) (n % 10);
            if (w > 1 || d > 0)
                dig.display.drawChar(xp, y, d + '0', c, false);
            n /= 10;
            w--;
            xp -= 12;
        }
    }

    void zeroScores() {
        player2Score = 0;
        player1Score = 0;
        finalScore = 0;
        player1NextLifeScore = bonusScore;
        player2NextLifeScore = bonusScore;
    }
}
