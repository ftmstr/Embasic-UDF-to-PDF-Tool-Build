package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * CI-safe Swing EDT component-load smoke (not a physical Windows GUI benchmark).
 * Models the real virtual grid's viewport mounting and emits observable metrics.
 */
final class VirtualGridSwingLoadTest {
    @Test void scrollZoomAndResizeKeepMountedSwingComponentsBounded() throws Exception {
        AtomicInteger maximumMounted=new AtomicInteger();
        AtomicInteger viewports=new AtomicInteger();
        AtomicLong totalNanos=new AtomicLong();
        AtomicLong memoryDelta=new AtomicLong();
        SwingUtilities.invokeAndWait(() -> {
            long startingHeap=Runtime.getRuntime().totalMemory()-
                    Runtime.getRuntime().freeMemory();
            JPanel grid=new JPanel(null);
            JScrollPane scroll=new JScrollPane(grid);
            for(int width:new int[]{680,1000,1400}) {
                scroll.setSize(width,650);
                scroll.doLayout();
                for(int zoom:new int[]{132,188,272}) {
                    VirtualGridLayout geometry=new VirtualGridLayout(161,width,zoom);
                    Dimension size=geometry.preferredSize();
                    grid.setPreferredSize(size);
                    grid.setSize(size);
                    scroll.getViewport().setViewSize(size);
                    for(int position=0;position<size.height;position+=180) {
                        long before=System.nanoTime();
                        int clamped=Math.min(position,Math.max(0,
                                size.height-scroll.getViewport().getExtentSize().height));
                        scroll.getViewport().setViewPosition(new Point(0,clamped));
                        Rectangle viewport=scroll.getViewport().getViewRect();
                        VirtualGridLayout.Visible visible=geometry.visible(viewport);
                        grid.removeAll();
                        for(int i=visible.startInclusive();i<visible.endExclusive();i++) {
                            JPanel tile=new JPanel();
                            tile.setBounds(geometry.bounds(i));
                            grid.add(tile);
                        }
                        assertEquals(visible.count(),grid.getComponentCount());
                        assertTrue(grid.getComponentCount()<=geometry.columns()*10);
                        maximumMounted.accumulateAndGet(grid.getComponentCount(),Math::max);
                        viewports.incrementAndGet();
                        totalNanos.addAndGet(System.nanoTime()-before);
                    }
                }
            }
            long finishingHeap=Runtime.getRuntime().totalMemory()-
                    Runtime.getRuntime().freeMemory();
            memoryDelta.set(finishingHeap-startingHeap);
        });
        assertTrue(viewports.get()>40,"Scroll coverage missing");
        assertTrue(maximumMounted.get()<100,"Virtualization must not mount all 160 cards");
        System.out.printf("SWING-VIRTUAL-GRID: viewportSamples=%d peakMounted=%d " +
                    "avgUpdateMs=%.3f observedHeapDeltaMiB=%.2f%n",
                viewports.get(),maximumMounted.get(),
                totalNanos.get()/1_000_000.0/viewports.get(),
                memoryDelta.get()/1048576.0);
    }
}
