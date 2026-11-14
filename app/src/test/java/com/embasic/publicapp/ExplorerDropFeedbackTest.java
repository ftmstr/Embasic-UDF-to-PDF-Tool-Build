package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Native Explorer must regain control before any modal import feedback opens. */
final class ExplorerDropFeedbackTest {
    @Test void nativeDropFeedbackIsDeferredUntilImportReturns() throws Exception {
        AtomicInteger shown = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            DesktopApp.presentImportFeedback(true, shown::incrementAndGet);
            assertEquals(0, shown.get(), "A modal dialog must not run inside importData");
        });
        // FIFO EDT dispatch: the queued callback executes before this assertion.
        SwingUtilities.invokeAndWait(() -> assertEquals(1, shown.get()));
    }

    @Test void chooserFeedbackRemainsImmediate() {
        AtomicInteger shown = new AtomicInteger();
        DesktopApp.presentImportFeedback(false, shown::incrementAndGet);
        assertEquals(1, shown.get());
    }
}
