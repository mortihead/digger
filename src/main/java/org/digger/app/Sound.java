package org.digger.app;

/**
 * PC Speaker sound emulation for Digger.
 * <p>
 * Originally driven by Intel 8253 timer hardware and IRQ0 (Int 8) interrupts.
 * Now uses SoundEngine to generate real-time square wave audio via Java Sound API.
 * <p>
 * Timer divisor → frequency: freq = 1193180 / divisor (Hz)
 * 0x7d00 (32000) → ~37 Hz  (sub-audible, used as "silence" marker)
 * 0x8e8  (2280)  → ~523 Hz (C5)
 * 0x11d1 (4561)  → ~262 Hz (C4)
 */
class Sound {

    private static final int SILENCE_T0VAL = 0x7d00;
    private static final int DEFAULT_T2VAL = 40;

    private final Digger dig;
    private SoundEngine engine;

    int wavetype = 0, t2val = 0, t0val = 0, musvol = 0;
    int spkrmode = 0;
    int pulsewidth = 1;
    int volume = 0;

    int timerClock = 0;

    boolean soundflag = true, musicflag = true;
    boolean sndflag = false, soundpausedflag = false;

    // --- Level done jingle ---
    boolean soundlevdoneflag = false;
    int nljpointer = 0, nljnoteduration = 0;
    int[] newlevjingle = {0x8e8, 0x712, 0x5f2, 0x7f0, 0x6ac, 0x54c, 0x712, 0x5f2, 0x4b8, 0x474, 0x474};

    // --- Fall sound ---
    boolean soundfallflag = false, soundfallf = false;
    int soundfallvalue, soundfalln = 0;

    // --- Break sound ---
    boolean soundbreakflag = false;
    int soundbreakduration = 0, soundbreakvalue = 0;

    // --- Wobble sound ---
    boolean soundwobbleflag = false;
    int soundwobblen = 0;

    // --- Fire sound ---
    boolean soundfireflag = false;
    int soundfirevalue, soundfiren = 0;

    // --- Explode sound ---
    boolean soundexplodeflag = false;
    int soundexplodevalue, soundexplodeduration;

    // --- Bonus sound ---
    boolean soundbonusflag = false;
    int soundbonusn = 0;

    // --- Emerald monster sound ---
    boolean soundemflag = false;

    // --- Emerald collect sound ---
    boolean soundemeraldflag = false;
    int soundemeraldduration, emerfreq, soundemeraldn;

    // --- Gold collect sound ---
    boolean soundgoldflag = false, soundgoldf = false;
    int soundgoldvalue1, soundgoldvalue2, soundgoldduration;

    // --- Eat monster sound ---
    boolean soundeatmflag = false;
    int soundeatmvalue, soundeatmduration, soundeatmn;

    // --- Digger death sound ---
    boolean soundddieflag = false;
    int soundddien, soundddievalue;

    // --- 1-Up sound ---
    boolean sound1upflag = false;
    int sound1upduration = 0;

    // --- Music state ---
    boolean musicplaying = false;
    int musicp = 0, tuneno = 0, noteduration = 0, notevalue = 0;
    int musicmaxvol = 0, musicattackrate = 0, musicsustainlevel = 0;
    int musicdecayrate = 0, musicnotewidth = 0, musicreleaserate = 0;
    int musicstage = 0, musicn = 0;

    // --- Music jingles (format: [freq1, dur1, freq2, dur2, ...], terminated by 0x7d64) ---
    int[] bonusjingle = {
        0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2,
        0xd59, 4, 0xbe4, 4, 0xa98, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2,
        0x11d1, 4, 0xd59, 2, 0xa98, 2, 0xbe4, 4, 0xe24, 4, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2,
        0x11d1, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2, 0xd59, 4, 0xbe4, 4,
        0xa98, 4, 0xd59, 2, 0xa98, 2, 0x8e8, 10, 0xa00, 2, 0xa98, 2, 0xbe4, 2, 0xd59, 4,
        0xa98, 4, 0xd59, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4,
        0x11d1, 2, 0x11d1, 2, 0xd59, 4, 0xbe4, 4, 0xa98, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4,
        0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0xd59, 2, 0xa98, 2, 0xbe4, 4, 0xe24, 4, 0x11d1, 4,
        0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2, 0x11d1, 4, 0x11d1, 2, 0x11d1, 2,
        0xd59, 4, 0xbe4, 4, 0xa98, 4, 0xd59, 2, 0xa98, 2, 0x8e8, 10, 0xa00, 2, 0xa98, 2,
        0xbe4, 2, 0xd59, 4, 0xa98, 4, 0xd59, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4, 0xa98, 2,
        0xa98, 2, 0xa98, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4, 0x7f0, 4, 0xa98, 4, 0x7f0, 4,
        0xa98, 4, 0x7f0, 4, 0xa98, 4, 0xbe4, 4, 0xd59, 4, 0xe24, 4, 0xfdf, 4, 0xa98, 2,
        0xa98, 2, 0xa98, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4,
        0x7f0, 4, 0xa98, 4, 0x7f0, 4, 0xa98, 4, 0x7f0, 4, 0x8e8, 4, 0x970, 4, 0x8e8, 4,
        0x970, 4, 0x8e8, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4,
        0xa98, 2, 0xa98, 2, 0xa98, 4, 0x7f0, 4, 0xa98, 4, 0x7f0, 4, 0xa98, 4, 0x7f0, 4,
        0xa98, 4, 0xbe4, 4, 0xd59, 4, 0xe24, 4, 0xfdf, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4,
        0xa98, 2, 0xa98, 2, 0xa98, 4, 0xa98, 2, 0xa98, 2, 0xa98, 4, 0x7f0, 4, 0xa98, 4,
        0x7f0, 4, 0xa98, 4, 0x7f0, 4, 0x8e8, 4, 0x970, 4, 0x8e8, 4, 0x970, 4, 0x8e8, 4,
        0x7d64
    };

    int[] backgjingle = {
        0xfdf, 2, 0x11d1, 2, 0xfdf, 2, 0x1530, 2, 0x1ab2, 2, 0x1530, 2, 0x1fbf, 4, 0xfdf, 2,
        0x11d1, 2, 0xfdf, 2, 0x1530, 2, 0x1ab2, 2, 0x1530, 2, 0x1fbf, 4, 0xfdf, 2, 0xe24, 2,
        0xd59, 2, 0xe24, 2, 0xd59, 2, 0xfdf, 2, 0xe24, 2, 0xfdf, 2, 0xe24, 2, 0x11d1, 2,
        0xfdf, 2, 0x11d1, 2, 0xfdf, 2, 0x1400, 2, 0xfdf, 4, 0xfdf, 2, 0x11d1, 2, 0xfdf, 2,
        0x1530, 2, 0x1ab2, 2, 0x1530, 2, 0x1fbf, 4, 0xfdf, 2, 0x11d1, 2, 0xfdf, 2, 0x1530, 2,
        0x1ab2, 2, 0x1530, 2, 0x1fbf, 4, 0xfdf, 2, 0xe24, 2, 0xd59, 2, 0xe24, 2, 0xd59, 2,
        0xfdf, 2, 0xe24, 2, 0xfdf, 2, 0xe24, 2, 0x11d1, 2, 0xfdf, 2, 0x11d1, 2, 0xfdf, 2,
        0xe24, 2, 0xd59, 4, 0xa98, 2, 0xbe4, 2, 0xa98, 2, 0xd59, 2, 0x11d1, 2, 0xd59, 2,
        0x1530, 4, 0xa98, 2, 0xbe4, 2, 0xa98, 2, 0xd59, 2, 0x11d1, 2, 0xd59, 2, 0x1530, 4,
        0xa98, 2, 0x970, 2, 0x8e8, 2, 0x970, 2, 0x8e8, 2, 0xa98, 2, 0x970, 2, 0xa98, 2,
        0x970, 2, 0xbe4, 2, 0xa98, 2, 0xbe4, 2, 0xa98, 2, 0xd59, 2, 0xa98, 4, 0xa98, 2,
        0xbe4, 2, 0xa98, 2, 0xd59, 2, 0x11d1, 2, 0xd59, 2, 0x1530, 4, 0xa98, 2, 0xbe4, 2,
        0xa98, 2, 0xd59, 2, 0x11d1, 2, 0xd59, 2, 0x1530, 4, 0xa98, 2, 0x970, 2, 0x8e8, 2,
        0x970, 2, 0x8e8, 2, 0xa98, 2, 0x970, 2, 0xa98, 2, 0x970, 2, 0xbe4, 2, 0xa98, 2,
        0xbe4, 2, 0xa98, 2, 0xd59, 2, 0xa98, 4, 0x7f0, 2, 0x8e8, 2, 0xa98, 2, 0xd59, 2,
        0x11d1, 2, 0xd59, 2, 0x1530, 4, 0xa98, 2, 0xbe4, 2, 0xa98, 2, 0xd59, 2, 0x11d1, 2,
        0xd59, 2, 0x1530, 4, 0xa98, 2, 0x970, 2, 0x8e8, 2, 0x970, 2, 0x8e8, 2, 0xa98, 2,
        0x970, 2, 0xa98, 2, 0x970, 2, 0xbe4, 2, 0xa98, 2, 0xbe4, 2, 0xd59, 2, 0xbe4, 2,
        0xa98, 4, 0x7d64
    };

    int[] dirge = {
        0x7d00, 2, 0x11d1, 6, 0x11d1, 4, 0x11d1, 2, 0x11d1, 6, 0xefb, 4, 0xfdf, 2,
        0xfdf, 4, 0x11d1, 2, 0x11d1, 4, 0x12e0, 2, 0x11d1, 12, 0x7d00, 16, 0x7d00, 16,
        0x7d00, 16, 0x7d00, 16, 0x7d00, 16, 0x7d00, 16, 0x7d00, 16, 0x7d00, 16,
        0x7d00, 16, 0x7d00, 16, 0x7d00, 16, 0x7d64
    };

    boolean soundt0flag = false;
    boolean int8flag = false;

    Sound(Digger d) {
        dig = d;
    }

    // --- Hardware emulation bridges ---

    private void pushToEngine() {
        if (engine != null) {
            engine.updateT0Val(t0val);
            engine.updateT2Val(t2val);
            engine.updateSpkrMode(spkrmode);
            engine.updatePulseWidth(pulsewidth);
        }
    }

    // --- Initialization / shutdown ---

    void initSound() {
        wavetype = 2;
        t0val = 12000;
        musvol = 8;
        t2val = 40;
        soundt0flag = true;
        sndflag = true;
        spkrmode = 0;
        int8flag = false;
        musicplaying = false;
        setSoundT2();
        soundStop();
        startInt8();

        // Start the Java Sound engine
        engine = new SoundEngine(this);
        boolean ok = engine.start();
        if (!ok) {
            System.err.println("Sound: failed to initialize audio output");
            engine = null;
        }
    }

    void killSound() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        stopInt8();
    }

    // --- Music ---

    void music(int tune) {
        tuneno = tune;
        musicp = 0;
        noteduration = 0;
        switch (tune) {
            case 0:
                musicmaxvol = 50;
                musicattackrate = 20;
                musicsustainlevel = 20;
                musicdecayrate = 10;
                musicreleaserate = 4;
                break;
            case 1:
                musicmaxvol = 50;
                musicattackrate = 50;
                musicsustainlevel = 8;
                musicdecayrate = 15;
                musicreleaserate = 1;
                break;
            case 2:
                musicmaxvol = 50;
                musicattackrate = 50;
                musicsustainlevel = 25;
                musicdecayrate = 5;
                musicreleaserate = 1;
                break;
        }
        musicplaying = true;
        if (tune == 2)
            soundDdieOff();
    }

    void musicOff() {
        musicplaying = false;
        musicp = 0;
    }

    void musicUpdate() {
        if (!musicplaying)
            return;
        if (noteduration != 0) {
            noteduration--;
        } else {
            musicstage = musicn = 0;
            switch (tuneno) {
                case 0:
                    noteduration = bonusjingle[musicp + 1] * 3;
                    musicnotewidth = noteduration - 3;
                    notevalue = bonusjingle[musicp];
                    musicp += 2;
                    if (bonusjingle[musicp] == 0x7d64)
                        musicp = 0;
                    break;
                case 1:
                    noteduration = backgjingle[musicp + 1] * 6;
                    musicnotewidth = 12;
                    notevalue = backgjingle[musicp];
                    musicp += 2;
                    if (backgjingle[musicp] == 0x7d64)
                        musicp = 0;
                    break;
                case 2:
                    noteduration = dirge[musicp + 1] * 10;
                    musicnotewidth = noteduration - 10;
                    notevalue = dirge[musicp];
                    musicp += 2;
                    if (dirge[musicp] == 0x7d64)
                        musicp = 0;
                    break;
            }
        }
        musicn++;
        wavetype = 1;
        t0val = notevalue;
        if (musicn >= musicnotewidth)
            musicstage = 2;
        switch (musicstage) {
            case 0:
                if (musvol + musicattackrate >= musicmaxvol) {
                    musicstage = 1;
                    musvol = musicmaxvol;
                    break;
                }
                musvol += musicattackrate;
                break;
            case 1:
                if (musvol - musicdecayrate <= musicsustainlevel) {
                    musvol = musicsustainlevel;
                    break;
                }
                musvol -= musicdecayrate;
                break;
            case 2:
                if (musvol - musicreleaserate <= 1) {
                    musvol = 1;
                    break;
                }
                musvol -= musicreleaserate;
        }
        if (musvol == 1)
            t0val = SILENCE_T0VAL;
    }

    // --- Sound mode / timer setup ---

    void setSoundMode() {
        spkrmode = wavetype;
        if (!soundt0flag && sndflag) {
            soundt0flag = true;
        }
    }

    void setSoundT2() {
        if (soundt0flag) {
            spkrmode = 0;
            soundt0flag = false;
        }
    }

    void setT0() {
        if (sndflag) {
            if (t0val < 1000 && (wavetype == 1 || wavetype == 2))
                t0val = 1000;
            if (musvol < 1) musvol = 1;
            if (musvol > 50) musvol = 50;
            pulsewidth = musvol * volume;
            setSoundMode();
        }
    }

    void setT2Val(int t2v) {
        t2val = t2v;
    }

    void setupSound() {
        // Re-initialize sound engine after killSound()
        // (called from Scores after game over / high score entry)
        if (engine == null) {
            sndflag = true;
            startInt8();
            engine = new SoundEngine(this);
            boolean ok = engine.start();
            if (!ok) {
                System.err.println("Sound: failed to reinitialize audio output");
                engine = null;
            }
        }
    }

    // --- Sound effects: 1-Up ---

    void sound1Up() {
        sound1upduration = 96;
        sound1upflag = true;
    }

    void sound1UpOff() {
        sound1upflag = false;
    }

    void sound1UpUpdate() {
        if (sound1upflag) {
            if ((sound1upduration / 3) % 2 != 0)
                t2val = (sound1upduration << 2) + 600;
            sound1upduration--;
            if (sound1upduration < 1)
                sound1upflag = false;
        }
    }

    // --- Sound effects: Bonus ---

    void soundBonus() {
        soundbonusflag = true;
    }

    void soundBonusOff() {
        soundbonusflag = false;
        soundbonusn = 0;
    }

    void soundBonusUpdate() {
        if (soundbonusflag) {
            soundbonusn++;
            if (soundbonusn > 15)
                soundbonusn = 0;
            if (soundbonusn >= 0 && soundbonusn < 6)
                t2val = 0x4ce;
            if (soundbonusn >= 8 && soundbonusn < 14)
                t2val = 0x5e9;
        }
    }

    // --- Sound effects: Break ---

    void soundBreak() {
        soundbreakduration = 3;
        if (soundbreakvalue < 15000)
            soundbreakvalue = 15000;
        soundbreakflag = true;
    }

    void soundBreakOff() {
        soundbreakflag = false;
    }

    void soundBreakUpdate() {
        if (soundbreakflag)
            if (soundbreakduration != 0) {
                soundbreakduration--;
                t2val = soundbreakvalue;
            } else
                soundbreakflag = false;
    }

    // --- Sound effects: Digger death ---

    void soundDdie() {
        soundddien = 0;
        soundddievalue = 20000;
        soundddieflag = true;
    }

    void soundDdieOff() {
        soundddieflag = false;
    }

    void soundDdieUpdate() {
        if (soundddieflag) {
            soundddien++;
            if (soundddien == 1)
                musicOff();
            if (soundddien >= 1 && soundddien <= 10)
                soundddievalue = 20000 - soundddien * 1000;
            if (soundddien > 10)
                soundddievalue += 500;
            if (soundddievalue > 30000)
                soundDdieOff();
            t2val = soundddievalue;
        }
    }

    // --- Sound effects: Eat monster ---

    void soundEatm() {
        soundeatmduration = 20;
        soundeatmn = 3;
        soundeatmvalue = 2000;
        soundeatmflag = true;
    }

    void soundEatmOff() {
        soundeatmflag = false;
    }

    void soundEatmUpdate() {
        if (soundeatmflag)
            if (soundeatmn != 0) {
                if (soundeatmduration != 0) {
                    if ((soundeatmduration % 4) == 1)
                        t2val = soundeatmvalue;
                    if ((soundeatmduration % 4) == 3)
                        t2val = soundeatmvalue - (soundeatmvalue >> 4);
                    soundeatmduration--;
                    soundeatmvalue -= (soundeatmvalue >> 4);
                } else {
                    soundeatmduration = 20;
                    soundeatmn--;
                    soundeatmvalue = 2000;
                }
            } else
                soundeatmflag = false;
    }

    // --- Sound effects: Emerald monster ---

    void soundEm() {
        soundemflag = true;
    }

    void soundEmerald(int emocttime) {
        if (emocttime != 0) {
            switch (emerfreq) {
                case 0x8e8:
                    emerfreq = 0x7f0;
                    break;
                case 0x7f0:
                    emerfreq = 0x712;
                    break;
                case 0x712:
                    emerfreq = 0x6ac;
                    break;
                case 0x6ac:
                    emerfreq = 0x5f2;
                    break;
                case 0x5f2:
                    emerfreq = 0x54c;
                    break;
                case 0x54c:
                    emerfreq = 0x4b8;
                    break;
                case 0x4b8:
                    emerfreq = 0x474;
                    dig.scores.scoreOctave();
                    break;
                case 0x474:
                    emerfreq = 0x8e8;
                    break;
            }
        } else {
            emerfreq = 0x8e8;
        }
        soundemeraldduration = 7;
        soundemeraldn = 0;
        soundemeraldflag = true;
    }

    void soundEmeraldOff() {
        soundemeraldflag = false;
    }

    void soundEmeraldUpdate() {
        if (soundemeraldflag)
            if (soundemeraldduration != 0) {
                if (soundemeraldn == 0 || soundemeraldn == 1)
                    t2val = emerfreq;
                soundemeraldn++;
                if (soundemeraldn > 7) {
                    soundemeraldn = 0;
                    soundemeraldduration--;
                }
            } else
                soundEmeraldOff();
    }

    void soundEmOff() {
        soundemflag = false;
    }

    void soundEmUpdate() {
        if (soundemflag) {
            t2val = 1000;
            soundEmOff();
        }
    }

    // --- Sound effects: Explode ---

    void soundExplode() {
        soundexplodevalue = 1500;
        soundexplodeduration = 10;
        soundexplodeflag = true;
        soundFireOff();
    }

    void soundExplodeOff() {
        soundexplodeflag = false;
    }

    void soundExplodeUpdate() {
        if (soundexplodeflag)
            if (soundexplodeduration != 0) {
                soundexplodevalue = t2val = soundexplodevalue - (soundexplodevalue >> 3);
                soundexplodeduration--;
            } else
                soundexplodeflag = false;
    }

    // --- Sound effects: Fall ---

    void soundFall() {
        soundfallvalue = 1000;
        soundfallflag = true;
    }

    void soundFallOff() {
        soundfallflag = false;
        soundfalln = 0;
    }

    void soundFallUpdate() {
        if (soundfallflag)
            if (soundfalln < 1) {
                soundfalln++;
                if (soundfallf)
                    t2val = soundfallvalue;
            } else {
                soundfalln = 0;
                if (soundfallf) {
                    soundfallvalue += 50;
                    soundfallf = false;
                } else
                    soundfallf = true;
            }
    }

    // --- Sound effects: Fire ---

    void soundFire() {
        soundfirevalue = 500;
        soundfireflag = true;
    }

    void soundFireOff() {
        soundfireflag = false;
        soundfiren = 0;
    }

    void soundFireUpdate() {
        if (soundfireflag) {
            if (soundfiren == 1) {
                soundfiren = 0;
                soundfirevalue += soundfirevalue / 55;
                t2val = soundfirevalue + dig.main.randomNumber(soundfirevalue >> 3);
                if (soundfirevalue > 30000)
                    soundFireOff();
            } else
                soundfiren++;
        }
    }

    // --- Sound effects: Gold ---

    void soundGold() {
        soundgoldvalue1 = 500;
        soundgoldvalue2 = 4000;
        soundgoldduration = 30;
        soundgoldf = false;
        soundgoldflag = true;
    }

    void soundGoldOff() {
        soundgoldflag = false;
    }

    void soundGoldUpdate() {
        if (soundgoldflag) {
            if (soundgoldduration != 0)
                soundgoldduration--;
            else
                soundgoldflag = false;
            if (soundgoldf) {
                soundgoldf = false;
                t2val = soundgoldvalue1;
            } else {
                soundgoldf = true;
                t2val = soundgoldvalue2;
            }
            soundgoldvalue1 += (soundgoldvalue1 >> 4);
            soundgoldvalue2 -= (soundgoldvalue2 >> 4);
        }
    }

    // --- Main sound interrupt handler (replaces hardware Int 8) ---

    void soundInt() {
        if (soundlevdoneflag) {
            // soundLevDone() drives timerclock and audio itself
            return;
        }
        timerClock++;
        if (soundflag && !sndflag)
            sndflag = true;
        if (!soundflag && sndflag) {
            sndflag = false;
            setSoundT2();
            // Silence the engine immediately when all sound is toggled off
            if (engine != null) {
                engine.updateT0Val(SILENCE_T0VAL);
                engine.updateT2Val(DEFAULT_T2VAL);
                engine.updateSpkrMode(0);
            }
        }
        if (!musicflag && musicplaying) {
            musicOff();
        }
        if (sndflag && !soundpausedflag) {
            t0val = SILENCE_T0VAL;
            t2val = DEFAULT_T2VAL;
            if (musicflag)
                musicUpdate();
            soundEmeraldUpdate();
            soundWobbleUpdate();
            soundDdieUpdate();
            soundBreakUpdate();
            soundGoldUpdate();
            soundEmUpdate();
            soundExplodeUpdate();
            soundFireUpdate();
            soundEatmUpdate();
            soundFallUpdate();
            sound1UpUpdate();
            soundBonusUpdate();
            if (t0val == SILENCE_T0VAL || t2val != DEFAULT_T2VAL)
                setSoundT2();
            else {
                setSoundMode();
                setT0();
            }
            setT2Val(t2val);
            pushToEngine();
        }
    }

    // --- Level done ---

    void soundLevDone() {
        soundStop();
        nljpointer = 0;
        nljnoteduration = 20;
        soundlevdoneflag = true;
        while (soundlevdoneflag) {
            // timerclock is incremented by soundInt() in the engine thread;
            // drive it ourselves since soundInt() skips updates while
            // soundlevdoneflag is true
            int prev = timerClock;
            timerClock++;
            soundLevDoneUpdate();
            // Small sleep to match original timing (~14ms per tick at 73Hz)
            try {
                Thread.sleep(14);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    void soundLevDoneOff() {
        soundlevdoneflag = false;
    }

    void soundLevDoneUpdate() {
        if (sndflag) {
            if (nljpointer < 11)
                t2val = newlevjingle[nljpointer];
            t0val = t2val + 35;
            musvol = 50;
            setSoundMode();
            setT0();
            setT2Val(t2val);
            pushToEngine();
            if (nljnoteduration > 0)
                nljnoteduration--;
            else {
                nljnoteduration = 20;
                nljpointer++;
                if (nljpointer > 10)
                    soundLevDoneOff();
            }
        } else {
            soundlevdoneflag = false;
        }
    }

    // --- Pause ---

    void soundPause() {
        soundpausedflag = true;
        // Silence the engine immediately so the current tone doesn't hang
        if (engine != null) {
            engine.updateT0Val(SILENCE_T0VAL);
            engine.updateT2Val(DEFAULT_T2VAL);
            engine.updateSpkrMode(0);
        }
    }

    void soundPauseOff() {
        soundpausedflag = false;
    }

    // --- Stop all sounds ---

    void soundStop() {
        soundFallOff();
        soundWobbleOff();
        soundFireOff();
        musicOff();
        soundBonusOff();
        soundExplodeOff();
        soundBreakOff();
        soundEmOff();
        soundEmeraldOff();
        soundGoldOff();
        soundEatmOff();
        soundDdieOff();
        sound1UpOff();
    }

    // --- Sound effects: Wobble ---

    void soundWobble() {
        soundwobbleflag = true;
    }

    void soundWobbleOff() {
        soundwobbleflag = false;
        soundwobblen = 0;
    }

    void soundWobbleUpdate() {
        if (soundwobbleflag) {
            soundwobblen++;
            if (soundwobblen > 63)
                soundwobblen = 0;
            switch (soundwobblen) {
                case 0:
                    t2val = 0x7d0;
                    break;
                case 16:
                case 48:
                    t2val = 0x9c4;
                    break;
                case 32:
                    t2val = 0xbb8;
                    break;
            }
        }
    }

    // --- Int 8 emulation (timer interrupt) ---

    void startInt8() {
        if (!int8flag) {
            int8flag = true;
        }
    }

    void stopInt8() {
        if (int8flag) {
            int8flag = false;
        }
        setT2Val(DEFAULT_T2VAL);
    }
}
