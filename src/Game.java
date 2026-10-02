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
    boolean running = true;
    int W, H, groundY;
    int frame;

    Image[] runFrames = new Image[4];
    Image slideImg, jumpImg, backflipImg, celebrateImg, hangImg;
    Image bgImg; int bgW, bgH;

    int cam;
    int animIdx = 0, animTick = 0;

    boolean jumping = false, sliding = false;
    int jy = 0, jv = 0;

    boolean showCelebrate = false, showBackflip = false;
    int specialTimer = 0;
    int specialKey = 0;

    GameScreen() {
        super(true);
        W = getWidth(); H = getHeight();
        groundY = H - 30;

        try {
            for (int i = 0; i < 4; i++) {
                Image raw = Image.createImage("/run" + (i+1) + ".png");
                runFrames[i] = scale(raw, 26, 44);
            }
        } catch (Exception e) {}
        try { slideImg = scale(Image.createImage("/slide.png"), 40, 28); } catch (Exception e) {}
        try { jumpImg = scale(Image.createImage("/jumptuck.png"), 26, 36); } catch (Exception e) {}
        try { backflipImg = scale(Image.createImage("/backflip.png"), 22, 44); } catch (Exception e) {}
        try { celebrateImg = scale(Image.createImage("/celebrate.png"), 36, 48); } catch (Exception e) {}
        try { hangImg = scale(Image.createImage("/hang.png"), 28, 48); } catch (Exception e) {}

        try {
            Image rawBg = Image.createImage("/bg2.png");
            bgH = H;
            bgW = rawBg.getWidth() * bgH / rawBg.getHeight();
            bgImg = scale(rawBg, bgW, bgH);
        } catch (Exception e) { bgImg = null; }
    }

    Image scale(Image src, int nw, int nh) {
        int sw = src.getWidth(), sh = src.getHeight();
        int[] sp = new int[sw * sh];
        src.getRGB(sp, 0, sw, 0, 0, sw, sh);
        int[] dst = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            int sy = y * sh / nh;
            for (int x = 0; x < nw; x++) {
                int sx = x * sw / nw;
                dst[y * nw + x] = sp[sy * sw + sx];
            }
        }
        return Image.createRGBImage(dst, nw, nh, true);
    }

    protected void keyPressed(int kc) {
        if (kc == KEY_NUM5) { showCelebrate = true; specialTimer = 50; }
        else if (kc == KEY_NUM0) { showBackflip = true; specialTimer = 30; }
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
        boolean up = (k & UP_PRESSED) != 0;
        boolean down = (k & DOWN_PRESSED) != 0;

        cam += 3; // auto-run scroll

        if (!jumping && up) { jumping = true; jv = 11; }
        if (jumping) {
            jy += jv; jv -= 1;
            if (jy <= 0) { jy = 0; jv = 0; jumping = false; }
        }

        sliding = down && !jumping;

        if (!jumping && !sliding) {
            animTick++;
            if (animTick >= 5) { animTick = 0; animIdx = (animIdx + 1) % 4; }
        }

        if (specialTimer > 0) specialTimer--;
        else { showCelebrate = false; showBackflip = false; }
    }

    void draw(Graphics g) {
        if (bgImg != null && bgW > 0) {
            int off = cam % bgW;
            for (int x = -off; x < W; x += bgW) g.drawImage(bgImg, x, 0, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(0x3B5B8C);
            g.fillRect(0, 0, W, H);
        }

        g.setColor(0x222233);
        g.fillRect(0, groundY, W, H - groundY);
        g.setColor(0x11111F);
        g.fillRect(0, groundY, W, 3);

        int px = W / 3;
        Image img;
        int drawY;

        if (showBackflip) {
            img = backflipImg; drawY = groundY - 44 - jy;
        } else if (showCelebrate) {
            img = celebrateImg; drawY = groundY - 48;
        } else if (jumping) {
            img = jumpImg; drawY = groundY - 36 - jy;
        } else if (sliding) {
            img = slideImg; drawY = groundY - 28;
        } else {
            img = runFrames[animIdx]; drawY = groundY - 44;
        }

        if (img != null) {
            g.drawImage(img, px - 2, drawY, Graphics.TOP | Graphics.LEFT);
            g.drawImage(img, px + 2, drawY, Graphics.TOP | Graphics.LEFT);
            g.drawImage(img, px, drawY, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(0xFF0000);
            g.fillRect(px, drawY, 26, 44);
        }

        g.setColor(0xFFFFFF);
        g.drawString("UP=jump DOWN=slide 5=celebrate 0=backflip", 4, 4, Graphics.TOP | Graphics.LEFT);
    }
}
