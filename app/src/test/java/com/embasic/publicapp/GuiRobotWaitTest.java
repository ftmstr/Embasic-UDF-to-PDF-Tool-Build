package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Normal Windows/Linux CI proves waiting semantics, not physical GUI acceptance. */
final class GuiRobotWaitTest {
    @Test void boundedPollingPassesOnlyWhenObservedConditionIsTrue() throws Exception {
        AtomicInteger seen=new AtomicInteger();
        int result=GuiRobotWait.until(Duration.ofSeconds(2), Duration.ofMillis(1),
                seen::incrementAndGet, n->n>=3);
        assertEquals(3,result);
    }
    @Test void failedConditionIsReportedInsteadOfPassingOrHanging() {
        AssertionError error=assertThrows(AssertionError.class, ()->GuiRobotWait.until(
                Duration.ofMillis(25),Duration.ofMillis(2),()->"NOT_READY",
                "READY"::equals));
        assertTrue(error.getMessage().contains("NOT_READY"));
    }
    @Test void readsRealSwingStateFromEventThread() throws Exception {
        boolean result=GuiRobotWait.onEdt(SwingUtilities::isEventDispatchThread);
        assertTrue(result);
    }
    @Test void invalidWaitsAndEdtBlockingFailClearly() throws Exception {
        assertThrows(IllegalArgumentException.class,()->GuiRobotWait.until(
                Duration.ZERO,Duration.ofMillis(1),()->0,x->false));
        assertThrows(IllegalArgumentException.class,()->GuiRobotWait.until(
                Duration.ofMillis(1),Duration.ZERO,()->0,x->false));
        var error=GuiRobotWait.onEdt(()->assertThrows(
                IllegalStateException.class,()->GuiRobotWait.until(
                    Duration.ofMillis(1),Duration.ofMillis(1),()->0,x->false)));
        assertTrue(error.getMessage().contains("EDT"));
    }
}
