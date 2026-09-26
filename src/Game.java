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

    boolean running = true;
    int W, H, groundY, maxAlt;
    int state = TITLE, frame;

    Image heliImg, carImg, bgImg;
    int vw = 56, vh = 34;
    int bgW, bgH;

    int mode = MODE_FLY;
    int wx, py; // wx = world x position
    int cam;

    GameScreen() {
        super(true);
        W = getWidth();
        H = getHeight();
        groundY = H - 40;
        maxAlt = 22;
        wx = 60;
        py = H / 2;

        try {
            Image raw = autoCrop(Image.createImage("/heli.png"));
            heliImg = scale(raw, vw, vh);
        } catch (Exception e) { heliImg = null; }

        try {
            Image raw2 = autoCrop(Image.createImage("/car.png"));
            carImg = scale(raw2, vw, vh);
        } catch (Exception e) { carImg = null; }

        try {
            Image rawBg = Image.createImage("/bg.png");
            bgH = H;
            bgW = rawBg.getWidth() * bgH / rawBg.getHeight();
            bgImg = scale(rawBg, bgW, bgH);
        } catch (Exception e) { bgImg = null; }
    }

    // removes empty transparent border so images don't have hidden padding
    Image autoCrop(Image src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int[] px = new int[sw * sh];
        src.getRGB(px, 0, sw, 0, 0, sw, sh);
        int minX = sw, minY = sh, maxX = -1, maxY = -1;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int a = (px[y * sw + x] >>> 24);
                if (a > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return src;
        int nw = maxX - minX + 1;
        int nh = maxY - minY + 1;
        int[] crop = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                crop[y * nw + x] = px[(minY + y) * sw + (minX + x)];
            }
        }
        return Image.createRGBImage(crop, nw, nh, true);
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
        frame++;
        int k = getKeyStates();

        if (state == TITLE) {
            if ((k & FIRE_PRESSED) != 0) {
                state = PLAY;
                wx = 60; py = H / 2; mode = MODE_FLY;
            }
            return;
        }

        if ((k & LEFT_PRESSED) != 0) wx -= 3;
        if ((k & RIGHT_PRESSED) != 0) wx += 3;
        if (wx < 0) wx = 0;

        if (mode == MODE_FLY) {
            if ((k & UP_PRESSED) != 0) py -= 3;
            if ((k & DOWN_PRESSED) != 0) py += 3;
            if (py < maxAlt) py = maxAlt;
            if (py >= groundY) { py = groundY; mode = MODE_CAR; }
        } else {
            if ((k & UP_PRESSED) != 0) { mode = MODE_FLY; py = groundY - 3; }
        }

        cam = wx - W / 3;
        if (cam < 0) cam = 0;
    }

    void draw(Graphics g) {
        // background
        if (bgImg != null && bgW > 0) {
            int off = cam % bgW;
            int startX = -off;
            for (int x = startX; x < W; x += bgW) {
                g.drawImage(bgImg, x, 0, Graphics.TOP | Graphics.LEFT);
            }
        } else {
            g.setColor(0x87CEEB);
            g.fillRect(0, 0, W, H);
            g.setColor(0x3E8E41);
            g.fillRect(0, groundY, W, H - groundY);
        }

        if (state == TITLE) {
            g.setColor(0x000000);
            g.fillRect(W / 2 - 90, H / 2 - 40, 180, 90);
            g.setColor(0xFFFFFF);
            g.drawString("WAHID FIRST GAME", W / 2, H / 2 - 34, Graphics.TOP | Graphics.HCENTER);
            if (((frame >> 3) & 1) == 0) {
                g.drawString("PRESS FIRE TO START", W / 2, H / 2 - 4, Graphics.TOP | Graphics.HCENTER);
            }
            if (heliImg != null) {
                g.drawImage(heliImg, W / 2 - vw / 2, H / 2 + 20, Graphics.TOP | Graphics.LEFT);
            }
            return;
        }

        int sx = wx - cam;
        Image img = (mode == MODE_FLY) ? heliImg : carImg;
        if (img != null) {
            g.drawImage(img, sx, py - vh, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(0xFF0000);
            g.fillRect(sx, py - vh, vw, vh);
        }

        g.setColor(0x000000);
        g.drawString(mode == MODE_FLY ? "FLYING" : "DRIVING", 4, 4, Graphics.TOP | Graphics.LEFT);
    }
}
