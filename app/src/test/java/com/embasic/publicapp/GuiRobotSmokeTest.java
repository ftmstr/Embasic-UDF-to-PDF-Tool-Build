package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Explicitly opt-in, physical Windows Robot test against the REAL DesktopApp.
 * Default Maven/JUnit never reports this as a passed Windows GUI acceptance.
 *
 * Run on an interactive desktop with
 * mvn -f app/pom.xml -Dtest=GuiRobotSmokeTest -Dembasic.gui.robot=true test
 */
@EnabledOnOs(OS.WINDOWS)
final class GuiRobotSmokeTest {
    @TempDir Path folder;

    private static Object field(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }
    private Path makeFixture(int index) throws Exception {
        Path path=folder.resolve(String.format("synthetic-%02d.udf",index));
        String xml="<template format_id='1.0'><content>Tur test</content>"
                +"<elements><paragraph><content startOffset='0' length='8'/>"
                +"</paragraph></elements></template>";
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }
    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    // Physical key delivery, not Swing ActionMap invocation: prove the actual
    // Windows keyboard shortcuts reach the focused, packaged-style frame.
    private static void chord(Robot robot, int modifier, int key) {
        robot.keyPress(modifier);
        try {
            robot.keyPress(key);
            robot.keyRelease(key);
        } finally {
            robot.keyRelease(modifier);
        }
    }

    /** Detect only the file chooser owned by this MVP window, never another app dialog. */
    private static boolean containsFileChooser(java.awt.Component component) {
        if (component instanceof javax.swing.JFileChooser) return true;
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents())
                if (containsFileChooser(child)) return true;
        }
        return false;
    }
    private static boolean isMvpChooserVisible(DesktopApp app) {
        for (java.awt.Window window : java.awt.Window.getWindows())
            if (window instanceof javax.swing.JDialog dialog && dialog.isShowing()
                    && dialog.getOwner() == app && containsFileChooser(dialog))
                return true;
        return false;
    }

    /** The chooser belongs to the tested window, not another desktop process. */
    private static javax.swing.JFileChooser activeMvpChooser(DesktopApp app) {
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            if (!(window instanceof javax.swing.JDialog dialog)
                    || !dialog.isShowing() || dialog.getOwner() != app) continue;
            java.util.ArrayDeque<java.awt.Component> pending = new java.util.ArrayDeque<>();
            pending.add(dialog);
            while (!pending.isEmpty()) {
                java.awt.Component next = pending.removeFirst();
                if (next instanceof javax.swing.JFileChooser chooser) return chooser;
                if (next instanceof java.awt.Container container)
                    for (java.awt.Component child : container.getComponents()) pending.addLast(child);
            }
        }
        return null;
    }

    private static javax.swing.JButton approveButton(java.awt.Component root, String text) {
        if (root instanceof javax.swing.JButton button && text.equals(button.getText())
                && button.isShowing() && button.isEnabled()) return button;
        if (root instanceof java.awt.Container container)
            for (java.awt.Component child : container.getComponents()) {
                javax.swing.JButton found = approveButton(child, text);
                if (found != null) return found;
            }
        return null;
    }

    @Test @Timeout(85)
    void realWindowDragAutoScrollAndAccessibleTarget() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("embasic.gui.robot"),
                "Real Windows Robot test is manual opt-in; do not count as GUI acceptance");
        assertFalse(GraphicsEnvironment.isHeadless(),
                "No interactive Windows desktop; Robot acceptance cannot be claimed");
        Robot robot;
        try { robot=new Robot(); }
        catch(AWTException ex) { throw new AssertionError(
                "Interactive Windows Robot unavailable; GUI acceptance inconclusive",ex); }
        robot.setAutoDelay(15);
        // Distinguish an unusable/locked desktop from a successful GUI test.
        Rectangle desktop=GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        assertTrue(desktop.width>=900 && desktop.height>=650,
                "Physical Windows GUI acceptance requires a usable, unlocked >=900x650 desktop");
        List<Path> source=new ArrayList<>();
        for(int i=0;i<40;i++)source.add(makeFixture(i));
        AtomicReference<DesktopApp> appRef=new AtomicReference<>();
        boolean pressed=false;
        try {
            edt(()-> {
                try {
                    var constructor=DesktopApp.class.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    DesktopApp app=constructor.newInstance();
                    app.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
                    app.setVisible(true);
                    var importMethod=DesktopApp.class.getDeclaredMethod("addFiles",List.class);
                    importMethod.setAccessible(true);
                    importMethod.invoke(app,source);
                    appRef.set(app);
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            // Preview workers may continue posting Swing events. Verify the
            // actual window state with a bounded EDT poll instead of waiting
            // for the event queue to become permanently idle.
            DesktopApp app=appRef.get();
            GuiRobotWait.until(Duration.ofSeconds(8),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        JScrollPane viewport=(JScrollPane)field(app,"scroll");
                        JPanel cards=(JPanel)field(app,"grid");
                        return app.isShowing() && viewport.getViewport().getWidth()>0
                                && cards.getComponentCount()>0;
                    }),Boolean.TRUE::equals);
            JScrollPane scroll=(JScrollPane)field(app,"scroll");
            JPanel grid=(JPanel)field(app,"grid");
            JLabel feedback=(JLabel)field(app,"dragFeedback");
            @SuppressWarnings("unchecked")
            List<Object> items=(List<Object>)field(app,"items");
            assertEquals(source.size(),items.size());
            Object first=items.get(0);

            VirtualGridLayout geometry=GuiRobotWait.onEdt(
                    ()->(VirtualGridLayout)field(app,"virtualGrid"));
            assertNotNull(geometry, "Live virtual layout must be initialized");
            Rectangle start=geometry.bounds(0);
            Point initial=SwingUtilities.convertPoint(grid,
                    new Point(start.x+start.width/4,start.y+start.height/2),
                    scroll.getViewport());
            Point viewportScreen=scroll.getViewport().getLocationOnScreen();
            int mouseX=viewportScreen.x+initial.x;
            int firstY=viewportScreen.y+initial.y;
            int edgeY=viewportScreen.y+scroll.getViewport().getHeight()-9;
            robot.mouseMove(mouseX,firstY);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            pressed=true;
            for(int i=1;i<=16;i++) {
                robot.mouseMove(mouseX,firstY+(edgeY-firstY)*i/16);
                robot.delay(32);
            }
            // Autoscroll speed varies with Windows DPI and runner performance.
            // Poll a real Swing property within a fixed deadline.
            GuiRobotWait.until(Duration.ofSeconds(8),Duration.ofMillis(70),
                    ()->GuiRobotWait.onEdt(
                            ()->scroll.getVerticalScrollBar().getValue()),
                    value->value>0);

            Rectangle view=GuiRobotWait.onEdt(
                    ()->scroll.getViewport().getViewRect());
            geometry=GuiRobotWait.onEdt(
                    ()->(VirtualGridLayout)field(app,"virtualGrid"));
            int target=-1;
            for(int i=1;i<items.size();i++) {
                Rectangle bounds=geometry.bounds(i);
                if(bounds.y>view.y+20 && bounds.y+bounds.height<view.y+view.height-15) {
                    target=i; break;
                }
            }
            assertTrue(target>0,"No fully visible drop destination after scrolling");
            final int targetIndex=target;
            Rectangle destination=geometry.bounds(targetIndex);
            Point destInViewport=SwingUtilities.convertPoint(grid,
                    new Point(destination.x+destination.width/4,
                            destination.y+destination.height/2),scroll.getViewport());
            viewportScreen=scroll.getViewport().getLocationOnScreen();
            robot.mouseMove(viewportScreen.x+destInViewport.x,
                    viewportScreen.y+destInViewport.y);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        int index=(Integer)field(app,"dropTargetIndex");
                        String hint=feedback.getText();
                        return index==targetIndex && hint.contains((targetIndex+1)+". kartın");
                    }),Boolean.TRUE::equals);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            pressed=false;
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(
                        ()->items.indexOf(first)>0 && feedback.getText().isEmpty()),
                    Boolean.TRUE::equals);

            // WGUI-02/03: after a real pointer reorder, exercise the actual
            // keyboard route through the 40-card logical (partly virtual) grid.
            // Do not substitute direct ActionMap calls for physical key events.
            javax.swing.JSlider zoom=(javax.swing.JSlider)field(app,"thumbnailZoom");
            JLabel count=(JLabel)field(app,"selectionCount");
            int originalZoom=GuiRobotWait.onEdt(zoom::getValue);
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_A);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> selection=(java.util.Set<Object>)field(app,"selected");
                        return selection.size()==source.size()
                                && count.getText().equals("Seçili: 40 / 40");
                    }),Boolean.TRUE::equals);
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_END);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->field(app,"focused")==items.get(items.size()-1)),
                    Boolean.TRUE::equals);
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_HOME);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->field(app,"focused")==items.get(0)),
                    Boolean.TRUE::equals);
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_MINUS);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->zoom.getValue()<originalZoom),
                    Boolean.TRUE::equals);
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_0);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> selection=(java.util.Set<Object>)field(app,"selected");
                        return zoom.getValue()==originalZoom
                                && selection.size()==source.size()
                                && count.getText().equals("Seçili: 40 / 40");
                    }),Boolean.TRUE::equals);

            // WGUI-02: actual Ctrl+Space must toggle the focused first card
            // without clearing the other 39 cards. A second toggle restores it.
            // The test deliberately checks the live selection and status label;
            // a mere ActionMap binding would not prove physical key delivery.
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_SPACE);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> selection=(java.util.Set<Object>)field(app,"selected");
                        return selection.size()==source.size()-1
                                && !selection.contains(items.get(0))
                                && count.getText().equals("Seçili: 39 / 40");
                    }),Boolean.TRUE::equals);
            chord(robot,KeyEvent.VK_CONTROL,KeyEvent.VK_SPACE);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> selection=(java.util.Set<Object>)field(app,"selected");
                        return selection.size()==source.size()
                                && selection.contains(items.get(0))
                                && count.getText().equals("Seçili: 40 / 40");
                    }),Boolean.TRUE::equals);

            // A plain arrow must collapse selection to the next card, while
            // Shift+End selects the full logical range through offscreen cards.
            robot.keyPress(KeyEvent.VK_RIGHT);
            robot.keyRelease(KeyEvent.VK_RIGHT);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> selection=(java.util.Set<Object>)field(app,"selected");
                        return selection.size()==1 && selection.contains(items.get(1))
                                && field(app,"focused")==items.get(1);
                    }),Boolean.TRUE::equals);
            chord(robot,KeyEvent.VK_SHIFT,KeyEvent.VK_END);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> selection=(java.util.Set<Object>)field(app,"selected");
                        return selection.size()==source.size()-1
                                && !selection.contains(items.get(0))
                                && selection.contains(items.get(items.size()-1))
                                && field(app,"focused")==items.get(items.size()-1)
                                && count.getText().equals("Seçili: 39 / 40");
                    }),Boolean.TRUE::equals);
            // Shift+End selected 39 of 40 cards above. Delete them through
            // the real keyboard route to expose the viewport's empty area;
            // testing only a full grid could hide the dark-theme white strip.
            Object survivor=items.get(0);
            robot.keyPress(KeyEvent.VK_DELETE);
            robot.keyRelease(KeyEvent.VK_DELETE);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        @SuppressWarnings("unchecked")
                        java.util.Set<Object> remaining=(java.util.Set<Object>)field(app,"selected");
                        return items.size()==1 && items.get(0)==survivor
                                && remaining.size()==1 && remaining.contains(survivor)
                                && count.getText().equals("Seçili: 1 / 1");
                    }),Boolean.TRUE::equals);

            // WGUI-06: use real Windows pointer events to toggle the visible
            // theme control. A palette helper test alone cannot prove that the
            // actual button updates the scroll pane, viewport and short-grid
            // background together. Keep the test opt-in on an unlocked desktop.
            javax.swing.JButton themeButton=(javax.swing.JButton)field(app,"themeButton");
            java.awt.Color originalBackground=GuiRobotWait.onEdt(
                    ()->scroll.getViewport().getBackground());
            Point themeScreen=GuiRobotWait.onEdt(()->{
                Point location=themeButton.getLocationOnScreen();
                return new Point(location.x+themeButton.getWidth()/2,
                        location.y+themeButton.getHeight()/2);
            });
            robot.mouseMove(themeScreen.x,themeScreen.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        java.awt.Color next=scroll.getViewport().getBackground();
                        return !next.equals(originalBackground)
                                && next.equals(scroll.getBackground())
                                && next.equals(grid.getBackground())
                                && themeButton.getText().equals("Karanlık tema");
                    }),Boolean.TRUE::equals);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        java.awt.Color restored=scroll.getViewport().getBackground();
                        return restored.equals(originalBackground)
                                && restored.equals(scroll.getBackground())
                                && restored.equals(grid.getBackground())
                                && themeButton.getText().equals("Aydınlık tema");
                    }),Boolean.TRUE::equals);

            // WGUI-07: the in-grid + UDF tile must remain reachable after
            // Delete, thumbnail zoom and theme rebuild. Verify a real pointer
            // click and real Enter/Space key events open the actual JFileChooser,
            // then cancel each dialog without importing any user files.
            JPanel plus=GuiRobotWait.onEdt(()->DesktopApp.existingAddTile(grid));
            assertNotNull(plus,"The + UDF tile must remain in the short grid");
            assertTrue(GuiRobotWait.onEdt(plus::isEnabled),
                    "The + tile must be enabled with only one UDF card");
            assertEquals("+ UDF ekle",GuiRobotWait.onEdt(
                    ()->plus.getAccessibleContext().getAccessibleName()));
            GuiRobotWait.onEdt(()->{
                plus.scrollRectToVisible(new Rectangle(0,0,plus.getWidth(),plus.getHeight()));
                return true;
            });
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->{
                        Rectangle tile=plus.getBounds();
                        Rectangle visible=scroll.getViewport().getViewRect();
                        return plus.isShowing() && tile.width>0 && tile.height>0
                                && visible.contains(tile);
                    }),Boolean.TRUE::equals);
            Point addScreen=GuiRobotWait.onEdt(()->{
                Point p=plus.getLocationOnScreen();
                return new Point(p.x+plus.getWidth()/2,p.y+plus.getHeight()/2);
            });
            robot.mouseMove(addScreen.x,addScreen.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->isMvpChooserVisible(app)),
                    Boolean.TRUE::equals);
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->!isMvpChooserVisible(app)),
                    Boolean.TRUE::equals);
            for(int activationKey : new int[]{KeyEvent.VK_ENTER,KeyEvent.VK_SPACE}) {
                // The application, not the test, must restore tile focus
                // after a cancelled native chooser. Do NOT request focus from
                // the Robot test: that would conceal a real keyboard defect.
                GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                        ()->GuiRobotWait.onEdt(()->{
                            if (!app.isFocused()) return "OWNER_NOT_FOCUSED";
                            if (!plus.isShowing()) return "ADD_TILE_NOT_SHOWING";
                            if (!plus.isEnabled()) return "ADD_TILE_DISABLED";
                            return plus.isFocusOwner() ? "FOCUSED" : "FOCUS_NOT_RESTORED";
                        }),"FOCUSED"::equals);
                robot.keyPress(activationKey);
                robot.keyRelease(activationKey);
                GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                        ()->GuiRobotWait.onEdt(()->isMvpChooserVisible(app)),
                        Boolean.TRUE::equals);
                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);
                GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                        ()->GuiRobotWait.onEdt(()->!isMvpChooserVisible(app)),
                        Boolean.TRUE::equals);
            }
            GuiRobotWait.until(Duration.ofSeconds(5),Duration.ofMillis(60),
                    ()->GuiRobotWait.onEdt(()->app.isFocused() && plus.isFocusOwner()),
                    Boolean.TRUE::equals);
            assertEquals(1,GuiRobotWait.onEdt(items::size),
                    "Cancelling all + tile choosers must leave the UDF queue unchanged");

            // WGUI-07 approval path: select ONLY a newly created, rights-clean
            // synthetic fixture in the actual owned JFileChooser. The Robot
            // physically clicks its enabled Approve button. This specifically
            // exercises the product's post-approval tile-focus restoration,
            // without touching user files or pretending to test native UYAP.
            Path approved = makeFixture(40);
            robot.mouseMove(addScreen.x, addScreen.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            GuiRobotWait.until(Duration.ofSeconds(5), Duration.ofMillis(60),
                    () -> GuiRobotWait.onEdt(() -> isMvpChooserVisible(app)),
                    Boolean.TRUE::equals);
            Point approveScreen = GuiRobotWait.until(Duration.ofSeconds(5),
                    Duration.ofMillis(60), () -> GuiRobotWait.onEdt(() -> {
                        javax.swing.JFileChooser chooser = activeMvpChooser(app);
                        if (chooser == null) return null;
                        // This chooser is explicitly multi-select: setting only
                        // selectedFile leaves getSelectedFiles() empty on some
                        // Windows LAFs, causing an empty approval and error dialog.
                        chooser.setCurrentDirectory(folder.toFile());
                        chooser.setSelectedFiles(new java.io.File[]{approved.toFile()});
                        if (chooser.getSelectedFiles().length != 1
                                || !approved.toFile().equals(chooser.getSelectedFiles()[0]))
                            return null;
                        String text = chooser.getUI().getApproveButtonText(chooser);
                        javax.swing.JButton button = approveButton(chooser, text);
                        if (button == null) return null;
                        Point point = button.getLocationOnScreen();
                        return new Point(point.x + button.getWidth()/2,
                                point.y + button.getHeight()/2);
                    }), java.util.Objects::nonNull);
            robot.mouseMove(approveScreen.x, approveScreen.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            GuiRobotWait.until(Duration.ofSeconds(8), Duration.ofMillis(60),
                    () -> GuiRobotWait.onEdt(() -> {
                        if (isMvpChooserVisible(app)) return "CHOOSER_STILL_OPEN";
                        if (items.size() != 2) return "QUEUE_SIZE_" + items.size();
                        if (items.get(0) != survivor) return "ORIGINAL_CARD_CHANGED";
                        if (!approved.equals(field(items.get(1), "path")))
                            return "APPROVED_PATH_MISMATCH";
                        return "APPROVED";
                    }), "APPROVED"::equals);
            GuiRobotWait.until(Duration.ofSeconds(8), Duration.ofMillis(60),
                    () -> GuiRobotWait.onEdt(() -> {
                        JPanel current = DesktopApp.existingAddTile(grid);
                        return app.isFocused() && current != null && current.isShowing()
                                && current.isEnabled() && current.isFocusOwner();
                    }), Boolean.TRUE::equals);
            assertEquals(2, GuiRobotWait.onEdt(items::size),
                    "Approved synthetic UDF must be added exactly once");

            System.out.println("WINDOWS-GUI-ROBOT-PASS: real DesktopApp mouse drag,"
                    +" edge scroll, target hint, drop order, Ctrl+A, Ctrl+End/Home,"
                    +" Ctrl+minus/zero thumbnail zoom, Ctrl+Space toggle,"
                    +" plain-arrow collapse, Shift+End offscreen range, Delete to one card,"
                    +" physical light/dark theme toggle and + tile chooser click/Enter/Space/Approve");
        } finally {
            if (pressed) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            DesktopApp app=appRef.get();
            if (app!=null) edt(app::dispose);
        }
    }
}
