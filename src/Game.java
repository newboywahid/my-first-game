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
    static final int MAXSMOKE = 16;

    boolean running = true;
    int W, H, groundY, maxAlt;
    int state = TITLE, frame;

    Image heliImg, carImg, bgImg;
    int vw = 56, vh = 34;
    int bgW, bgH;

    int mode = MODE_FLY;
    int wx, py;
    int cam;

    // numpad diagonal keys
    boolean k7, k9, k1, k3;

    // tire smoke particles
    int[] smX = new int[MAXSMOKE];
    int[] smY = new int[MAXSMOKE];
    int[] smAge = new int[MAXSMOKE];
    int smNext = 0;

    GameScreen() {
        super(true);
        W = getWidth();
        H = getHeight();
        groundY = H - 40;
        maxAlt = 22;
        wx = 60;
        py = H / 2;

        try { heliImg = scale(autoCrop(Image.createImage("/heli.png")), vw, vh); } catch (Exception e) { heliImg = null; }
        try { carImg = scale(autoCrop(Image.createImage("/car.png")), vw, vh); } catch (Exception e) { carImg = null; }
        try {
            Image rawBg = Image.createImage("/bg.png");
            bgH = H;
            bgW = rawBg.getWidth() * bgH / rawBg.getHeight();
            bgImg = scale(rawBg, bgW, bgH);
        } catch (Exception e) { bgImg = null; }

        for (int i = 0; i < MAXSMOKE; i++) smAge[i] = 0;
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
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                if ((px[y * sw + x] >>> 24) > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return src;
        int nw = maxX - minX + 1, nh = maxY - minY + 1;
        int[] crop = new int[nw * nh];
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++)
                crop[y * nw + x] = px[(minY + y) * sw + (minX + x)];
        return Image.createRGBImage(crop, nw, nh, true);
    }

    Image scale(Image src, int nw, int nh) {
        int sw = src.getWidth(), sh = src.getHeight();
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

        boolean left = (k & LEFT_PRESSED) != 0;
        boolean right = (k & RIGHT_PRESSED) != 0;
        boolean up = (k & UP_PRESSED) != 0;
        boolean down = (k & DOWN_PRESSED) != 0;

        if (left) wx -= 3;
        if (right) wx += 3;

        if (mode == MODE_FLY) {
            if (up) py -= 3;
            if (down) py += 3;
            // diagonals
            if (k7) { wx -= 3; py -= 3; }
            if (k9) { wx += 3; py -= 3; }
            if (k1) { wx -= 3; py += 3; }
            if (k3) { wx += 3; py += 3; }

            if (py < maxAlt) py = maxAlt;
            if (py >= groundY) { py = groundY; mode = MODE_CAR; }
        } else {
            if (k7 || k1) wx -= 2;
            if (k9 || k3) wx += 2;
            boolean moving = left || right || k7 || k9 || k1 || k3;
            if (moving && (frame % 3 == 0)) {
                smX[smNext] = wx;
                smY[smNext] = groundY;
                smAge[smNext] = 1;
                smNext = (smNext + 1) % MAXSMOKE;
            }
            if (up) { mode = MODE_FLY; py = groundY - 3; }
        }

        if (wx < 0) wx = 0;

        for (int i = 0; i < MAXSMOKE; i++) {
            if (smAge[i] > 0) {
                smAge[i]++;
                if (smAge[i] > 20) smAge[i] = 0;
            }
        }

        cam = wx - W / 3;
        if (cam < 0) cam = 0;
    }

    void draw(Graphics g) {
        if (bgImg != null && bgW > 0) {
            int off = cam % bgW;
            for (int x = -off; x < W; x += bgW) {
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

        // tire smoke (behind vehicle)
        for (int i = 0; i < MAXSMOKE; i++) {
            if (smAge[i] > 0) {
                int ssx = smX[i] - cam - smAge[i];
                int ssy = smY[i] - smAge[i] / 2;
                int size = 3 + smAge[i] / 3;
                g.setColor(0xAAAAAA);
                g.fillArc(ssx - size / 2, ssy - size / 2, size, size, 0, 360);
            }
        }

        Image img = (mode == MODE_FLY) ? heliImg : carImg;
        int drawY = py - vh;

        // ground shadow
        g.setColor(0x1A1A1A);
        int shW = vw - 6;
        g.fillArc(sx + 3, groundY - 5, shW, 8, 0, 360);

        // fake outline: draw dark offset copies behind, then the real image
        if (img != null) {
            g.drawImage(img, sx - 1, drawY, Graphics.TOP | Graphics.LEFT);
            g.drawImage(img, sx + 1, drawY, Graphics.TOP | Graphics.LEFT);
            g.drawImage(img, sx, drawY - 1, Graphics.TOP | Graphics.LEFT);
            g.drawImage(img, sx, drawY + 1, Graphics.TOP | Graphics.LEFT);
            g.drawImage(img, sx, drawY, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(0xFF0000);
            g.fillRect(sx, drawY, vw, vh);
        }

        // spinning blade blur (helicopter mode only)
        if (mode == MODE_FLY) {
            int bladeY = drawY + 2;
            int spin = frame % 4;
            g.setColor(0x333333);
            if (spin < 2) {
                g.fillRect(sx + 6, bladeY, vw - 4, 2);
            } else {
                g.fillArc(sx + 8, bladeY - 3, vw - 12, 6, 0, 360);
            }
        }

        g.setColor(0x000000);
        g.drawString(mode == MODE_FLY ? "FLYING" : "DRIVING", 4, 4, Graphics.TOP | Graphics.LEFT);
    }
    }
