package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
            robot.waitForIdle();
            DesktopApp app=appRef.get();
            JScrollPane scroll=(JScrollPane)field(app,"scroll");
            JPanel grid=(JPanel)field(app,"grid");
            JLabel feedback=(JLabel)field(app,"dragFeedback");
            @SuppressWarnings("unchecked")
            List<Object> items=(List<Object>)field(app,"items");
            assertEquals(source.size(),items.size());
            Object first=items.get(0);

            VirtualGridLayout geometry=new VirtualGridLayout(items.size()+1,
                    scroll.getViewport().getWidth(),ThumbnailSizing.DEFAULT_WIDTH);
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
            robot.delay(900);
            robot.waitForIdle();
            final int[] scrolled={0};
            edt(()-> scrolled[0]=scroll.getVerticalScrollBar().getValue());
            assertTrue(scrolled[0]>0,"Drag near bottom did not auto-scroll real JFrame");

            Rectangle view=scroll.getViewport().getViewRect();
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
            robot.delay(200);
            robot.waitForIdle();
            final int[] indicated={-1};
            edt(()->{
                try { indicated[0]=(Integer)field(app,"dropTargetIndex"); }
                catch(Exception ex){ throw new RuntimeException(ex); }
            });
            assertEquals(targetIndex,indicated[0],
                    "Visual marker must resolve real window drop location");
            assertTrue(feedback.getText().contains((targetIndex+1)+". kartın"),
                    "Drag position is not announced in the accessible status");
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            pressed=false;
            robot.waitForIdle();
            assertTrue(items.indexOf(first)>0,"Dragged source was not reordered");
            assertEquals("",feedback.getText(),"Drop hint must be cleared after release");
            System.out.println("WINDOWS-GUI-ROBOT-PASS: real DesktopApp mouse press/drag,"
                    +" edge scroll, target marker, accessible hint and drop order");
        } finally {
            if (pressed) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            DesktopApp app=appRef.get();
            if (app!=null) edt(app::dispose);
        }
    }
}
