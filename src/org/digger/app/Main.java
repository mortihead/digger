package org.digger.app;


import java.awt.event.KeyEvent;

class Main {

    Digger dig;


    int digsprorder[] = {14, 13, 7, 6, 5, 4, 3, 2, 1, 12, 11, 10, 9, 8, 15, 0};    // [16]

    _game[] gamedat = {new _game(), new _game()};

    String pldispbuf = "";

    int curplayer = 0, nplayers = 0, penalty = 0;
    boolean levnotdrawn = false, flashplayer = false;

    int speedmul = 40;
    int delaytime = 0;
    int scaleFactor = 2;

    int randv;

    String leveldat[][] =        // [8][10][15]
            {{"S   B     HHHHS",
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

    void addlife(int pl) {
        gamedat[pl - 1].lives++;
        dig.sound.sound1up();
    }

    void calibrate() {
        dig.sound.volume = (int) (dig.pc.getkips() / 291);
        if (dig.sound.volume == 0)
            dig.sound.volume = 1;
    }

    void checklevdone() {
        if ((dig.countem() == 0 || dig.monster.monleft() == 0) && dig.digonscr)
            gamedat[curplayer].levdone = true;
        else
            gamedat[curplayer].levdone = false;
    }

    void cleartopline() {
        dig.drawing.outtext("                          ", 0, 0, 3);
        dig.drawing.outtext(" ", 308, 0, 3);
    }

    void drawscreen() {
        dig.drawing.creatembspr();
        dig.drawing.drawstatics();
        dig.bags.drawbags();
        dig.drawemeralds();
        dig.initdigger();
        dig.monster.initmonsters();
    }

    int getcplayer() {
        return curplayer;
    }

    int getlevch(int x, int y, int l) {
        if (l == 0)
            l++;
        return leveldat[l - 1][y].charAt(x);
    }

    int getlives(int pl) {
        return gamedat[pl - 1].lives;
    }

    void incpenalty() {
        penalty++;
    }

    void initchars() {
        dig.drawing.initmbspr();
        dig.initdigger();
        dig.monster.initmonsters();
    }

    void initlevel() {
        gamedat[curplayer].levdone = false;
        dig.drawing.makefield();
        dig.makeemfield();
        dig.bags.initbags();
        levnotdrawn = true;
    }

    int levno() {
        return gamedat[curplayer].level;
    }

    int levof10() {
        if (gamedat[curplayer].level > 10)
            return 10;
        return gamedat[curplayer].level;
    }

    int levplan() {
        int l = levno();
        if (l > 8)
            l = (l & 3) + 5; /* Level plan: 12345678, 678, (5678) 247 times, 5 forever */
        return l;
    }

    void main() {

        int frame, t, x = 0;
        boolean start;

        randv = (int) dig.pc.gethrt();
        calibrate();
//  parsecmd(argc,argv);
        dig.ftime = speedmul * 2000l;
        dig.sprite.setretr(false);
        dig.pc.ginit();
        dig.sprite.setretr(true);
        dig.pc.gpal(0);
        dig.input.initkeyb();
        dig.input.detectjoy();
        dig.scores.loadscores();
        dig.sound.initsound();

        dig.scores.run();        // ??
        dig.scores._updatescores(dig.scores.scores);

        nplayers = 1;
        do {
            dig.sound.soundstop();
            dig.sprite.setsprorder(digsprorder);
            dig.drawing.creatembspr();
            dig.input.detectjoy();
            dig.pc.gclear();
            dig.pc.gtitle();
            dig.drawing.outtext("D I G G E R", 100, 0, 3);
            shownplayers();
            dig.scores.showtable();
            start = false;
            frame = 0;

            dig.time = dig.pc.gethrt();

            while (!start) {
                start = dig.input.teststart();
                if (dig.input.keypressed != 0)
                    System.out.println("key=" + dig.input.keypressed);
                if (dig.input.keypressed == KeyEvent.VK_ESCAPE) {  //	esc
                    System.out.println("ESCAPE pressed");
                    switchnplayers();
                    shownplayers();
                    dig.input.akeypressed = 0;
                    dig.input.keypressed = 0;
                }
                if (frame == 0)
                    for (t = 54; t < 174; t += 12)
                        dig.drawing.outtext("            ", 164, t, 0);
                if (frame == 50) {
                    dig.sprite.movedrawspr(8, 292, 63);
                    x = 292;
                }
                if (frame > 50 && frame <= 77) {
                    x -= 4;
                    dig.drawing.drawmon(0, true, 4, x, 63);
                }
                if (frame > 77)
                    dig.drawing.drawmon(0, true, 0, 184, 63);
                if (frame == 83)
                    dig.drawing.outtext("NOBBIN", 216, 64, 2);
                if (frame == 90) {
                    dig.sprite.movedrawspr(9, 292, 82);
                    dig.drawing.drawmon(1, false, 4, 292, 82);
                    x = 292;
                }
                if (frame > 90 && frame <= 117) {
                    x -= 4;
                    dig.drawing.drawmon(1, false, 4, x, 82);
                }
                if (frame > 117)
                    dig.drawing.drawmon(1, false, 0, 184, 82);
                if (frame == 123)
                    dig.drawing.outtext("HOBBIN", 216, 83, 2);
                if (frame == 130) {
                    dig.sprite.movedrawspr(0, 292, 101);
                    dig.drawing.drawdigger(4, 292, 101, true);
                    x = 292;
                }
                if (frame > 130 && frame <= 157) {
                    x -= 4;
                    dig.drawing.drawdigger(4, x, 101, true);
                }
                if (frame > 157)
                    dig.drawing.drawdigger(0, 184, 101, true);
                if (frame == 163)
                    dig.drawing.outtext("DIGGER", 216, 102, 2);
                if (frame == 178) {
                    dig.sprite.movedrawspr(1, 184, 120);
                    dig.drawing.drawgold(1, 0, 184, 120);
                }
                if (frame == 183)
                    dig.drawing.outtext("GOLD", 216, 121, 2);
                if (frame == 198)
                    dig.drawing.drawemerald(184, 141);
                if (frame == 203)
                    dig.drawing.outtext("EMERALD", 216, 140, 2);
                if (frame == 218)
                    dig.drawing.drawbonus(184, 158);
                if (frame == 223)
                    dig.drawing.outtext("BONUS", 216, 159, 2);
                dig.newframe();
                frame++;
                if (frame > 250)
                    frame = 0;
                System.out.println(frame);
                if (!dig.running && frame > 1) {
                    System.out.println("Операция прервана.");
                    return; // Завершаем выполнение, если флаг установлен в false
                }
            }
            gamedat[0].level = 1;
            gamedat[0].lives = 3;
            if (nplayers == 2) {
                gamedat[1].level = 1;
                gamedat[1].lives = 3;
            } else
                gamedat[1].lives = 0;
            dig.pc.gclear();
            curplayer = 0;
            initlevel();
            curplayer = 1;
            initlevel();
            dig.scores.zeroscores();
            dig.bonusvisible = true;
            if (nplayers == 2)
                flashplayer = true;
            curplayer = 0;
//	if (dig.Input.escape)
//	  break;
//    if (recording)
//	  recputinit();
            while ((gamedat[0].lives != 0 || gamedat[1].lives != 0) && !dig.input.escape) {
                gamedat[curplayer].dead = false;
                while (!gamedat[curplayer].dead && gamedat[curplayer].lives != 0 && !dig.input.escape) {
                    dig.drawing.initmbspr();
                    play();
                }
                if (gamedat[1 - curplayer].lives != 0) {
                    curplayer = 1 - curplayer;
                    flashplayer = levnotdrawn = true;
                }
            }
            dig.input.escape = false;
        } while (!false); //dig.Input.escape);
/*  dig.Sound.soundoff();
  restoreint8();
  restorekeyb();
  graphicsoff(); */
    }

    void play() {
        int t, c;
/*  if (playing)
	randv=recgetrand();
  else
	randv=getlrt();
  if (recording)
	recputrand(randv); */
        if (levnotdrawn) {
            levnotdrawn = false;
            drawscreen();
            dig.time = dig.pc.gethrt();
            if (flashplayer) {
                flashplayer = false;
                pldispbuf = "PLAYER ";
                if (curplayer == 0)
                    pldispbuf += "1";
                else
                    pldispbuf += "2";
                cleartopline();
                for (t = 0; t < 15; t++)
                    for (c = 1; c <= 3; c++) {
                        dig.drawing.outtext(pldispbuf, 108, 0, c);
                        dig.scores.writecurscore(c);
                        /* olddelay(20); */
                        dig.newframe();
                        if (dig.input.escape)
                            return;
                    }
                dig.scores.drawscores();
                dig.scores.addscore(0);
            }
        } else
            initchars();
        dig.input.keypressed = 0;
        dig.drawing.outtext("        ", 108, 0, 3);
        dig.scores.initscores();
        dig.drawing.drawlives();
        dig.sound.music(1);
        dig.input.readdir();
        dig.time = dig.pc.gethrt();
        while (!gamedat[curplayer].dead && !gamedat[curplayer].levdone && !dig.input.escape) {
            penalty = 0;
            dig.dodigger();
            dig.monster.domonsters();
            dig.bags.dobags();
/*  if (penalty<8)
	  for (t=(8-penalty)*5;t>0;t--)
		olddelay(1); */
            if (penalty > 8)
                dig.monster.incmont(penalty - 8);
            testpause();
            checklevdone();
        }
        dig.erasedigger();
        dig.sound.musicoff();
        t = 20;
        while ((dig.bags.getnmovingbags() != 0 || t != 0) && !dig.input.escape) {
            if (t != 0)
                t--;
            penalty = 0;
            dig.bags.dobags();
            dig.dodigger();
            dig.monster.domonsters();
            if (penalty < 8)
/*    for (t=(8-penalty)*5;t>0;t--)
		 olddelay(1); */
                t = 0;
        }
        dig.sound.soundstop();
        dig.killfire();
        dig.erasebonus();
        dig.bags.cleanupbags();
        dig.drawing.savefield();
        dig.monster.erasemonsters();
        dig.newframe();        // needed by Java version!!
        if (gamedat[curplayer].levdone)
            dig.sound.soundlevdone();
        if (dig.countem() == 0) {
            gamedat[curplayer].level++;
            if (gamedat[curplayer].level > 1000)
                gamedat[curplayer].level = 1000;
            initlevel();
        }
        if (gamedat[curplayer].dead) {
            gamedat[curplayer].lives--;
            dig.drawing.drawlives();
            if (gamedat[curplayer].lives == 0 && !dig.input.escape)
                dig.scores.endofgame();
        }
        if (gamedat[curplayer].levdone) {
            gamedat[curplayer].level++;
            if (gamedat[curplayer].level > 1000)
                gamedat[curplayer].level = 1000;
            initlevel();
        }
    }

    int randno(int n) {
        randv = randv * 0x15a4e35 + 1;
        return (randv & 0x7fffffff) % n;
    }

    void setdead(boolean bp6) {
        gamedat[curplayer].dead = bp6;
    }

    void shownplayers() {
        if (nplayers == 1) {
            dig.drawing.outtext("ONE", 220, 25, 3);
            dig.drawing.outtext(" PLAYER ", 192, 39, 3);
        } else {
            dig.drawing.outtext("TWO", 220, 25, 3);
            dig.drawing.outtext(" PLAYERS", 184, 39, 3);
        }
    }

    void switchnplayers() {
        nplayers = 3 - nplayers;
    }

    void testpause() {
        if (dig.input.akeypressed == 32) { /* Space bar */
            dig.input.akeypressed = 0;
            dig.sound.soundpause();
            dig.sound.sett2val(40);
            dig.sound.setsoundt2();
            cleartopline();
            dig.drawing.outtext("PRESS ANY KEY", 80, 0, 1);
            dig.newframe();
            dig.input.keypressed = 0;
            while (true) {
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                }
                if (dig.input.keypressed != 0)
                    break;
            }
            cleartopline();
            dig.scores.drawscores();
            dig.scores.addscore(0);
            dig.drawing.drawlives();
            dig.newframe();
            dig.time = dig.pc.gethrt() - dig.frametime;
//	olddelay(200);
            dig.input.keypressed = 0;
        } else
            dig.sound.soundpauseoff();
    }
}