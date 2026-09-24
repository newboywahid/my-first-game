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
    public void destroyApp(boolean u) {
        if (screen != null) screen.stop();
    }
}

class GameScreen extends GameCanvas implements Runnable {
    static final int PLAY = 0, OVER = 1, WIN = 2;
    static final int PW = 14, PH = 30, CLIP = 8, MAXB = 6;

    boolean running = true;
    int W, H, groundY;
    int state, stateTimer, frame, phase, cam;

    // player
    int px, jh, jv, pdir, php, pinv, walkAnim;
    int ammo, fireCd;
    boolean reloading;
    long reloadEnd;

    // player bullets
    int[] bx = new int[MAXB];
    int[] bo = new int[MAXB];
    int[] bd = new int[MAXB];
    int[] bl = new int[MAXB];

    // enemy
    boolean eOn;
    int eType, ex, eh, ehv, ehp, emax, edir, eW, eH;
    int eSpd, eAcc, eDmg, eAtk, eSwing, eFlash;
    int bMode, bTimer, bvx, bShot;

    // boss shot
    boolean shOn;
    int shx, sho, shd;

    // water bottle
    boolean botOn;
    int botX;

    GameScreen() {
        super(true);
        W = getWidth();
        H = getHeight();
        groundY = H - 24;
        reset();
    }

    public void stop() { running = false; }

    void reset() {
        px = 40; jh = 0; jv = 0; pdir = 1; php = 100; pinv = 0; walkAnim = 0;
        ammo = CLIP; fireCd = 0; reloading = false;
        for (int i = 0; i < MAXB; i++) bl[i] = 0;
        eOn = false; shOn = false; botOn = false;
        phase = 0; state = PLAY; stateTimer = 0; cam = 0;
    }

    public void run() {
        Graphics g = getGraphics();
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        while (running) {
            long t0 = System.currentTimeMillis();
            W = getWidth();
            H = getHeight();
            groundY = H - 24;
            update();
            draw(g);
            flushGraphics();
            long dt = System.currentTimeMillis() - t0;
            if (dt < 40) {
                try { Thread.sleep(40 - dt); } catch (InterruptedException e) {}
            }
        }
    }

    // ---------------- LOGIC ----------------

    void update() {
        frame++;
        int k = getKeyStates();

        if (state != PLAY) {
            stateTimer++;
            if (stateTimer > 25 && (k & FIRE_PRESSED) != 0) reset();
            return;
        }

        if (pinv > 0) pinv--;

        if ((k & LEFT_PRESSED) != 0) { px -= 3; pdir = -1; walkAnim++; }
        if ((k & RIGHT_PRESSED) != 0) { px += 3; pdir = 1; walkAnim++; }
        if (px < 0) px = 0;

        if ((k & UP_PRESSED) != 0 && jh == 0 && jv == 0) jv = 11;
        if (jh > 0 || jv > 0) {
            jh += jv;
            jv -= 1;
            if (jh <= 0) { jh = 0; jv = 0; }
        }

        cam = px - W / 3;
        if (cam < 0) cam = 0;

        // shooting and reload
        long now = System.currentTimeMillis();
        if (reloading && now >= reloadEnd) { reloading = false; ammo = CLIP; }
        if (fireCd > 0) fireCd--;
        if ((k & FIRE_PRESSED) != 0 && fireCd == 0 && !reloading && ammo > 0) {
            for (int i = 0; i < MAXB; i++) {
                if (bl[i] <= 0) {
                    bx[i] = px + (pdir > 0 ? PW + 6 : -6);
                    bo[i] = jh + 17;
                    bd[i] = pdir;
                    bl[i] = 20;
                    break;
                }
            }
            ammo--;
            fireCd = 6;
            if (ammo == 0) { reloading = true; reloadEnd = now + 5000; }
        }

        // bullets
        for (int i = 0; i < MAXB; i++) {
            if (bl[i] > 0) {
                bx[i] += 8 * bd[i];
                bl[i]--;
                if (eOn && bx[i] + 4 > ex && bx[i] < ex + eW && bo[i] >= eh && bo[i] <= eh + eH) {
                    bl[i] = 0;
                    ehp -= 10;
                    eFlash = 3;
                    if (ehp <= 0) enemyDied();
                }
            }
        }

        // story triggers
        if (phase == 0 && px > 150) spawn(0);
        else if (phase == 2 && px > botX + 60) spawn(1);
        else if (phase == 4 && px > botX + 60) spawn(2);

        if (eOn) updateEnemy();

        // boss shot
        if (shOn) {
            shx += 4 * shd;
            if (Math.abs(shx - px) > W) {
                shOn = false;
            } else if (shx + 6 > px && shx < px + PW && sho + 3 >= jh && sho - 3 <= jh + PH) {
                shOn = false;
                hitPlayer(8);
            }
        }

        // water bottle (must jump to grab it)
        if (botOn) {
            int pc = px + PW / 2;
            if (Math.abs(pc - botX) < 14 && jh + PH >= 40 && jh <= 52) {
                botOn = false;
                php = Math.min(100, php + 35);
            }
        }
    }

    void spawn(int t) {
        eType = t;
        eOn = true;
        ex = px + W * 2 / 3 + 20;
        eh = 0; ehv = 0; eFlash = 0; eAtk = 20; eSwing = 0; eAcc = 0;
        bMode = 0; bTimer = 90; bShot = 60;
        shOn = false;
        if (t == 0) { eW = 34; eH = 54; ehp = 60; emax = 60; eSpd = 10; eDmg = 8; }
        else if (t == 1) { eW = 34; eH = 54; ehp = 90; emax = 90; eSpd = 15; eDmg = 10; }
        else { eW = 40; eH = 62; ehp = 250; emax = 250; eSpd = 22; eDmg = 12; }
        phase++;
    }

    void enemyDied() {
        eOn = false;
        shOn = false;
        if (phase == 1 || phase == 3) {
            botX = px + 110;
            botOn = true;
            phase++;
        } else if (phase == 5) {
            state = WIN;
            stateTimer = 0;
        }
    }

    void walk() {
        eAcc += eSpd;
        while (eAcc >= 10) { ex += edir; eAcc -= 10; }
    }

    void updateEnemy() {
        if (eFlash > 0) eFlash--;
        int pc = px + PW / 2;
        int ec = ex + eW / 2;
        int dist = Math.abs(pc - ec);
        edir = (pc >= ec) ? 1 : -1;
        int reach = (PW + eW) / 2 + 2;
        if (eSwing > 0) eSwing--;

        if (eType < 2) {
            // ladies walk to you, then hit non-stop
            if (dist > reach) {
                walk();
                eAtk = 15;
            } else {
                if (eAtk > 0) eAtk--;
                if (eAtk == 0) {
                    eSwing = 8;
                    eAtk = 25;
                    if (jh < 20) hitPlayer(eDmg);
                }
            }
        } else {
            // boss
            bTimer--;
            bShot--;
            if (bMode == 0) {
                if (dist > reach) {
                    walk();
                    eAtk = 12;
                } else {
                    if (eAtk > 0) eAtk--;
                    if (eAtk == 0) {
                        eSwing = 8;
                        eAtk = 24;
                        if (jh < 20) hitPlayer(eDmg);
                    }
                }
                if (bTimer <= 0 && dist > 60) {
                    bMode = 1;
                    bTimer = 18;
                } else if (bShot <= 0 && dist > 110) {
                    shOn = true;
                    shx = (edir > 0) ? ex + eW : ex - 6;
                    sho = 30;
                    shd = edir;
                    bShot = 70;
                }
            } else if (bMode == 1) {
                // warning flash, then jump
                if (bTimer <= 0) {
                    int v = dist / 24;
                    if (v < 2) v = 2;
                    if (v > 6) v = 6;
                    bvx = edir * v;
                    ehv = 12;
                    bMode = 2;
                }
            } else {
                ex += bvx;
                eh += ehv;
                ehv -= 1;
                if (eh <= 0 && ehv < 0) {
                    eh = 0; ehv = 0; bMode = 0; bTimer = 110;
                    if (Math.abs(pc - (ex + eW / 2)) < 50 && jh < 25) hitPlayer(18);
                }
            }
        }
    }

    void hitPlayer(int d) {
        if (pinv > 0) return;
        php -= d;
        pinv = 12;
        if (php <= 0) {
            php = 0;
            state = OVER;
            stateTimer = 0;
        }
    }

    // ---------------- DRAWING ----------------

    void draw(Graphics g) {
        g.setColor(0x87CEEB);
        g.fillRect(0, 0, W, H);

        // clouds
        int base = cam / 4;
        int c0 = base / 160;
        int off = base % 160;
        for (int i = 0; i < W / 160 + 3; i++) {
            int idx = c0 + i;
            cloud(g, i * 160 - off + 20, 24 + ((idx * 37) % 3) * 18);
        }

        // ground
        g.setColor(0x3E8E41);
        g.fillRect(0, groundY, W, H - groundY);
        g.setColor(0x2B6B30);
        g.fillRect(0, groundY, W, 3);

        // palm trees
        int n0 = cam / 130;
        for (int i = -1; i < W / 130 + 3; i++) {
            int n = n0 + i;
            if (n < 0) continue;
            int wx = n * 130 + 30 + ((n * 53) % 40);
            palm(g, wx - cam, 46 + ((n * 17) % 3) * 8);
        }

        // rocks
        int m0 = cam / 97;
        for (int i = -1; i < W / 97 + 3; i++) {
            int m = m0 + i;
            if (m < 0 || (m & 1) == 1) continue;
            int wx = m * 97 + ((m * 29) % 50);
            rock(g, wx - cam);
        }

        if (botOn) drawBottle(g);

        if (eOn) {
            int sx = ex - cam;
            int fy = groundY - eh;
            if (eType == 2) {
                if (eh > 0) {
                    g.setColor(0x1B5E20);
                    g.fillArc(sx + 4, groundY - 3, eW - 8, 6, 0, 360);
                }
                drawBoss(g, sx, fy);
            } else {
                drawLady(g, sx, fy);
            }
        }

        drawPlayer(g);

        // bullets
        g.setColor(0xFFEB3B);
        for (int i = 0; i < MAXB; i++) {
            if (bl[i] > 0) g.fillRect(bx[i] - cam, groundY - bo[i] - 1, 5, 3);
        }
        if (shOn) {
            g.setColor(0x76FF03);
            g.fillArc(shx - cam, groundY - sho - 3, 7, 7, 0, 360);
        }

        drawHud(g);

        if (state != PLAY) {
            g.setColor(0x000000);
            g.fillRect(W / 2 - 70, H / 2 - 30, 140, 56);
            g.setColor(0xFFFFFF);
            g.drawRect(W / 2 - 70, H / 2 - 30, 139, 55);
            g.drawString(state == OVER ? "GAME OVER" : "YOU WIN!", W / 2, H / 2 - 26, Graphics.TOP | Graphics.HCENTER);
            g.drawString("Press FIRE", W / 2, H / 2 - 8, Graphics.TOP | Graphics.HCENTER);
            g.drawString("to play again", W / 2, H / 2 + 6, Graphics.TOP | Graphics.HCENTER);
        }
    }

    void drawHud(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(3, 3, 72, 10);
        g.setColor(0xD32F2F);
        g.fillRect(4, 4, php * 70 / 100, 8);
        g.setColor(0x000000);
        g.drawString("HP", 78, 2, Graphics.TOP | Graphics.LEFT);

        String a;
        if (reloading) {
            long left = (reloadEnd - System.currentTimeMillis()) / 1000 + 1;
            a = "RELOAD " + left;
        } else {
            a = "AMMO " + ammo;
        }
        g.drawString(a, 4, 16, Graphics.TOP | Graphics.LEFT);

        if (eOn) {
            g.setColor(0x000000);
            g.fillRect(W - 75, 3, 72, 10);
            g.setColor(eType == 2 ? 0x2E7D32 : 0xFFC107);
            g.fillRect(W - 74, 4, ehp * 70 / emax, 8);
            g.setColor(0x000000);
            g.drawString(eType == 2 ? "BOSS" : "ENEMY", W - 4, 16, Graphics.TOP | Graphics.RIGHT);
        } else if (state == PLAY && ((frame >> 3) & 1) == 0) {
            g.setColor(0x000000);
            g.drawString("GO >>", W - 4, H / 2 - 20, Graphics.TOP | Graphics.RIGHT);
        }
    }

    void cloud(Graphics g, int x, int y) {
        g.setColor(0xFFFFFF);
        g.fillArc(x, y + 4, 34, 14, 0, 360);
        g.fillArc(x + 10, y, 26, 16, 0, 360);
        g.fillArc(x + 22, y + 5, 32, 12, 0, 360);
    }

    void palm(Graphics g, int x, int h) {
        int top = groundY - h;
        g.setColor(0x8B5A2B);
        g.fillRect(x, top, 5, h);
        g.setColor(0x6D4520);
        g.fillRect(x, top + 8, 5, 2);
        g.fillRect(x, top + 20, 5, 2);
        g.setColor(0x1E8E3E);
        g.fillArc(x - 18, top - 8, 24, 14, 0, 180);
        g.fillArc(x, top - 8, 24, 14, 0, 180);
        g.setColor(0x2FAE52);
        g.fillArc(x - 8, top - 14, 22, 14, 0, 180);
    }

    void rock(Graphics g, int x) {
        g.setColor(0x7A7A7A);
        g.fillArc(x, groundY - 9, 22, 18, 0, 180);
        g.setColor(0x9A9A9A);
        g.fillArc(x + 4, groundY - 8, 8, 6, 0, 180);
    }

    void drawBottle(Graphics g) {
        int sx = botX - cam;
        int bob = ((frame >> 2) & 1) * 2;
        int y = groundY - 52 - bob;
        g.setColor(0x4FC3F7);
        g.fillRect(sx - 4, y + 4, 8, 14);
        g.setColor(0x1565C0);
        g.fillRect(sx - 2, y, 4, 4);
        g.setColor(0xFFFFFF);
        g.fillRect(sx - 3, y + 9, 6, 3);
    }

    void drawPlayer(Graphics g) {
        if (pinv > 0 && (frame & 2) == 0) return;
        int sx = px - cam;
        int top = groundY - jh - PH;
        int a = ((walkAnim >> 2) & 1) * 2;
        if (jh > 0) a = 1;

        g.setColor(0x0B1F5C);
        g.fillRect(sx + 2, top + 20, 4, 10 - a);
        g.fillRect(sx + 8, top + 20, 4, 8 + a);
        g.setColor(0x1E4FD8);
        g.fillRect(sx + 1, top + 10, 12, 11);
        g.setColor(0xFFFFFF);
        g.fillRect(sx + 5, top + 10, 4, 5);
        g.setColor(0xD32F2F);
        g.fillRect(sx + 6, top + 11, 2, 5);
        g.setColor(0xF1C27D);
        g.fillRect(sx + 3, top + 1, 8, 9);
        g.setColor(0x222222);
        g.fillRect(sx + 3, top, 8, 3);
        g.fillRect(sx + (pdir > 0 ? 8 : 4), top + 5, 2, 2);

        g.setColor(0x1E4FD8);
        if (pdir > 0) {
            g.fillRect(sx + 9, top + 12, 6, 3);
            g.setColor(0x111111);
            g.fillRect(sx + 14, top + 11, 7, 3);
            g.fillRect(sx + 14, top + 14, 2, 3);
            if (fireCd > 3) {
                g.setColor(0xFFEB3B);
                g.fillRect(sx + 21, top + 10, 4, 5);
            }
        } else {
            g.fillRect(sx - 1, top + 12, 6, 3);
            g.setColor(0x111111);
            g.fillRect(sx - 7, top + 11, 7, 3);
            g.fillRect(sx - 2, top + 14, 2, 3);
            if (fireCd > 3) {
                g.setColor(0xFFEB3B);
                g.fillRect(sx - 11, top + 10, 4, 5);
            }
        }
    }

    void drawLady(Graphics g, int sx, int fy) {
        int top = fy - eH;
        int dress = (eType == 1) ? 0x8E24AA : 0xE91E63;
        if (eFlash > 0) dress = 0xFFFFFF;
        int skin = 0xF1C27D;

        g.setColor(skin);
        g.fillRect(sx + 9, top + 46, 6, 8);
        g.fillRect(sx + 19, top + 46, 6, 8);
        g.fillRect(sx, top + 18, 4, 14);
        g.fillRect(sx + eW - 4, top + 18, 4, 14);
        if (eSwing > 0) {
            int fx = (edir > 0) ? sx + eW - 2 : sx - 10;
            g.fillRect(fx, top + 20 + ((eSwing > 4) ? -6 : 4), 12, 5);
        }
        g.setColor(dress);
        g.fillArc(sx + 1, top + 30, 32, 18, 0, 360);
        g.fillRect(sx + 12, top + 26, 10, 8);
        g.fillArc(sx + 5, top + 14, 24, 16, 0, 360);
        g.setColor(0x3B2314);
        g.fillArc(sx + 9, top, 16, 14, 0, 360);
        g.setColor(skin);
        g.fillArc(sx + 11, top + 3, 12, 12, 0, 360);
        g.setColor(0x000000);
        g.fillRect(sx + (edir > 0 ? 18 : 13), top + 8, 2, 2);
    }

    void drawBoss(Graphics g, int sx, int fy) {
        int top = fy - eH;
        int skin = 0x2E9E2E;
        if (eFlash > 0) skin = 0xFFFFFF;
        else if (bMode == 1 && (frame & 2) == 0) skin = 0xC6FF00;

        g.setColor(0x5A3A1A);
        g.fillRect(sx + 6, top + 44, 28, 10);
        g.setColor(skin);
        g.fillRect(sx + 8, top + 54, 10, 8);
        g.fillRect(sx + 22, top + 54, 10, 8);
        g.fillRect(sx + 6, top + 18, 28, 26);
        g.fillRect(sx, top + 18, 8, 24);
        g.fillRect(sx + 32, top + 18, 8, 24);
        g.fillRect(sx + 13, top + 4, 14, 14);
        if (eSwing > 0) {
            int fx = (edir > 0) ? sx + eW - 2 : sx - 14;
            g.fillRect(fx, top + 22 + ((eSwing > 4) ? -8 : 4), 16, 9);
        }
        g.setColor(0x111111);
        g.fillRect(sx + 12, top + 2, 16, 5);
        g.setColor(0xFFFFFF);
        g.fillRect(sx + 15, top + 9, 3, 3);
        g.fillRect(sx + 22, top + 9, 3, 3);
        g.setColor(0x000000);
        g.fillRect(sx + 14, top + 7, 5, 2);
        g.fillRect(sx + 21, top + 7, 5, 2);
    }
}
