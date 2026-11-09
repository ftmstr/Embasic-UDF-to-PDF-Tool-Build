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
    /**
     * Synthetic slow font/raster task stays on a background worker while
     * 160-card selection/reordering and viewport scheduling stay on EDT.
     * CI/headless semantic gate only: this is not a physical Robot GUI run.
     */
    @Test void blockedPreviewWorkerNeverBlocksEdtSelectionAndReorder() throws Exception {
        java.util.List<Integer> cards = java.util.stream.IntStream.range(0, 160)
                .boxed().toList();
        VirtualGridLayout geometry = new VirtualGridLayout(161, 900,
                ThumbnailSizing.DEFAULT_WIDTH);
        var visible = geometry.visible(new Rectangle(0, 0, 900, 600));
        assertTrue(visible.count() > 0);
        var executor = new java.util.concurrent.ThreadPoolExecutor(1, 1,
                0, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingDeque<Runnable>(192),
                task -> {
                    Thread thread = new Thread(task, "embasic-test-font-preview");
                    thread.setDaemon(true);
                    return thread;
                });
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var completed = new java.util.concurrent.CountDownLatch(visible.count());
        var scheduled = new AtomicInteger();
        var ranOnEdt = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            var blocker = executor.submit(() -> {
                entered.countDown();
                try {
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("Synthetic font worker was not released");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            });
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                DesktopApp.scheduleVisiblePreviews(cards, visible, card -> true, card -> {
                    scheduled.incrementAndGet();
                    executor.execute(() -> {
                        if (SwingUtilities.isEventDispatchThread()) ranOnEdt.set(true);
                        completed.countDown();
                    });
                });
                assertEquals(visible.count(), scheduled.get());
                // Font worker is still busy. Keyboard selection and reordering
                // must not call the PDF renderer on EDT or wait for it.
                var all = GridKeyboardSelection.selectAll(cards, 100);
                assertEquals(160, all.selected().size());
                assertEquals(100, all.focused());
                assertEquals(160, GridReorder.step(cards,
                        java.util.List.of(3, 100), 1).size());
            });
            assertEquals(visible.count(), completed.getCount(),
                    "No preview rendering should start on EDT while the worker is occupied");
            release.countDown();
            blocker.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(ranOnEdt.get(), "Preview work must never execute on the Swing EDT");
            System.out.printf("EMBASIC_EDT_PREVIEW_QUEUE logicalCards=160 "
                    + "submitted=%d completed=%d workerOffEdt=PASS%n",
                    scheduled.get(), visible.count());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

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
