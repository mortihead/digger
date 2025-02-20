package org.digger.app;

class Scores implements Runnable {

    Digger dig;
    Object[][] scores;
    String substr;

    char highbuf[] = new char[10];
    long scorehigh[] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};    // [12]
    String scoreinit[] = new String[11];
    long scoret = 0, score1 = 0, score2 = 0, nextbs1 = 0, nextbs2 = 0;
    String hsbuf;
    char scorebuf[] = new char[512];
    int bonusscore = 20000;
    boolean gotinitflag = false;

    Scores(Digger d) {
        dig = d;
    }

    public Object[][] _submit(String n, int s) {
        return scores;
    }

    public void _updatescores(Object[][] o) {

        if (o == null)
            return;
        if (1==1) return;
        try {
            String[] in = new String[10];
            int[] sc = new int[10];
            for (int i = 0; i < 10; i++) {
                in[i] = (String) o[i][0];
                sc[i] = ((Integer) o[i][1]).intValue();
            }
            for (int i = 0; i < 10; i++) {
                scoreinit[i + 1] = in[i];
                scorehigh[i + 2] = sc[i];
            }
        } catch (Exception e) {
        }
        ;

    }

    void addscore(int score) {
        if (dig.main.getcplayer() == 0) {
            score1 += score;
            if (score1 > 999999l)
                score1 = 0;
            writenum(score1, 0, 0, 6, 1);
            if (score1 >= nextbs1) {
                if (dig.main.getlives(1) < 5) {
                    dig.main.addlife(1);
                    dig.drawing.drawlives();
                }
                nextbs1 += bonusscore;
            }
        } else {
            score2 += score;
            if (score2 > 999999l)
                score2 = 0;
            if (score2 < 100000l)
                writenum(score2, 236, 0, 6, 1);
            else
                writenum(score2, 248, 0, 6, 1);
            if (score2 > nextbs2) {   /* Player 2 doesn't get the life until >20,000 ! */
                if (dig.main.getlives(2) < 5) {
                    dig.main.addlife(2);
                    dig.drawing.drawlives();
                }
                nextbs2 += bonusscore;
            }
        }
        dig.main.incpenalty();
        dig.main.incpenalty();
        dig.main.incpenalty();
    }

    void drawscores() {
        writenum(score1, 0, 0, 6, 3);
        if (dig.main.nplayers == 2)
            if (score2 < 100000l)
                writenum(score2, 236, 0, 6, 3);
            else
                writenum(score2, 248, 0, 6, 3);
    }

    void endofgame() {
        int i, j, z;
        addscore(0);
        if (dig.main.getcplayer() == 0)
            scoret = score1;
        else
            scoret = score2;
        if (scoret > scorehigh[11]) {
            dig.pc.gclear();
            drawscores();
            dig.main.pldispbuf = "PLAYER ";
            if (dig.main.getcplayer() == 0)
                dig.main.pldispbuf += "1";
            else
                dig.main.pldispbuf += "2";
            dig.drawing.outtext(dig.main.pldispbuf, 108, 0, 2, true);
            dig.drawing.outtext(" NEW HIGH SCORE ", 64, 40, 2, true);
            getinitials();
            _updatescores(_submit(scoreinit[0], (int) scoret));
            shufflehigh();
            // scoreinit[0]
            // scoret, score1
            //
//	savescores();
        } else {
            dig.main.cleartopline();
            dig.drawing.outtext("GAME OVER", 104, 0, 3, true);
            _updatescores(_submit("...", (int) scoret));
            dig.sound.killsound();
            for (j = 0; j < 20; j++) /* Number of times screen flashes * 2 */
                for (i = 0; i < 2; i++) { //i<8;i++) {
                    dig.sprite.setretr(true);
//		dig.Pc.ginten(1);
                    dig.pc.gpal(1 - (j & 1));
                    dig.sprite.setretr(false);
                    for (z = 0; z < 111; z++) ; /* A delay loop */
                    dig.pc.gpal(0);
//		dig.Pc.ginten(0);
                    dig.pc.ginten(1 - i & 1);
                    dig.newframe();
                }
            dig.sound.setupsound();
            dig.drawing.outtext("         ", 104, 0, 3, true);
            dig.sprite.setretr(true);
        }
    }

    void flashywait(int n) {
/*  int i,gt,cx,p=0,k=1;
  int gap=19;
  dig.Sprite.setretr(false);
  for (i=0;i<(n<<1);i++) {
	for (cx=0;cx<dig.Sound.volume;cx++) {
	  dig.Pc.gpal(p=1-p);
	  for (gt=0;gt<gap;gt++);
	}
	} */

        try {
            Thread.sleep(n * 2);
        } catch (Exception e) {
        }

    }

    int getinitial(int x, int y) {
        int i, j;
        dig.input.keypressed = 0;
        dig.pc.gwrite(x, y, '_', 3, true);
        for (j = 0; j < 5; j++) {
            for (i = 0; i < 40; i++) {
                if ((dig.input.keypressed & 0x80) == 0 && dig.input.keypressed != 0)
                    return dig.input.keypressed;
                flashywait(15);
            }
            for (i = 0; i < 40; i++) {
                if ((dig.input.keypressed & 0x80) == 0 && dig.input.keypressed != 0) {
                    dig.pc.gwrite(x, y, '_', 3, true);
                    return dig.input.keypressed;
                }
                flashywait(15);
            }
        }
        gotinitflag = true;
        return 0;
    }

    void getinitials() {
        int k, i;
        dig.drawing.outtext("ENTER YOUR", 100, 70, 3, true);
        dig.drawing.outtext(" INITIALS", 100, 90, 3, true);
        dig.drawing.outtext("_ _ _", 128, 130, 3, true);
        scoreinit[0] = "...";
        dig.sound.killsound();
        gotinitflag = false;
        for (i = 0; i < 3; i++) {
            k = 0;
            while (k == 0 && !gotinitflag) {
                k = getinitial(i * 24 + 128, 130);
                if (i != 0 && k == 8)
                    i--;
                k = dig.input.getasciikey(dig.input.keypressed);
            }
            if (k != 0) {
                dig.pc.gwrite(i * 24 + 128, 130, k, 3, true);
                StringBuffer sb = new StringBuffer(scoreinit[0]);
                sb.setCharAt(i, (char) k);
                scoreinit[0] = sb.toString();
            }
        }
        dig.input.keypressed = 0;
        for (i = 0; i < 20; i++)
            flashywait(15);
        dig.sound.setupsound();
        dig.pc.gclear();
        dig.pc.gpal(0);
        dig.pc.ginten(0);
        dig.newframe();    // needed by Java version!!
        dig.sprite.setretr(true);
    }

    void initscores() {
        addscore(0);
    }

    void loadscores() {
        int p = 1, i, x;
        readscores();
        /*
        for (i = 1; i < 11; i++) {
            for (x = 0; x < 3; x++)
                scoreinit[i] = "..."; //  scorebuf[p++];	--- zmienic
            p += 2;
            for (x = 0; x < 6; x++)
                highbuf[x] = scorebuf[p++];
            scorehigh[i + 1] = 0; //atol(highbuf);
        }
        if (scorebuf[0] != 's')
            for (i = 0; i < 11; i++) {
                scorehigh[i + 1] = 0;
                scoreinit[i] = "...";
            }
        scorehigh[1] = 1;

         */
        scoreinit[1] = "bob";
        scoreinit[2] = "ali";
    }

    private void readscores() {
        String[] names = {"Ali", "Bob", "Chr", "Dav", "Eva", "Den", "Max", "Joe", "Len", "...", "..."};
        long[] scoresLong = {8000L, 7000L, 6000L, 5000L, 4000L, 3000L, 2000L, 1000L, 500L, 0L, 0L};

        for (int i = 0; i < names.length; i++) {
            scoreinit[i] = names[i]; // Имя
            scorehigh[i] = scoresLong[i];
        }
    }

    String numtostring(long n) {
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



    void scorebonus() {
        addscore(1000);
    }

    void scoreeatm() {
        addscore(dig.eatmsc * 200);
        dig.eatmsc <<= 1;
    }

    void scoreemerald() {
        addscore(25);
    }

    void scoregold() {
        addscore(500);
    }

    void scorekill() {
        addscore(250);
    }

    void scoreoctave() {
        addscore(250);
    }

    void showtable() {
        int i, col;
        dig.drawing.outtext("HIGH SCORES", 16, 25, 3);
        col = 2;
        for (i = 1; i < 11; i++) {
            hsbuf = scoreinit[i] + "  " + numtostring(scorehigh[i + 1]);
            dig.drawing.outtext(hsbuf, 16, 31 + 13 * i, col);
            col = 1;
        }
    }

    void shufflehigh() {
        int i, j;
        for (j = 10; j > 1; j--)
            if (scoret < scorehigh[j])
                break;
        for (i = 10; i > j; i--) {
            scorehigh[i + 1] = scorehigh[i];
            scoreinit[i] = scoreinit[i - 1];
        }
        scorehigh[j + 1] = scoret;
        scoreinit[j] = scoreinit[0];
    }

    void writecurscore(int bp6) {
        if (dig.main.getcplayer() == 0)
            writenum(score1, 0, 0, 6, bp6);
        else if (score2 < 100000l)
            writenum(score2, 236, 0, 6, bp6);
        else
            writenum(score2, 248, 0, 6, bp6);
    }

    void writenum(long n, int x, int y, int w, int c) {
        int d, xp = (w - 1) * 12 + x;
        while (w > 0) {
            d = (int) (n % 10);
            if (w > 1 || d > 0)
                dig.pc.gwrite(xp, y, d + '0', c, false);    //true
            n /= 10;
            w--;
            xp -= 12;
        }
    }

    void zeroscores() {
        score2 = 0;
        score1 = 0;
        scoret = 0;
        nextbs1 = bonusscore;
        nextbs2 = bonusscore;
    }

    @Override
    public void run() {
    }
}