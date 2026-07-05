package org.digger.app;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Main game class: manages the game loop, digger logic, fire, bonus mode,
 * emerald field, and the AWT window with CGA display output.
 */
public class Digger extends Frame implements Runnable {

    private static final String INI_FILE = "digger.ini";

    private final Map<String, String> parameters;

    public boolean running;

    static int MAX_RATE = 200, MIN_RATE = 40;

    int width = 320, height = 200, frametime = 66;
    int scaleFactor = 2;
    Thread gamethread;

    Bags bags;
    Main main;
    Sound sound;
    Monster monster;
    Scores scores;
    Sprite sprite;
    Drawing drawing;
    Input input;
    CgaDisplay display;

    // ----- Digger state -----

    int diggerx = 0, diggery = 0, diggerh = 0, diggerv = 0, diggerrx = 0, diggerry = 0, digmdir = 0,
            digdir = 0, digtime = 0, rechargetime = 0, firex = 0, firey = 0, firedir = 0, expsn = 0,
            deathstage = 0, deathbag = 0, deathani = 0, deathtime = 0, startbonustimeleft = 0,
            bonustimeleft = 0, eatmsc = 0, emocttime = 0;

    int emmask = 0;

    byte[] emfield = new byte[150];

    boolean digonscr = false, notfiring = false, bonusvisible = false, bonusmode = false, diggervisible = false;

    long time, ftime = 50;

    /** Hit-box offsets for emerald collection per direction. */
    int[] embox = {8, 12, 12, 9, 16, 12, 6, 9};
    /** Arc heights for digger death animation (stage 5). */
    int[] deatharc = {3, 5, 6, 6, 5, 3, 0};

    public Digger() throws IOException {
        setTitle("Digger Game");
        this.parameters = new HashMap<>();
        loadParameters(INI_FILE);
        scaleFactor = Math.max(1, Integer.parseInt(getParameter("scale_factor", "2")));
        configureWindowBounds();

        bags = new Bags(this);
        main = new Main(this);
        sound = new Sound(this);
        monster = new Monster(this);
        scores = new Scores(this);
        sprite = new Sprite(this);
        drawing = new Drawing(this);
        input = new Input(this);
        display = new CgaDisplay(this);
        init();

        setVisible(true);
        configureWindowBounds();
        requestFocus();
        running = true;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                running = false;
                input.escape = true;
                if (sound != null)
                    sound.killSound();
                dispose();
                System.exit(0);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keyDownProcess(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyUpProcess(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }
        });
    }

    public static void main(String[] args) {
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
        File file = new File(filePath);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            if (!file.exists())
                System.err.println("Warning: Configuration file not found: " + filePath);
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#"))
                    continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    parameters.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    private String getParameter(String param, String defaultValue) {
        String res = getParameter(param);
        return (Objects.isNull(res) ? defaultValue : res);
    }

    boolean isDiggerUnderBag(int h, int v) {
        if (digmdir == 2 || digmdir == 6)
            if ((diggerx - 12) / 20 == h)
                if ((diggery - 18) / 18 == v || (diggery - 18) / 18 + 1 == v)
                    return true;
        return false;
    }

    int countEmeralds() {
        int n = 0;
        for (int x = 0; x < 15; x++)
            for (int y = 0; y < 10; y++)
                if ((emfield[y * 15 + x] & emmask) != 0)
                    n++;
        return n;
    }

    void createbonus() {
        bonusvisible = true;
        drawing.drawBonus(292, 18);
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
                drawing.drawDigger(15, diggerx, diggery, false);
                main.incrementPenalty();
                if (bags.getbagdir(deathbag) + 1 == 0) {
                    sound.soundDdie();
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
                clbits = drawing.drawDigger(14 - deathani, diggerx, diggery, false);
                main.incrementPenalty();
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
                    drawing.drawDigger(15, diggerx, diggery - deatharc[deathani], false);
                    if (deathani == 6)
                        sound.musicOff();
                    main.incrementPenalty();
                    deathani++;
                    if (deathani == 1)
                        sound.soundDdie();
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
                    main.setDead(true);
        }
    }

    void doDigger() {
        newFrame();
        if (expsn != 0)
            drawexplosion();
        else
            updatefire();
        if (diggervisible)
            if (digonscr)
                if (digtime != 0) {
                    drawing.drawDigger(digmdir, diggerx, diggery, notfiring && rechargetime == 0);
                    main.incrementPenalty();
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
                        display.setIntensity(1);
                        sound.soundBonus();
                    } else {
                        display.setIntensity(0);
                        sound.soundBonus();
                    }
                    if (startbonustimeleft == 0) {
                        sound.music(0);
                        sound.soundBonusOff();
                        display.setIntensity(0);
                    }
                }
            } else {
                endbonusmode();
                sound.soundBonusOff();
                sound.music(1);
            }
        }
        if (bonusmode && !digonscr) {
            endbonusmode();
            sound.soundBonusOff();
            sound.music(1);
        }
        if (emocttime > 0)
            emocttime--;
    }

    void drawEmeralds() {
        emmask = 1 << main.getCurrentPlayer();
        for (int x = 0; x < 15; x++)
            for (int y = 0; y < 10; y++)
                if ((emfield[y * 15 + x] & emmask) != 0)
                    drawing.drawEmerald(x * 20 + 12, y * 18 + 21);
    }

    void drawexplosion() {
        switch (expsn) {
            case 1:
                sound.soundExplode();
            case 2:
            case 3:
                drawing.drawFire(firex, firey, expsn);
                main.incrementPenalty();
                expsn++;
                break;
            default:
                killFire();
                expsn = 0;
        }
    }

    void endbonusmode() {
        bonusmode = false;
        display.setIntensity(0);
    }

    void eraseBonus() {
        if (bonusvisible) {
            bonusvisible = false;
            sprite.erasespr(14);
        }
        display.setIntensity(0);
    }

    void eraseDigger() {
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
                drawing.drawEmerald(x * 20 + 12, y * 18 + 21);
                main.incrementPenalty();
            }
            if (r == embox[dir + 1]) {
                drawing.eraseEmerald(x * 20 + 12, y * 18 + 21);
                main.incrementPenalty();
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

        display.pixels = new int[65536];

        for (int i = 0; i < 2; i++) {
            display.source[i] = new MemoryImageSource(display.width, display.height,
                    new IndexColorModel(8, 4, display.palettes[i][0], display.palettes[i][1], display.palettes[i][2]),
                    display.pixels, 0, display.width);
            display.source[i].setAnimated(true);
            display.image[i] = createImage(display.source[i]);
            display.source[i].newPixels();
        }

        display.currentImage = display.image[0];
        display.currentSource = display.source[0];

        gamethread = new Thread(this);
        gamethread.start();
    }

    void initbonusmode() {
        bonusmode = true;
        eraseBonus();
        display.setIntensity(1);
        bonustimeleft = 250 - main.getLevelNumberClampedToTen() * 20;
        startbonustimeleft = 20;
        eatmsc = 1;
    }

    void initDigger() {
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
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                input.keyUpPressed();
                break;
            case KeyEvent.VK_DOWN:
                input.keyDownPressed();
                break;
            case KeyEvent.VK_LEFT:
                input.keyLeftPressed();
                break;
            case KeyEvent.VK_RIGHT:
                input.keyRightPressed();
                break;
            case KeyEvent.VK_F1:
                input.keyF1Pressed();
                break;
            case KeyEvent.VK_F7:
                sound.musicflag = !sound.musicflag;
                if (sound.musicflag && !sound.musicplaying && sound.soundflag)
                    sound.music(sound.tuneno);
                break;
            case KeyEvent.VK_F9:
                sound.soundflag = !sound.soundflag;
                break;
            case KeyEvent.VK_F10:
                input.escape = true;
                break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS:
                input.pluspressed = true;
                break;
            case KeyEvent.VK_MINUS:
                input.minuspressed = true;
                break;
            case KeyEvent.VK_T:
                if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    // Ctrl+T cheat: take over during playback (no-op without recording)
                }
                break;
            default:
                input.processkey(e.getKeyCode());
                break;
        }
        return true;
    }

    public boolean keyUpProcess(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                input.keyUpReleased();
                break;
            case KeyEvent.VK_DOWN:
                input.keyDownReleased();
                break;
            case KeyEvent.VK_LEFT:
                input.keyLeftReleased();
                break;
            case KeyEvent.VK_RIGHT:
                input.keyRightReleased();
                break;
            case KeyEvent.VK_F1:
                input.keyF1Released();
                break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS:
                input.pluspressed = false;
                break;
            case KeyEvent.VK_MINUS:
                input.minuspressed = false;
                break;
            default:
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
            drawing.eraseEmerald(x * 20 + 12, (y + 1) * 18 + 21);
        }
    }

    void killFire() {
        if (!notfiring) {
            notfiring = true;
            sprite.erasespr(15);
            sound.soundFireOff();
        }
    }

    void makeEmeraldField() {
        emmask = 1 << main.getCurrentPlayer();
        for (int x = 0; x < 15; x++)
            for (int y = 0; y < 10; y++)
                if (main.getLevelChar(x, y, main.getLevelPlan()) == 'C')
                    emfield[y * 15 + x] |= emmask;
                else
                    emfield[y * 15 + x] &= ~emmask;
    }

    void newFrame() {
        input.checkkeyb();
        time += frametime;
        long l = time - display.getCurrentTimeMillis();
        if (l > 0) {
            try {
                Thread.sleep((int) l);
            } catch (Exception e) {
            }
        }
        display.currentSource.newPixels();
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

    public void update(Graphics g) {
        if (!Objects.isNull(g)) {
            int originalWidth = display.currentImage.getWidth(this);
            int originalHeight = display.currentImage.getHeight(this);
            int newWidth = originalWidth * scaleFactor;
            int newHeight = originalHeight * scaleFactor;
            Insets insets = getInsets();
            g.drawImage(display.currentImage, insets.left, insets.top, newWidth, newHeight, this);
        }
    }

    private void configureWindowBounds() {
        if (!isDisplayable())
            addNotify();

        GraphicsConfiguration configuration = getGraphicsConfiguration();
        if (Objects.isNull(configuration)) {
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }

        Rectangle usableBounds = getUsableScreenBounds(configuration);
        Insets windowInsets = getInsets();

        int horizontalInsets = windowInsets.left + windowInsets.right;
        int verticalInsets = windowInsets.top + windowInsets.bottom;
        int maxScaleByWidth = Math.max(1, (usableBounds.width - horizontalInsets) / width);
        int maxScaleByHeight = Math.max(1, (usableBounds.height - verticalInsets) / height);
        scaleFactor = Math.max(1, Math.min(scaleFactor, Math.min(maxScaleByWidth, maxScaleByHeight)));

        int windowWidth = width * scaleFactor + horizontalInsets;
        int windowHeight = height * scaleFactor + verticalInsets;
        int x = usableBounds.x + Math.max(0, (usableBounds.width - windowWidth) / 2);
        int y = usableBounds.y + Math.max(0, (usableBounds.height - windowHeight) / 2);

        setBounds(x, y, windowWidth, windowHeight);
    }

    private Rectangle getUsableScreenBounds(GraphicsConfiguration configuration) {
        Rectangle bounds = configuration.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(
                bounds.x + screenInsets.left,
                bounds.y + screenInsets.top,
                bounds.width - screenInsets.left - screenInsets.right,
                bounds.height - screenInsets.top - screenInsets.bottom
        );
    }

    void updatedigger() {
        int dir, ddir, clbits, diggerox, diggeroy, nmon;
        boolean push = false;
        input.readDirection();
        dir = input.getDirection();
        if (dir == 0 || dir == 2 || dir == 4 || dir == 6)
            ddir = dir;
        else
            ddir = -1;
        // Allow turn when aligned on the perpendicular axis.
        // Direction is preserved across frames by readDirection() checking held keys,
        // so the turn happens automatically once alignment is reached.
        if (diggerrx == 0 && (ddir == 2 || ddir == 6))
            digdir = digmdir = ddir;
        if (diggerry == 0 && (ddir == 4 || ddir == 0))
            digdir = digmdir = ddir;
        if (dir == -1)
            digmdir = -1;
        else if (digmdir == -1 && ddir == -1)
            digmdir = -1;
        else if (digmdir == -1)
            digmdir = digdir;
        if ((diggerx == 292 && digmdir == 0) || (diggerx == 12 && digmdir == 4) ||
                (diggery == 180 && digmdir == 6) || (diggery == 18 && digmdir == 2))
            digmdir = -1;
        diggerox = diggerx;
        diggeroy = diggery;
        if (digmdir != -1)
            drawing.digTunnel(diggerox, diggeroy, digmdir);
        switch (digmdir) {
            case 0:
                drawing.drawTunnelEdgeRight(diggerx, diggery);
                diggerx += 4;
                break;
            case 4:
                drawing.drawTunnelEdgeLeft(diggerx, diggery);
                diggerx -= 4;
                break;
            case 2:
                drawing.drawTunnelEdgeTop(diggerx, diggery);
                diggery -= 3;
                break;
            case 6:
                drawing.drawTunnelEdgeBottom(diggerx, diggery);
                diggery += 3;
                break;
        }
        if (hitemerald((diggerx - 12) / 20, (diggery - 18) / 18, (diggerx - 12) % 20,
                (diggery - 18) % 18, digmdir)) {
            scores.scoreemerald();
            sound.soundEm();
            sound.soundEmerald(emocttime);
            emocttime = 9;
        }
        clbits = drawing.drawDigger(digdir, diggerx, diggery, notfiring && rechargetime == 0);
        main.incrementPenalty();
        if ((bags.bagbits() & clbits) != 0) {
            if (digmdir == 0 || digmdir == 4) {
                push = bags.pushbags(digmdir, clbits);
                digtime++;
            } else if (!bags.pushudbags(clbits))
                push = false;
            if (!push) {
                // Restore previous position, then snap to nearest grid line
                // in the BLOCKED direction so perpendicular turns are not blocked.
                // Round toward the origin cell (opposite of blocked direction).
                diggerx = diggerox;
                diggery = diggeroy;
                if (digmdir == 2)      // blocked going up → snap down to current cell
                    diggery = ((diggery - 18 + 17) / 18) * 18 + 18;
                else if (digmdir == 6) // blocked going down → snap up to current cell
                    diggery = ((diggery - 18) / 18) * 18 + 18;
                if (digmdir == 4)      // blocked going left → snap right to current cell
                    diggerx = ((diggerx - 12 + 19) / 20) * 20 + 12;
                else if (digmdir == 0) // blocked going right → snap left to current cell
                    diggerx = ((diggerx - 12) / 20) * 20 + 12;
                drawing.drawDigger(digmdir, diggerx, diggery, notfiring && rechargetime == 0);
                main.incrementPenalty();
                digdir = reversedir(digmdir);
            }
        }
        if (((clbits & 0x3f00) != 0) && bonusmode)
            for (nmon = monster.killmonsters(clbits); nmon != 0; nmon--) {
                sound.soundEatm();
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
                    rechargetime = main.getLevelNumberClampedToTen() * 3 + 60;
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
                    sound.soundFire();
                }
        } else {
            switch (firedir) {
                case 0:
                    firex += 8;
                    pix = display.getPixel(firex, firey + 4) | display.getPixel(firex + 4, firey + 4);
                    break;
                case 4:
                    firex -= 8;
                    pix = display.getPixel(firex, firey + 4) | display.getPixel(firex + 4, firey + 4);
                    break;
                case 2:
                    firey -= 7;
                    pix = (display.getPixel(firex + 4, firey) | display.getPixel(firex + 4, firey + 1) |
                            display.getPixel(firex + 4, firey + 2) | display.getPixel(firex + 4, firey + 3) |
                            display.getPixel(firex + 4, firey + 4) | display.getPixel(firex + 4, firey + 5) |
                            display.getPixel(firex + 4, firey + 6)) & 0xc0;
                    break;
                case 6:
                    firey += 7;
                    pix = (display.getPixel(firex, firey) | display.getPixel(firex, firey + 1) |
                            display.getPixel(firex, firey + 2) | display.getPixel(firex, firey + 3) |
                            display.getPixel(firex, firey + 4) | display.getPixel(firex, firey + 5) |
                            display.getPixel(firex, firey + 6)) & 3;
                    break;
            }
            clbits = drawing.drawFire(firex, firey, 0);
            main.incrementPenalty();
            if ((clbits & 0x3f00) != 0)
                for (mon = 0, b = 256; mon < 6; mon++, b <<= 1)
                    if ((clbits & b) != 0) {
                        monster.killMonster(mon);
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
                        drawing.drawFire(firex, firey, 0);
                    }
                    break;
                case 4:
                    if (firex < 16)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firex += 8;
                        drawing.drawFire(firex, firey, 0);
                    }
                    break;
                case 2:
                    if (firey < 15)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firey += 7;
                        drawing.drawFire(firex, firey, 0);
                    }
                    break;
                case 6:
                    if (firey > 183)
                        expsn = 1;
                    else if (pix != 0 && clbits == 0) {
                        expsn = 1;
                        firey -= 7;
                        drawing.drawFire(firex, firey, 0);
                    }
            }
        }
    }
}
