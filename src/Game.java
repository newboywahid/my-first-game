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

    // player bullets
    int[] pbX = new int[MAXPB], pbY = new int[MAXPB];
    int[] pbDX = new int[MAXPB], pbDY = new int[MAXPB];
    boolean[] pbOn = new boolean[MAXPB];

    // enemy bullets
    int[] ebX = new int[MAXEB], ebY = new int[MAXEB];
    int[] ebDX = new int[MAXEB];
    boolean[] ebOn = new boolean[MAXEB];

    // enemies
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
            bgImg = darken(scale(rawBg, bgW, bgH), 150);
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
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++)
                if ((px[y * sw + x] >>> 24) > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
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

    Image skew(Image src, int amount) {
        int w = src.getWidth(), h = src.getHeight();
        int[] srcPix = new int[w * h];
        src.getRGB(srcPix, 0, w, 0, 0, w, h);
        int[] dst = new int[w * h];
        for (int y = 0; y < h; y++) {
            int shift = ((y * amount) / h) - (amount / 2);
            for (int x = 0; x < w; x++) {
                int sx = x - shift;
                int val = 0;
                if (sx >= 0 && sx < w) val = srcPix[y * w + sx];
                dst[y * w + x] = val;
            }
        }
        return Image.createRGBImage(dst, w, h, true);
    }

    Image darken(Image src, int amt) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getRGB(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int a = px[i] & 0xFF000000;
            int r = Math.max(0, ((px[i] >> 16) & 0xFF) - amt);
            int gg = Math.max(0, ((px[i] >> 8) & 0xFF) - amt);
            int b = Math.max(0, (px[i] & 0xFF) - amt);
            px[i] = a | (r << 16) | (gg << 8) | b;
        }
        return Image.createRGBImage(px, w, h, true);
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
                wx = 60; py = H / 2; mode = MODE_FLY; php = 100;
                for (int i = 0; i < MAXEN; i++) enOn[i] = false;
            }
            return;
        }

        boolean left = (k & LEFT_PRESSED) != 0;
        boolean right = (k & RIGHT_PRESSED) != 0;
        boolean up = (k & UP_PRESSED) != 0;
        boolean down = (k & DOWN_PRESSED) != 0;

        if (left) wx -= 3;
        if (right) wx += 3;

        tilt = 0;
        if (mode == MODE_FLY) {
            if (up) { py -= 3; tilt = -1; }
            if (down) { py += 3; tilt = 1; }
            if (right) tilt = 1;
            if (left && !right) tilt = -1;
            if (k7) { wx -= 3; py -= 3; tilt = -1; }
            if (k9) { wx += 3; py -= 3; tilt = 1; }
            if (k1) { wx -= 3; py += 3; tilt = -1; }
            if (k3) { wx += 3; py += 3; tilt = 1; }
            if (py < maxAlt) py = maxAlt;
            if (py >= groundY) { py = groundY; mode = MODE_CAR; }
        } else {
            if (k7 || k1) wx -= 2;
            if (k9 || k3) wx += 2;
            boolean moving = left || right || k7 || k9 || k1 || k3;
            if (moving && (frame % 3 == 0)) {
                smX[smNext] = wx; smY[smNext] = groundY; smAge[smNext] = 1;
                smNext = (smNext + 1) % MAXSMOKE;
            }
            if (up) { mode = MODE_FLY; py = groundY - 3; }
        }
        if (wx < 0) wx = 0;

        for (int i = 0; i < MAXSMOKE; i++) {
            if (smAge[i] > 0) { smAge[i]++; if (smAge[i] > 20) smAge[i] = 0; }
        }

        cam = wx - W / 3;
        if (cam < 0) cam = 0;

        // firing
        if (fireCd > 0) fireCd--;
        if ((k & FIRE_PRESSED) != 0 && fireCd == 0) {
            fireCd = (mode == MODE_FLY) ? 8 : 6;
            for (int i = 0; i < MAXPB; i++) {
                if (!pbOn[i]) {
                    pbOn[i] = true;
                    pbX[i] = wx + vw / 2;
                    pbY[i] = (mode == MODE_FLY) ? py - vh / 2 : groundY - 10;
                    pbDX[i] = (mode == MODE_FLY) ? 8 : 7;
                    pbDY[i] = (mode == MODE_FLY) ? 5 : 0;
                    break;
                }
            }
        }
        for (int i = 0; i < MAXPB; i++) {
            if (pbOn[i]) {
                pbX[i] += pbDX[i];
                pbY[i] += pbDY[i];
                if (pbX[i] - cam > W + 20 || pbY[i] > H) pbOn[i] = false;
            }
        }

        // spawn enemies
        if (spawnCd > 0) spawnCd--;
        int count = 0;
        for (int i = 0; i < MAXEN; i++) if (enOn[i]) count++;
        if (count < MAXEN && spawnCd == 0) {
            for (int i = 0; i < MAXEN; i++) {
                if (!enOn[i]) {
                    enOn[i] = true;
                    enX[i] = wx + W / 2 + 40 + (i * 30);
                    enType[i] = (frame / 37 + i) % 2;
                    enHP[i] = (enType[i] == T_SOLDIER) ? 20 : 50;
                    enTimer[i] = 40 + (i * 17) % 60;
                    enBurst[i] = false;
                    break;
                }
            }
            spawnCd = 50;
        }

        // update enemies
        for (int i = 0; i < MAXEN; i++) {
            if (!enOn[i]) continue;
            int dist = enX[i] - wx;
            int speed = (enType[i] == T_CAR) ? 2 : 1;
            int stopDist = (enType[i] == T_CAR) ? 70 : 90;
            if (dist > stopDist) enX[i] -= speed;

            enTimer[i]--;
            if (enTimer[i] <= 0) {
                enBurst[i] = !enBurst[i];
                enTimer[i] = enBurst[i] ? 60 : 100;
            }
            if (enBurst[i] && (frame % 12 == 0)) {
                for (int b = 0; b < MAXEB; b++) {
                    if (!ebOn[b]) {
                        ebOn[b] = true;
                        ebX[b] = enX[i];
                        ebY[b] = groundY - 14;
                        ebDX[b] = (enType[i] == T_CAR) ? -6 : -4;
                        break;
                    }
                }
            }

            // ram damage (car close range)
            if (enType[i] == T_CAR && dist < 20 && mode == MODE_CAR && hitFlash == 0) {
                php -= 15;
                hitFlash = 15;
            }

            // player bullet hits enemy
            for (int p = 0; p < MAXPB; p++) {
                if (pbOn[p]) {
                    int ex = enX[i] - cam;
                    int psx = pbX[p] - cam;
                    if (Math.abs(psx - ex) < enW / 2 && pbY[p] > groundY - enH && pbY[p] < groundY + 5) {
                        pbOn[p] = false;
                        enHP[i] -= 10;
                        if (enHP[i] <= 0) enOn[i] = false;
                    }
                }
            }
        }

        // enemy bullets vs player
        for (int b = 0; b < MAXEB; b++) {
            if (ebOn[b]) {
                ebX[b] += ebDX[b];
                if (ebX[b] - cam < -20) { ebOn[b] = false; continue; }
                int psx = wx - cam;
                if (Math.abs(ebX[b] - cam - psx) < vw / 2 && hitFlash == 0) {
                    ebOn[b] = false;
                    php -= 6;
                    hitFlash = 12;
                }
            }
        }
        if (hitFlash > 0) hitFlash--;
        if (php < 0) php = 0;
    }

    void draw(Graphics g) {
        if (bgImg != null && bgW > 0) {
            int off = cam % bgW;
            for (int x = -off; x < W; x += bgW) g.drawImage(bgImg, x, 0, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(0x2E3B2E);
            g.fillRect(0, 0, W, H);
        }

        if (state == TITLE) {
            g.setColor(0x000000);
            g.fillRect(W / 2 - 90, H / 2 - 40, 180, 90);
            g.setColor(0xFFFFFF);
            g.drawString("WAHID FIRST GAME", W / 2, H / 2 - 34, Graphics.TOP | Graphics.HCENTER);
            if (((frame >> 3) & 1) == 0) g.drawString("PRESS FIRE TO START", W / 2, H / 2 - 4, Graphics.TOP | Graphics.HCENTER);
            if (heliLevel != null) g.drawImage(heliLevel, W / 2 - vw / 2, H / 2 + 20, Graphics.TOP | Graphics.LEFT);
            return;
        }

        int sx = wx - cam;

        for (int i = 0; i < MAXSMOKE; i++) {
            if (smAge[i] > 0) {
                int ssx = smX[i] - cam - smAge[i];
                int ssy = smY[i] - smAge[i] / 2;
                int size = 3 + smAge[i] / 3;
                g.setColor(0xAAAAAA);
                g.fillArc(ssx - size / 2, ssy - size / 2, size, size, 0, 360);
            }
        }

        // enemies
        for (int i = 0; i < MAXEN; i++) {
            if (!enOn[i]) continue;
            int ex = enX[i] - cam;
            Image eimg = (enType[i] == T_SOLDIER) ? soldierImg : armorImg;
            int ew = (enType[i] == T_SOLDIER) ? enW : enW + 12;
            g.setColor(0x111111);
            g.fillArc(ex - ew / 2 + 3, groundY - 5, ew - 6, 8, 0, 360);
            if (eimg != null) {
                g.drawImage(eimg, ex - ew / 2, groundY - enH, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(0x556B2F);
                g.fillRect(ex - ew / 2, groundY - enH, ew, enH);
            }
            g.setColor(0x000000);
            g.fillRect(ex - ew / 2, groundY - enH - 6, ew, 4);
            g.setColor(0xFFC107);
            int maxhp = (enType[i] == T_SOLDIER) ? 20 : 50;
            g.fillRect(ex - ew / 2 + 1, groundY - enH - 5, (ew - 2) * enHP[i] / maxhp, 2);
        }

        // enemy bullets
        g.setColor(0xFF5555);
        for (int b = 0; b < MAXEB; b++) if (ebOn[b]) g.fillRect(ebX[b] - cam - 2, ebY[b], 4, 3);

        // player bullets
        g.setColor(0xFFEB3B);
        for (int p = 0; p < MAXPB; p++) if (pbOn[p]) g.fillRect(pbX[p] - cam, pbY[p], 5, 3);

        Image img;
        if (mode == MODE_FLY) img = (tilt > 0) ? heliDown : (tilt < 0) ? heliUp : heliLevel;
        else img = carImg;
        int drawY = py - vh;

        g.setColor(0x1A1A1A);
        g.fillArc(sx + 3, groundY - 5, vw - 6, 8, 0, 360);

        if (img != null) {
            if (hitFlash > 0 && (frame & 2) == 0) {
                // skip draw to flash white/red feel
            } else {
                g.drawImage(img, sx - 1, drawY, Graphics.TOP | Graphics.LEFT);
                g.drawImage(img, sx + 1, drawY, Graphics.TOP | Graphics.LEFT);
                g.drawImage(img, sx, drawY - 1, Graphics.TOP | Graphics.LEFT);
                g.drawImage(img, sx, drawY + 1, Graphics.TOP | Graphics.LEFT);
                g.drawImage(img, sx, drawY, Graphics.TOP | Graphics.LEFT);
            }
        } else {
            g.setColor(0xFF0000);
            g.fillRect(sx, drawY, vw, vh);
        }

        if (mode == MODE_FLY) {
            int bladeY = drawY + 2;
            int spin = frame % 4;
            g.setColor(0x333333);
            if (spin < 2) g.fillRect(sx + 6, bladeY, vw - 4, 2);
            else g.fillArc(sx + 8, bladeY - 3, vw - 12, 6, 0, 360);
        }

        // HUD
        g.setColor(0x000000);
        g.fillRect(2, 2, 74, 10);
        g.setColor(0xD32F2F);
        g.fillRect(3, 3, php * 72 / 100, 8);
        g.setColor(0xFFFFFF);
        g.drawString(mode == MODE_FLY ? "FLY" : "DRIVE", W - 4, 2, Graphics.TOP | Graphics.RIGHT);

        if (php <= 0) {
            g.setColor(0x000000);
            g.fillRect(W / 2 - 60, H / 2 - 16, 120, 32);
            g.setColor(0xFFFFFF);
            g.drawString("YOU DIED", W / 2, H / 2 - 10, Graphics.TOP | Graphics.HCENTER);
        }
    }
    }
