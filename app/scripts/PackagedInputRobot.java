import java.awt.GraphicsEnvironment;
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
            throw new IllegalArgumentException("Expected focus x y, open, or cancel");
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
            default -> throw new IllegalArgumentException("Unknown Robot action");
        }
    }
}
