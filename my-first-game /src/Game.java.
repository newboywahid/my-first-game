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
    private boolean running = true;
    private int x = 50, y = 50;

    GameScreen() { super(true); }

    public void stop() { running = false; }

    public void run() {
        Graphics g = getGraphics();
        while (running) {
            int k = getKeyStates();
            if ((k & LEFT_PRESSED) != 0) x -= 3;
            if ((k & RIGHT_PRESSED) != 0) x += 3;
            if ((k & UP_PRESSED) != 0) y -= 3;
            if ((k & DOWN_PRESSED) != 0) y += 3;

            g.setColor(0xFFFFFF);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xFF0000);
            g.fillRect(x, y, 10, 10);
            flushGraphics();

            try { Thread.sleep(30); } catch (InterruptedException e) {}
        }
    }
}
