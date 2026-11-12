import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Real OS mouse/keyboard input against a separately launched jpackage EXE.
 * No application classes are loaded into this probe JVM.
 * No synthetic UDF or user file is opened; Ctrl+O is cancelled with Escape.
 */
public final class PackagedInputRobot {
    private PackagedInputRobot() {}

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless())
            throw new IllegalStateException("Physical Windows desktop unavailable");
        if (args.length < 1)
            throw new IllegalArgumentException("Expected focus x y, open, cancel, theme, zoom, resetzoom, pixel x y, or region x y w h");
        Robot robot = new Robot();
        robot.setAutoDelay(60);
        switch (args[0]) {
            case "focus" -> {
                if (args.length != 3) throw new IllegalArgumentException("focus x y");
                robot.mouseMove(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "open" -> {
                if (args.length != 1) throw new IllegalArgumentException("open");
                robot.keyPress(KeyEvent.VK_CONTROL);
                try {
                    robot.keyPress(KeyEvent.VK_O);
                    robot.keyRelease(KeyEvent.VK_O);
                } finally {
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                }
            }
            case "cancel" -> {
                if (args.length != 1) throw new IllegalArgumentException("cancel");
                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);
            }
            case "theme" -> {
                if (args.length != 1) throw new IllegalArgumentException("theme");
                robot.keyPress(KeyEvent.VK_CONTROL);
                try {
                    robot.keyPress(KeyEvent.VK_T);
                    robot.keyRelease(KeyEvent.VK_T);
                } finally {
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                }
            }
            case "zoom" -> {
                if (args.length != 1) throw new IllegalArgumentException("zoom");
                robot.keyPress(KeyEvent.VK_CONTROL);
                try {
                    robot.keyPress(KeyEvent.VK_EQUALS);
                    robot.keyRelease(KeyEvent.VK_EQUALS);
                } finally {
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                }
            }
            case "resetzoom" -> {
                if (args.length != 1) throw new IllegalArgumentException("resetzoom");
                robot.keyPress(KeyEvent.VK_CONTROL);
                try {
                    robot.keyPress(KeyEvent.VK_0);
                    robot.keyRelease(KeyEvent.VK_0);
                } finally {
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                }
            }
            case "region" -> {
                if (args.length != 5) throw new IllegalArgumentException("region x y w h");
                int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]);
                int width = Integer.parseInt(args[3]), height = Integer.parseInt(args[4]);
                Rectangle screen = new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
                Rectangle requested = new Rectangle(x, y, width, height);
                if (width < 1 || height < 1 || width > 400 || height > 400 ||
                        !screen.contains(requested))
                    throw new IllegalArgumentException("Region outside primary screen or over limit");
                BufferedImage pixels = robot.createScreenCapture(requested);
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                for (int yy = 0; yy < height; yy++) {
                    for (int xx = 0; xx < width; xx++) {
                        int argb = pixels.getRGB(xx, yy);
                        sha.update((byte) (argb >>> 24));
                        sha.update((byte) (argb >>> 16));
                        sha.update((byte) (argb >>> 8));
                        sha.update((byte) argb);
                    }
                }
                System.out.println(HexFormat.of().formatHex(sha.digest()));
            }
            case "pixel" -> {
                if (args.length != 3) throw new IllegalArgumentException("pixel x y");
                Color sample = robot.getPixelColor(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                System.out.printf("#%02X%02X%02X%n", sample.getRed(), sample.getGreen(), sample.getBlue());
            }
            default -> throw new IllegalArgumentException("Unknown Robot action");
        }
    }
}
