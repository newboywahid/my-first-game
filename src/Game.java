import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.GameCanvas;

public class Game extends MIDlet {
    private GameScreen screen;
    public void startApp() {
        if (screen == null) {
            screen = new GameScreen();
            Display.getDisplay(this).setCurrent(screen);
            new Thread(screen).start();
        }
    }
    public void pauseApp() {}
    public void destroyApp(boolean u) { if (screen != null) screen.stop(); }
}

class GameScreen extends GameCanvas implements Runnable {
    static final int TITLE = 0, PLAY = 1;
    static final int MODE_FLY = 0, MODE_CAR = 1;
    static final int MAXSMOKE = 16, MAXPB = 8, MAXEB = 16, MAXEN = 5;
    static final int T_SOLDIER = 0, T_CAR = 1;

    boolean running = true;
    int W, H, groundY, maxAlt;
    int state = TITLE, frame;

    Image heliLevel, heliDown, heliUp, carImg, bgImg, soldierImg, armorImg;
    int vw = 56, vh = 34;
    int bgW, bgH;

    int mode = MODE_FLY;
    int wx, py, php = 100;
    int cam;
    int tilt = 0;
    int hitFlash = 0;
    int fireCd = 0;

    boolean k7, k9, k1, k3;

    int[] smX = new int[MAXSMOKE];
    int[] smY = new int[MAXSMOKE];
    int[] smAge = new int[MAXSMOKE];
    int smNext = 0;

    int[] pbX = new int[MAXPB], pbY = new int[MAXPB];
    int[] pbDX = new int[MAXPB], pbDY = new int[MAXPB];
    boolean[] pbOn = new boolean[MAXPB];

    int[] ebX = new int[MAXEB], ebY = new int[MAXEB];
    int[] ebDX = new int[MAXEB];
    boolean[] ebOn = new boolean[MAXEB];

    int[] enX = new int[MAXEN];
    int[] enHP = new int[MAXEN];
    int[] enType = new int[MAXEN];
    boolean[] enOn = new boolean[MAXEN];
    int[] enTimer = new int[MAXEN];
    boolean[] enBurst = new boolean[MAXEN];
    int spawnCd = 0;
    int enW = 24, enH = 32;

    GameScreen() {
        super(true);
        W = getWidth();
        H = getHeight();
        groundY = H - 40;
        maxAlt = groundY - 140;
        if (maxAlt < 20) maxAlt = 20;
        wx = 60;
        py = H / 2;

        try {
            Image base = scale(autoCrop(Image.createImage("/heli.png")), vw, vh);
            heliLevel = base;
            heliDown = skew(base, 6);
            heliUp = skew(base, -6);
        } catch (Exception e) { heliLevel = heliDown = heliUp = null; }

        try { carImg = scale(autoCrop(Image.createImage("/car.png")), vw, vh); } catch (Exception e) { carImg = null; }
        try { soldierImg = scale(autoCrop(Image.createImage("/soldier.png")), enW, enH); } catch (Exception e) { soldierImg = null; }
        try { armorImg = scale(autoCrop(Image.createImage("/armorcar.png")), enW + 12, enH); } catch (Exception e) { armorImg = null; }

        try {
            Image rawBg = Image.createImage("/bg.png");
            bgH = H;
            bgW = rawBg.getWidth() * bgH / rawBg.getHeight();
            bgImg = fade(scale(rawBg, bgW, bgH), 40);
        } catch (Exception e) { bgImg = null; }

        for (int i = 0; i < MAXSMOKE; i++) smAge[i] = 0;
        for (int i = 0; i < MAXEN; i++) enOn[i] = false;
    }

    protected void keyPressed(int keyCode) {
        if (keyCode == KEY_NUM7) k7 = true;
        else if (keyCode == KEY_NUM9) k9 = true;
        else if (keyCode == KEY_NUM1) k1 = true;
        else if (keyCode == KEY_NUM3) k3 = true;
    }
    protected void keyReleased(int keyCode) {
        if (keyCode == KEY_NUM7) k7 = false;
        else if (keyCode == KEY_NUM9) k9 = false;
        else if (keyCode == KEY_NUM1) k1 = false;
        else if (keyCode == KEY_NUM3) k3 = false;
    }

    Image autoCrop(Image src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int[] px = new int[sw * sh];
        src.getRGB(px, 0, sw, 0, 0, sw, sh);
        int minX = sw, minY = sh, maxX = -1, maxY = -1;
        for (int y = 0; y
