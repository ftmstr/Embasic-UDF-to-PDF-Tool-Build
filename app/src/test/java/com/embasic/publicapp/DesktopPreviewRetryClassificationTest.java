package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tur 96: The explicit preview-retry action must never reinterpret an
 * unparsed or unsupported UDF as a recoverable rasterization failure.
 * Synthetic status strings only; no native UYAP compatibility claim.
 */
final class DesktopPreviewRetryClassificationTest {
    @Test void parseCompletionBurstUsesSingleEdtRefresh() throws Exception {
        // 160 parsed UDF callbacks can arrive in one EDT burst. Rebuilding the
        // entire virtual grid for each callback causes visible UI stalls.
        CountDownLatch refreshed = new CountDownLatch(1);
        AtomicInteger rebuilds = new AtomicInteger();
        Timer refresh = new Timer(90, event -> {
            rebuilds.incrementAndGet();
            refreshed.countDown();
        });
        refresh.setRepeats(false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < 160; i++)
                    DesktopApp.requestCoalescedWorkspaceRefresh(refresh);
                assertTrue(refresh.isRunning());
                assertEquals(0, rebuilds.get(),
                        "callbacks must not synchronously rebuild the grid");
            });
            assertTrue(refreshed.await(5, TimeUnit.SECONDS),
                    "debounced EDT refresh did not execute");
            SwingUtilities.invokeAndWait(() ->
                    assertEquals(1, rebuilds.get(),
                            "all parsed UDFs must share one workspace refresh"));
        } finally {
            SwingUtilities.invokeAndWait(refresh::stop);
        }
    }

    @Test void sustainedCallbacksDoNotRestartPendingWorkspaceRefresh() throws Exception {
        // A continuous stream of parse/thumbnail completions used to restart
        // the 110 ms timer each time, postponing the first visible update.
        // Keep this deterministic: the EDT processes all callbacks in one event.
        AtomicInteger starts = new AtomicInteger();
        Timer refresh = new Timer(10_000, event -> {}) {
            @Override public void start() {
                starts.incrementAndGet();
                super.start();
            }
            @Override public void restart() {
                fail("pending refresh must not be postponed by a later callback");
            }
        };
        refresh.setRepeats(false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < 160; i++)
                    DesktopApp.requestCoalescedWorkspaceRefresh(refresh);
                assertEquals(1, starts.get(),
                        "the first callback must set the refresh deadline");
                refresh.stop(); // Simulate the one-shot timer having fired.
                DesktopApp.requestCoalescedWorkspaceRefresh(refresh);
                assertEquals(2, starts.get(),
                        "the next burst must still schedule a new refresh");
            });
        } finally {
            SwingUtilities.invokeAndWait(refresh::stop);
        }
    }

    @Test void failedPreviewIsRetryableOnlyAfterSuccessfulParse() {
        assertTrue(DesktopApp.isFailedPreview(true, "Önizleme yok: sentetik raster hatası"));
        assertFalse(DesktopApp.isFailedPreview(false, "Önizleme yok: sentetik raster hatası"));
    }

    @Test void importFailuresAndPendingPreviewsAreNotPreviewRetries() {
        assertFalse(DesktopApp.isFailedPreview(true, null));
        assertFalse(DesktopApp.isFailedPreview(true, ""));
        assertFalse(DesktopApp.isFailedPreview(true, "Desteklenmiyor: bilinmeyen UDF öğesi"));
        assertFalse(DesktopApp.isFailedPreview(true, "Önizleme hazırlanıyor..."));
        assertFalse(DesktopApp.isFailedPreview(true, "Önizleme görünür olunca hazırlanacak"));
        assertFalse(DesktopApp.isFailedPreview(true, "Önizleme kuyruğu dolu — PDF kaydedilebilir"));
    }

    @Test void delayedPreviewCallbackCannotOverwriteNewRetry() {
        Object parsed = new Object();
        Object replacement = new Object();
        assertTrue(DesktopApp.isCurrentPreview(false, 7, 7, parsed, parsed));
        assertFalse(DesktopApp.isCurrentPreview(false, 7, 8, parsed, parsed),
                "old EDT callback must be rejected after explicit retry");
        assertFalse(DesktopApp.isCurrentPreview(false, 8, 8, parsed, replacement),
                "replacement document must invalidate old thumbnail");
        assertFalse(DesktopApp.isCurrentPreview(true, 8, 8, parsed, parsed),
                "removed card must not receive a thumbnail");
    }
    @Test void queuedPreviewKeepsSubmissionGenerationAfterRetry() throws Exception {
        // One worker is deliberately occupied so the preview is queued, not running.
        // The queued task must retain its submission generation even after a retry.
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> blocker = worker.submit(() -> {
                occupied.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("synthetic worker was not released");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            });
            assertTrue(occupied.await(5, TimeUnit.SECONDS));
            Object document = new Object();
            AtomicInteger generation = new AtomicInteger(17);
            final int submittedGeneration = generation.get();
            Future<Boolean> queued = worker.submit(() -> DesktopApp.isCurrentPreview(
                    false, submittedGeneration, generation.get(), document, document));
            generation.incrementAndGet(); // user retry before the queued task begins
            release.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            assertFalse(queued.get(5, TimeUnit.SECONDS),
                    "queued pre-retry work must not adopt the new generation");
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
