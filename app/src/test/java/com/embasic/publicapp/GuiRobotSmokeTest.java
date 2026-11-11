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

    @Test @Timeout(65)
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
            System.out.println("WINDOWS-GUI-ROBOT-PASS: real DesktopApp mouse drag,"
                    +" edge scroll, target hint, drop order, Ctrl+A, Ctrl+End/Home,"
                    +" Ctrl+minus/zero thumbnail zoom and selection preservation");
        } finally {
            if (pressed) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            DesktopApp app=appRef.get();
            if (app!=null) edt(app::dispose);
        }
    }
}
