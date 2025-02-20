/* WARNING! This code is ugly and highly non-object-oriented.
It was ported from C almost mechanically! */
package org.digger.app;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Digger extends Frame implements Runnable {
    private static final String INI_FILE = "digger.ini";
    private final Map<String, String> parameters;
    public boolean running; // Флаг для контроля

    static int MAX_RATE = 200, MIN_RATE = 40;

    int width = 320, height = 200, frametime = 66;
    int scaleFactor = 2;
    int top_indent = 30;
    Thread gamethread;

    Bags bags;
    Main main;
    Sound sound;
    Monster monster;
    Scores scores;
    Sprite sprite;
    Drawing drawing;
    Input input;
    Pc pc;

// -----

    int diggerx = 0, diggery = 0, diggerh = 0, diggerv = 0, diggerrx = 0, diggerry = 0, digmdir = 0,
            digdir = 0, digtime = 0, rechargetime = 0, firex = 0, firey = 0, firedir = 0, expsn = 0,
            deathstage = 0, deathbag = 0, deathani = 0, deathtime = 0, startbonustimeleft = 0,
            bonustimeleft = 0, eatmsc = 0, emocttime = 0;

    int emmask = 0;

    byte emfield[] = {    //[150]
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    boolean digonscr = false, notfiring = false, bonusvisible = false, bonusmode = false, diggervisible = false;

    long time, ftime = 50;
    int embox[] = {8, 12, 12, 9, 16, 12, 6, 9};    // [8]
    int deatharc[] = {3, 5, 6, 6, 5, 3, 0};            // [7]

    public Digger() throws IOException {
        setTitle("Digger Game");
        setLocationRelativeTo(null);

        this.parameters = new HashMap<>();
        loadParameters(INI_FILE);
        scaleFactor = Integer.parseInt(getParameter("scale_factor", "2"));
        setSize(width*scaleFactor, height*scaleFactor+top_indent);

        bags = new Bags(this);
        main = new Main(this);
        sound = new Sound(this);
        monster = new Monster(this);
        scores = new Scores(this);
        sprite = new Sprite(this);
        drawing = new Drawing(this);
        input = new Input(this);
        pc = new Pc(this);
        init();

        setVisible(true);
        requestFocus();
        running = true; // Устанавливаем флаг выполнения
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                running = false; // Устанавливаем флаг для завершения операции
                dispose(); // Закрываем окно
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Обработка нажатия клавиши
                keyDownProcess(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyUpProcess(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // Обработка нажатия клавиши (с учетом символа)

            }
        });
    }



    public static void main(String[] args) {
        // Запуск приложения
        javax.swing.SwingUtilities.invokeLater(() -> {
            Digger app = null;
            try {
                app = new Digger();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            app.setVisible(true);
        });
    }

    private void loadParameters(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Пропускаем пустые строки и комментарии
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                    continue;
                }
                // Разделяем строку на ключ и значение
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    parameters.put(key, value);
                }
            }
        }
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    private String getParameter(String param, String defaultValue) {
        String res = getParameter(param);
        return (Objects.isNull(param) ? defaultValue : res);
    }


    boolean checkdiggerunderbag(int h, int v) {
        if (digmdir == 2 || digmdir == 6)
            if ((diggerx - 12) / 20 == h)
                if ((diggery - 18) / 18 == v || (diggery - 18) / 18 + 1 == v)
                    return true;
        return false;
    }

    int countem() {
        int x, y, n = 0;
        for (x = 0; x < 15; x++)
            for (y = 0; y < 10; y++)
                if ((emfield[y * 15 + x] & emmask) != 0)
                    n++;
        return n;
    }

    void createbonus() {
        bonusvisible = true;
        drawing.drawbonus(292, 18);
    }

    public void destroy() {
        if (gamethread != null)
            gamethread.stop();
    }

    void diggerdie() {
        int clbits;
        switch (deathstage) {
            case 1:
                if (bags.bagy(deathbag) + 6 > diggery)
                    diggery = bags.bagy(deathbag) + 6;
                drawing.drawdigger(15, diggerx, diggery, false);
                main.incpenalty();
                if (bags.getbagdir(deathbag) + 1 == 0) {
                    sound.soundddie();
                    deathtime = 5;
                    deathstage = 2;
                    deathani = 0;
                    diggery -= 6;
                }
                break;
            case 2:
                if (deathtime != 0) {
                    deathtime--;
                    break;
                }
                if (deathani == 0)
                    sound.music(2);
                clbits = drawing.drawdigger(14 - deathani, diggerx, diggery, false);
                main.incpenalty();
                if (deathani == 0 && ((clbits & 0x3f00) != 0))
                    monster.killmonsters(clbits);
                if (deathani < 4) {
                    deathani++;
                    deathtime = 2;
                } else {
                    deathstage = 4;
                    if (sound.musicflag)
                        deathtime = 60;
                    else
                        deathtime = 10;
                }
                break;
            case 3:
                deathstage = 5;
                deathani = 0;
                deathtime = 0;
                break;
            case 5:
                if (deathani >= 0 && deathani <= 6) {
                    drawing.drawdigger(15, diggerx, diggery - deatharc[deathani], false);
                    if (deathani == 6)
                        sound.musicoff();
                    main.incpenalty();
                    deathani++;
                    if (deathani == 1)
                        sound.soundddie();
                    if (deathani == 7) {
                        deathtime = 5;
                        deathani = 0;
                        deathstage = 2;
                    }
                }
                break;
            case 4:
                if (deathtime != 0)
                    deathtime--;
                else
                    main.setdead(true);
        }
    }

    void dodigger() {
        newframe();
        if (expsn != 0)
            drawexplosion();
        else
            updatefire();
        if (diggervisible)
            if (digonscr)
                if (digtime != 0) {
                    drawing.drawdigger(digmdir, diggerx, diggery, notfiring && rechargetime == 0);
                    main.incpenalty();
                    digtime--;
                } else
                    updatedigger();
            else
                diggerdie();
        if (bonusmode && digonscr) {
            if (bonustimeleft != 0) {
                bonustimeleft--;
                if (startbonustimeleft != 0 || bonustimeleft < 20) {
                    startbonustimeleft--;
                    if ((bonustimeleft & 1) != 0) {
                        pc.ginten(0);
                        sound.soundbonus();
                    } else {
                        pc.ginten(1);
                        sound.soundbonus();
                    }
                    if (startbonustimeleft == 0) {
                        sound.music(0);
                        sound.soundbonusoff();
                        pc.ginten(1);
                    }
                }
            } else {
                endbonusmode();
                sound.soundbonusoff();
                sound.music(1);
            }
        }
        if (bonusmode && !digonscr) {
            endbonusmode();
            sound.soundbonusoff();
            sound.music(1);
        }
        if (emocttime > 0)
            emocttime--;
    }

    void drawemeralds() {
        int x, y;
        emmask = 1 << main.getcplayer();
        for (x = 0; x < 15; x++)
            for (y = 0; y < 10; y++)
                if ((emfield[y * 15 + x] & emmask) != 0)
                    drawing.drawemerald(x * 20 + 12, y * 18 + 21);
    }

    void drawexplosion() {
        switch (expsn) {
            case 1:
                sound.soundexplode();
            case 2:
            case 3:
                drawing.drawfire(firex, firey, expsn);
                main.incpenalty();
                expsn++;
                break;
            default:
                killfire();
                expsn = 0;
        }
    }

    void endbonusmode() {
        bonusmode = false;
        pc.ginten(0);
    }

    void erasebonus() {
        if (bonusvisible) {
            bonusvisible = false;
            sprite.erasespr(14);
        }
        pc.ginten(0);
    }

    void erasedigger() {
        sprite.erasespr(0);
        diggervisible = false;
    }

    boolean getfirepflag() {
        return input.firepflag;
    }

    boolean hitemerald(int x, int y, int rx, int ry, int dir) {
        boolean hit = false;
        int r;
        if (dir < 0 || dir > 6 || ((dir & 1) != 0))
            return hit;
        if (dir == 0 && rx != 0)
            x++;
        if (dir == 6 && ry != 0)
            y++;
        if (dir == 0 || dir == 4)
            r = rx;
        else
            r = ry;
        if ((emfield[y * 15 + x] & emmask) != 0) {
            if (r == embox[dir]) {
                drawing.drawemerald(x * 20 + 12, y * 18 + 21);
                main.incpenalty();
            }
            if (r == embox[dir + 1]) {
                drawing.eraseemerald(x * 20 + 12, y * 18 + 21);
                main.incpenalty();
                hit = true;
                emfield[y * 15 + x] &= ~emmask;
            }
        }
        return hit;
    }

    public void init() {

        if (gamethread != null)
            gamethread.stop();

        try {
            frametime = Integer.parseInt(getParameter("speed", "66"));
            if (frametime > MAX_RATE)
                frametime = MAX_RATE;
            else if (frametime < MIN_RATE)
                frametime = MIN_RATE;
        } catch (Exception e) {
        }

        pc.pixels = new int[65536];

        for (int i = 0; i < 2; i++) {
            pc.source[i] = new MemoryImageSource(pc.width, pc.height, new IndexColorModel(8, 4, pc.pal[i][0], pc.pal[i][1], pc.pal[i][2]), pc.pixels, 0, pc.width);
            pc.source[i].setAnimated(true);
            pc.image[i] = createImage(pc.source[i]);
            pc.source[i].newPixels();
        }

        pc.currentImage = pc.image[0];
        pc.currentSource = pc.source[0];

        gamethread = new Thread(this);
        gamethread.start();
    }

    void initbonusmode() {
        bonusmode = true;
        erasebonus();
        pc.ginten(1);
        bonustimeleft = 250 - main.levof10() * 20;
        startbonustimeleft = 20;
        eatmsc = 1;
    }

    void initdigger() {
        diggerv = 9;
        digmdir = 4;
        diggerh = 7;
        diggerx = diggerh * 20 + 12;
        digdir = 0;
        diggerrx = 0;
        diggerry = 0;
        digtime = 0;
        digonscr = true;
        deathstage = 1;
        diggervisible = true;
        diggery = diggerv * 18 + 18;
        sprite.movedrawspr(0, diggerx, diggery);
        notfiring = true;
        emocttime = 0;
        bonusvisible = bonusmode = false;
        input.firepressed = false;
        expsn = 0;
        rechargetime = 0;
    }

    public boolean keyDownProcess(KeyEvent e) {
        System.out.println("key pressed "+e.getKeyCode());
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                input.Key_uppressed();
                break;
            case KeyEvent.VK_DOWN:
                input.Key_downpressed();
                break;
            case KeyEvent.VK_LEFT:
                input.Key_leftpressed();
                break;
            case KeyEvent.VK_RIGHT:
                input.Key_rightpressed();
                break;
            case KeyEvent.VK_F1:
                input.Key_f1pressed();
                break;
            case KeyEvent.VK_F10:
                input.Key_f10pressed();
                break;

            default:
                input.processkey(e.getKeyCode());
                break;
        }
        return true;
    }

    public boolean keyUpProcess(KeyEvent e) {
        System.out.println("key up "+e.getKeyCode());
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                input.Key_upreleased();
                break;
            case KeyEvent.VK_DOWN:
                input.Key_downreleased();
                break;
            case KeyEvent.VK_LEFT:
                input.Key_leftreleased();
                break;
            case KeyEvent.VK_RIGHT:
                input.Key_rightreleased();
                break;
            case KeyEvent.VK_F1:
                input.Key_f1released();
                break;
            case KeyEvent.VK_F10:
                input.Key_f10released();
                break;

            default:
                     // input.processkey(e.getKeyCode());
                break;
        }
        return true;
    }

    void killdigger(int stage, int bag) {
        if (deathstage < 2 || deathstage > 4) {
            digonscr = false;
            deathstage = stage;
            deathbag = bag;
        }
    }

    void killemerald(int x, int y) {
        if ((emfield[y * 15 + x + 15] & emmask) != 0) {
            emfield[y * 15 + x + 15] &= ~emmask;
            drawing.eraseemerald(x * 20 + 12, (y + 1) * 18 + 21);
        }
    }

    void killfire() {
        if (!notfiring) {
            notfiring = true;
            sprite.erasespr(15);
            sound.soundfireoff();
        }
    }

    void makeemfield() {
        int x, y;
        emmask = 1 << main.getcplayer();
        for (x = 0; x < 15; x++)
            for (y = 0; y < 10; y++)
                if (main.getlevch(x, y, main.levplan()) == 'C')
                    emfield[y * 15 + x] |= emmask;
                else
                    emfield[y * 15 + x] &= ~emmask;
    }

    void newframe() {
        input.checkkeyb();
        time += frametime;
        long l = time - pc.gethrt();
        if (l > 0) {
            try {
                Thread.sleep((int) l);
            } catch (Exception e) {
            }
        }
        pc.currentSource.newPixels();
    }

    public void paint(Graphics g) {
        update(g);
    }

    int reversedir(int dir) {
        switch (dir) {
            case 0:
                return 4;
            case 4:
                return 0;
            case 2:
                return 6;
            case 6:
                return 2;
        }
        return dir;
    }

    public void run() {
        main.main();
    }

    // public void start() {
    //     requestFocus();
    //  }

    public void update(Graphics g) {
        if (!Objects.isNull(g)) {

            int originalWidth = pc.currentImage.getWidth(this);
            int originalHeight = pc.currentImage.getHeight(this);

            // Вычисляем новые размеры с учетом коэффициента масштабирования
            int newWidth = originalWidth * scaleFactor;
            int newHeight = originalHeight * scaleFactor;

            // Рисуем изображение с новыми размерами
            g.drawImage(pc.currentImage, 0, 32, newWidth, newHeight, this);



        }
    }

    void updatedigger() {
        int dir, ddir, clbits, diggerox, diggeroy, nmon;
        boolean push = false;
        input.readdir();
        dir = input.getdir();
        if (dir == 0 || dir == 2 || dir == 4 || dir == 6)
            ddir = dir;
        else
            ddir = -1;
        if (diggerrx == 0 && (ddir == 2 || ddir == 6))
            digdir = digmdir = ddir;
        if (diggerry == 0 && (ddir == 4 || ddir == 0))
            digdir = digmdir = ddir;
        if (dir == -1)
            digmdir = -1;
        else
            digmdir = digdir;
        if ((diggerx == 292 && digmdir == 0) || (diggerx == 12 && digmdir == 4) ||
                (diggery == 180 && digmdir == 6) || (diggery == 18 && digmdir == 2))
            digmdir = -1;
        diggerox = diggerx;
        diggeroy = diggery;
        if (digmdir != -1)
            drawing.eatfield(diggerox, diggeroy, digmdir);
        switch (digmdir) {
            case 0:
                drawing.drawrightblob(diggerx, diggery);
                diggerx += 4;
                break;
            case 4:
                drawing.drawleftblob(diggerx, diggery);
                diggerx -= 4;
                break;
            case 2:
                drawing.drawtopblob(diggerx, diggery);
                diggery -= 3;
                break;
            case 6:
                drawing.drawbottomblob(diggerx, diggery);
                diggery += 3;
                break;
        }
        if (hitemerald((diggerx - 12) / 20, (diggery - 18) / 18, (diggerx - 12) % 20,
                (diggery - 18) % 18, digmdir)) {
            scores.scoreemerald();
            sound.soundem();
            sound.soundemerald(emocttime);
            emocttime = 9;
        }
        clbits = drawing.drawdigger(digdir, diggerx, diggery, notfiring && rechargetime == 0);
        main.incpenalty();
        if ((bags.bagbits() & clbits) != 0) {
            if (digmdir == 0 || digmdir == 4) {
                push = bags.pushbags(digmdir, clbits);
                digtime++;
            } else if (!bags.pushudbags(clbits))
                push = false;
            if (!push) { /* Strange, push not completely defined */
                diggerx = diggerox;
                diggery = diggeroy;
                drawing.drawdigger(digmdir, diggerx, diggery, notfiring && rechargetime == 0);
                main.incpenalty();
                digdir = reversedir(digmdir);
            }
        }
        if (((clbits & 0x3f00) != 0) && bonusmode)
            for (nmon = monster.killmonsters(clbits); nmon != 0; nmon--) {
                sound.soundeatm();
                scores.scoreeatm();
            }
        if ((clbits & 0x4000) != 0) {
            scores.scorebonus();
            initbonusmode();
        }
        diggerh = (diggerx - 12) / 20;
        diggerrx = (diggerx - 12) % 20;
        diggerv = (diggery - 18) / 18;
        diggerry = (diggery - 18) % 18;
    }

    void updatefire() {
        int clbits, b, mon, pix = 0;
        if (notfiring) {
            if (rechargetime != 0)
                rechargetime--;
            else if (getfirepflag())
                if (digonscr) {
                    rechargetime = main.levof10() * 3 + 60;
                    notfiring = false;
                    switch (digdir) {
                        case 0:
                            firex = diggerx + 8;
                            firey = diggery + 4;
                            break;
                        case 4:
                            firex = diggerx;
                            firey = diggery + 4;
                            break;
                        case 2:
                            firex = diggerx + 4;
                            firey = diggery;
                            break;
                        case 6:
                            firex = diggerx + 4;
                            firey = diggery + 8;
                    }
                    firedir = digdir;
                    sprite.movedrawspr(15, firex, firey);
                    sound.soundfire();
                }
        } else {
            switch (firedir) {
                case 0:
                    firex += 8;
                    pix = pc.ggetpix(firex, firey + 4) | pc.ggetpix(firex + 4, firey + 4);
                    break;
                case 4:
                    firex -= 8;
                    pix = pc.ggetpix(firex, firey + 4) | pc.ggetpix(firex + 4, firey + 4);
                    break;
                case 2:
                    firey -= 7;
                    pix = (pc.ggetpix(firex + 4, firey) | pc.ggetpix(firex + 4, firey + 1) |
                            pc.ggetpix(firex + 4, firey + 2) | pc.ggetpix(firex + 4, firey + 3) |
                            pc.ggetpix(firex + 4, firey + 4) | pc.ggetpix(firex + 4, firey + 5) |
                            pc.ggetpix(firex + 4, firey + 6)) & 0xc0;
                    break;
                case 6:
                    firey += 7;
                    pix = (pc.ggetpix(firex, firey) | pc.ggetpix(firex, firey + 1) |
                            pc.ggetpix(firex, firey + 2) | pc.ggetpix(firex, firey + 3) |
                            pc.ggetpix(firex, firey + 4) | pc.ggetpix(firex, firey + 5) |
                            pc.ggetpix(firex, firey + 6)) & 3;
                    break;
            }
            clbits = drawing.drawfire(firex, firey, 0);
            main.incpenalty();
            if ((clbits & 0x3f00) != 0)
                for (mon = 0, b = 256; mon < 6; mon++, b <<= 1)
                    if ((clbits & b) != 0) {
                        monster.killmon(mon);
                        scores.scorekill();
                        expsn = 1;
                    }
            if ((clbits & 0x40fe) != 0)
                expsn = 1;
            switch (firedir) {
                case 0:
                    if (firex > 296)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firex -= 8;
                        drawing.drawfire(firex, firey, 0);
                    }
                    break;
                case 4:
                    if (firex < 16)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firex += 8;
                        drawing.drawfire(firex, firey, 0);
                    }
                    break;
                case 2:
                    if (firey < 15)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firey += 7;
                        drawing.drawfire(firex, firey, 0);
                    }
                    break;
                case 6:
                    if (firey > 183)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firey -= 7;
                        drawing.drawfire(firex, firey, 0);
                    }
            }
        }
    }
}