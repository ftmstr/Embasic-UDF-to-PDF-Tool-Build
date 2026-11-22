package com.embasic.publicapp;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;

/**
 * Bounded state polling for manually opted-in GUI Robot scenarios.
 * Does not depend on AWT's event queue ever becoming completely idle.
 */
final class GuiRobotWait {
    private GuiRobotWait() {}

    static <T> T onEdt(Callable<T> task) throws Exception {
        Objects.requireNonNull(task);
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> problem = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(task.call()); }
            catch (Exception ex) { problem.set(ex); }
        });
        if (problem.get() != null) throw problem.get();
        return value.get();
    }

    static <T> T until(Duration timeout, Duration interval,
                       Callable<T> observation, Predicate<T> ready) throws Exception {
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(interval);
        Objects.requireNonNull(observation);
        Objects.requireNonNull(ready);
        if (timeout.isZero() || timeout.isNegative()
                || interval.isZero() || interval.isNegative())
            throw new IllegalArgumentException("GUI bekleme aralığı pozitif olmalıdır");
        if (SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Swing EDT üzerinde Robot beklemesi yapılamaz");
        long timeoutNanos=timeout.toNanos(), start=System.nanoTime();
        T last=null;
        while (true) {
            last=observation.call();
            if (ready.test(last)) return last;
            long elapsed=System.nanoTime()-start;
            if (elapsed>=timeoutNanos)
                throw new AssertionError("GUI koşulu zaman aşımına uğradı: " + last);
            long remaining=timeoutNanos-elapsed;
            long pauseMillis=Math.max(1,
                    Math.min(interval.toMillis(),Math.max(1,remaining/1_000_000)));
            Thread.sleep(pauseMillis);
        }
    }
}
