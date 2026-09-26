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
    static final int MODE_FLY = 0, MODE_CAR = 1;
    boolean running = true;
    int W, H, groundY;
    Image heliImg, carImg;
    int vw = 56, vh = 34;

    int mode = MODE_FLY;
    int px, py;
    int maxAlt;

    GameScreen() {
        super(true);
        W = getWidth();
        H = getHeight();
        groundY = H - 20;
        maxAlt = H - 60;
        px = 40;
        py = H / 2;

        try {
            Image raw = Image.createImage("/heli.png");
            heliImg = scale(raw, vw, vh);
        } catch (Exception e) { heliImg = null; }
        try {
            Image raw2 = Image.createImage("/car.png");
            carImg = scale(raw2, vw, vh);
        } catch (Exception e) { carImg = null; }
    }

    Image scale(Image src, int nw, int nh) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int[] srcPix = new int[sw * sh];
        src.getRGB(srcPix, 0, sw, 0, 0, sw, sh);
        int[] dst = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            int sy = y * sh / nh;
            for (int x = 0; x < nw; x++) {
                int sx = x * sw / nw;
                dst[y * nw + x] = srcPix[sy * sw + sx];
            }
        }
        return Image.createRGBImage(dst, nw, nh, true);
    }

    public void stop() { running = false; }

    public void run() {
        Graphics g = getGraphics();
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        while (running) {
            long t0 = System.currentTimeMillis();
            update();
            draw(g);
            flushGraphics();
            long dt = System.currentTimeMillis() - t0;
            if (dt < 40) { try { Thread.sleep(40 - dt); } catch (InterruptedException e) {} }
        }
    }

    void update() {
        int k = getKeyStates();

        if ((k & LEFT_PRESSED) != 0) px -= 3;
        if ((k & RIGHT_PRESSED) != 0) px += 3;
        if (px < 0) px = 0;

        if (mode == MODE_FLY) {
            if ((k & UP_PRESSED) != 0) py -= 3;
            if ((k & DOWN_PRESSED) != 0) py += 3;
            if (py < maxAlt) py = maxAlt;
            if (py >= groundY) {
                py = groundY;
                mode = MODE_CAR;
            }
        } else {
            if ((k & UP_PRESSED) != 0) {
                mode = MODE_FLY;
                py = groundY - 3;
            }
        }
    }

    void draw(Graphics g) {
        g.setColor(0x87CEEB);
        g.fillRect(0, 0, W, H);
        g.setColor(0x3E8E41);
        g.fillRect(0, groundY, W, H - groundY);

        Image img = (mode == MODE_FLY) ? heliImg : carImg;
        if (img != null) {
            g.drawImage(img, px, py - vh, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(0xFF0000);
            g.fillRect(px, py - vh, vw, vh);
        }

        g.setColor(0x000000);
        g.drawString(mode == MODE_FLY ? "FLYING" : "DRIVING", 4, 4, Graphics.TOP | Graphics.LEFT);
    }
}
