package org.digger.app;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Main game class: manages the game loop, digger logic, fire, bonus mode,
 * emerald field, and the AWT window with CGA display output.
 */
public class Digger extends Frame implements Runnable {

    public boolean running;

    static int MAX_RATE = 200, MIN_RATE = 40;

    int width = 320, height = 200, frametime = 66;
    Thread gamethread;

    private static final Preferences windowPrefs = Preferences.userNodeForPackage(Digger.class);
    private static final String PREF_WINDOW_WIDTH = "windowWidth";
    private static final String PREF_WINDOW_HEIGHT = "windowHeight";
    private static final String PREF_FRAMETIME = "frametime";

    Bags bags;
    Main main;
    Sound sound;
    Monster monster;
    Scores scores;
    Sprite sprite;
    Drawing drawing;
    Input input;
    CgaDisplay display;
    private final GameCanvas canvas = new GameCanvas();

    // ----- Digger state -----

    int diggerx = 0, diggery = 0, diggerh = 0, diggerv = 0, diggerrx = 0, diggerry = 0, digmdir = 0,
            digdir = 0, digtime = 0, rechargetime = 0, firex = 0, firey = 0, firedir = 0, expsn = 0,
            deathstage = 0, deathbag = 0, deathani = 0, deathtime = 0, startbonustimeleft = 0,
            bonustimeleft = 0, monsterEatMultiplier = 0, emocttime = 0;

    int emmask = 0;

    byte[] emfield = new byte[150];

    boolean digOnScreen = false, notFiring = false, bonusVisible = false, bonusMode = false, diggerVisible = false;

    long time, ftime = 50;

    /** Hit-box offsets for emerald collection per direction. */
    int[] embox = {8, 12, 12, 9, 16, 12, 6, 9};
    /** Arc heights for digger death animation (stage 5). */
    int[] deatharc = {3, 5, 6, 6, 5, 3, 0};

    public Digger() {
        setTitle("Digger Game");

        canvas.setFocusable(false);
        setLayout(new BorderLayout());
        add(canvas, BorderLayout.CENTER);

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
                windowPrefs.putInt(PREF_WINDOW_WIDTH, canvas.getWidth());
                windowPrefs.putInt(PREF_WINDOW_HEIGHT, canvas.getHeight());
                windowPrefs.putInt(PREF_FRAMETIME, frametime);
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
            Digger app = new Digger();
            app.setVisible(true);
        });
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
        bonusVisible = true;
        drawing.drawBonus(292, 18);
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
                    if (sound.musicFlag)
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
        if (diggerVisible)
            if (digOnScreen)
                if (digtime != 0) {
                    drawing.drawDigger(digmdir, diggerx, diggery, notFiring && rechargetime == 0);
                    main.incrementPenalty();
                    digtime--;
                } else
                    updatedigger();
            else
                diggerdie();
        if (bonusMode && digOnScreen) {
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
        if (bonusMode && !digOnScreen) {
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
        bonusMode = false;
        display.setIntensity(0);
    }

    void eraseBonus() {
        if (bonusVisible) {
            bonusVisible = false;
            sprite.eraseSprite(14);
        }
        display.setIntensity(0);
    }

    void eraseDigger() {
        sprite.eraseSprite(0);
        diggerVisible = false;
    }

    boolean getfirepflag() {
        return input.firePressedFlag;
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

        frametime = Math.max(MIN_RATE, Math.min(MAX_RATE, windowPrefs.getInt(PREF_FRAMETIME, frametime)));

        display.pixels = new int[65536];

        for (int i = 0; i < 2; i++) {
            display.source[i] = new MemoryImageSource(display.width, display.height,
                    new IndexColorModel(8, 4, display.palettes[i][0], display.palettes[i][1], display.palettes[i][2]),
                    display.pixels, 0, display.width);
            display.source[i].setAnimated(true);
            display.image[i] = canvas.createImage(display.source[i]);
            display.source[i].newPixels();
        }

        display.currentImage = display.image[0];
        display.currentSource = display.source[0];

        gamethread = new Thread(this);
        gamethread.start();
    }

    void initbonusmode() {
        bonusMode = true;
        eraseBonus();
        display.setIntensity(1);
        bonustimeleft = 250 - main.getLevelNumberClampedToTen() * 20;
        startbonustimeleft = 20;
        monsterEatMultiplier = 1;
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
        digOnScreen = true;
        deathstage = 1;
        diggerVisible = true;
        diggery = diggerv * 18 + 18;
        sprite.moveDrawSprite(0, diggerx, diggery);
        notFiring = true;
        emocttime = 0;
        bonusVisible = bonusMode = false;
        input.firePressed = false;
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
                sound.musicFlag = !sound.musicFlag;
                if (sound.musicFlag && !sound.musicPlaying && sound.soundFlag)
                    sound.music(sound.tuneno);
                break;
            case KeyEvent.VK_F9:
                sound.soundFlag = !sound.soundFlag;
                break;
            case KeyEvent.VK_F10:
                input.escape = true;
                break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS:
                input.plusPressed = true;
                break;
            case KeyEvent.VK_MINUS:
                input.minusPressed = true;
                break;
            case KeyEvent.VK_T:
                if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    // Ctrl+T cheat: take over during playback (no-op without recording)
                }
                break;
            default:
                input.processKey(e.getKeyCode());
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
                input.plusPressed = false;
                break;
            case KeyEvent.VK_MINUS:
                input.minusPressed = false;
                break;
            default:
                break;
        }
        return true;
    }

    void killdigger(int stage, int bag) {
        if (deathstage < 2 || deathstage > 4) {
            digOnScreen = false;
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
        if (!notFiring) {
            notFiring = true;
            sprite.eraseSprite(15);
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
        input.checkKeyboard();
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

    /**
     * Отрисовывает буфер CGA, масштабируя его под размер canvas с сохранением пропорций 320x200.
     * <p>
     * Свободное место по краям (letterbox) закрашивается чёрным. Интерполяция методом ближайшего
     * соседа сохраняет чёткость пиксель-арта даже при нецелочисленном коэффициенте масштабирования,
     * возникающем при произвольном изменении размера окна.
     */
    private final class GameCanvas extends Canvas {
        private BufferStrategy bufferStrategy;

        GameCanvas() {
            // Пересоздаём стратегию буферизации после ресайза — старая привязана к прежнему размеру поверхности.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    bufferStrategy = null;
                }
            });
        }

        @Override
        public void paint(Graphics g) {
            render();
        }

        @Override
        public void update(Graphics g) {
            render();
        }

        private void render() {
            if (Objects.isNull(display) || Objects.isNull(display.currentImage))
                return;

            int canvasWidth = getWidth();
            int canvasHeight = getHeight();
            int originalWidth = display.currentImage.getWidth(this);
            int originalHeight = display.currentImage.getHeight(this);
            if (canvasWidth <= 0 || canvasHeight <= 0 || originalWidth <= 0 || originalHeight <= 0)
                return;

            if (Objects.isNull(bufferStrategy)) {
                createBufferStrategy(2);
                bufferStrategy = getBufferStrategy();
                if (Objects.isNull(bufferStrategy))
                    return;
            }

            double scale = Math.min((double) canvasWidth / originalWidth, (double) canvasHeight / originalHeight);
            int drawWidth = (int) Math.round(originalWidth * scale);
            int drawHeight = (int) Math.round(originalHeight * scale);
            int offsetX = (canvasWidth - drawWidth) / 2;
            int offsetY = (canvasHeight - drawHeight) / 2;

            // Рисуем в задний буфер и показываем его атомарно через show() — без двойной
            // буферизации кадр рисовался прямо на видимой поверхности (сначала чёрная заливка,
            // потом картинка), что на Windows/Linux было заметно как мерцание между кадрами.
            Graphics g = bufferStrategy.getDrawGraphics();
            try {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, canvasWidth, canvasHeight);
                if (g instanceof Graphics2D)
                    ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(display.currentImage, offsetX, offsetY, drawWidth, drawHeight, this);
            } finally {
                g.dispose();
            }
            bufferStrategy.show();
        }
    }

    /**
     * Определяет размер окна при запуске: восстанавливает последний размер, до которого игрок
     * растянул окно (сохранённый в {@link #windowPrefs}), либо подбирает наибольшее кратное
     * разрешению 320x200, комфортно помещающееся на текущем экране.
     * <p>
     * Инсеты вручную не читаются — этим занимается {@link #pack()}.
     */
    private void configureWindowBounds() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        if (Objects.isNull(configuration)) {
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }

        Rectangle usableBounds = getUsableScreenBounds(configuration);

        int savedWidth = windowPrefs.getInt(PREF_WINDOW_WIDTH, 0);
        int savedHeight = windowPrefs.getInt(PREF_WINDOW_HEIGHT, 0);
        Dimension targetSize = (savedWidth > 0 && savedHeight > 0)
                ? new Dimension(Math.min(savedWidth, usableBounds.width), Math.min(savedHeight, usableBounds.height))
                : computeDefaultCanvasSize(usableBounds);

        canvas.setPreferredSize(targetSize);
        pack();

        // pack() выставляет размер окна по preferred size canvas плюс инсеты, которые в этот момент
        // сообщит оконный менеджер; если результат всё равно не помещается на экран (например,
        // сохранённый размер был снят с большего монитора), уменьшаем и вызываем pack() повторно.
        while ((getWidth() > usableBounds.width || getHeight() > usableBounds.height)
                && targetSize.width > width && targetSize.height > height) {
            targetSize = new Dimension((int) (targetSize.width * 0.9), (int) (targetSize.height * 0.9));
            canvas.setPreferredSize(targetSize);
            pack();
        }

        setLocation(usableBounds.x + Math.max(0, (usableBounds.width - getWidth()) / 2),
                usableBounds.y + Math.max(0, (usableBounds.height - getHeight()) / 2));
    }

    /** Наибольшее целое кратное базового разрешения 320x200, помещающееся в 90% рабочей области экрана. */
    private Dimension computeDefaultCanvasSize(Rectangle usableBounds) {
        int marginedWidth = (int) (usableBounds.width * 0.9);
        int marginedHeight = (int) (usableBounds.height * 0.9);
        int maxScale = Math.max(1, Math.min(marginedWidth / width, marginedHeight / height));
        return new Dimension(width * maxScale, height * maxScale);
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
            scores.scoreEmerald();
            sound.soundEm();
            sound.soundEmerald(emocttime);
            emocttime = 9;
        }
        clbits = drawing.drawDigger(digdir, diggerx, diggery, notFiring && rechargetime == 0);
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
                drawing.drawDigger(digmdir, diggerx, diggery, notFiring && rechargetime == 0);
                main.incrementPenalty();
                digdir = reversedir(digmdir);
            }
        }
        if (((clbits & 0x3f00) != 0) && bonusMode)
            for (nmon = monster.killmonsters(clbits); nmon != 0; nmon--) {
                sound.soundEatm();
                scores.scoreEatMonster();
            }
        if ((clbits & 0x4000) != 0) {
            scores.scoreBonus();
            initbonusmode();
        }
        diggerh = (diggerx - 12) / 20;
        diggerrx = (diggerx - 12) % 20;
        diggerv = (diggery - 18) / 18;
        diggerry = (diggery - 18) % 18;
    }

    void updatefire() {
        int clbits, b, mon, pix = 0;
        if (notFiring) {
            if (rechargetime != 0)
                rechargetime--;
            else if (getfirepflag())
                if (digOnScreen) {
                    rechargetime = main.getLevelNumberClampedToTen() * 3 + 60;
                    notFiring = false;
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
                    sprite.moveDrawSprite(15, firex, firey);
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
                        scores.scoreKillMonster();
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
