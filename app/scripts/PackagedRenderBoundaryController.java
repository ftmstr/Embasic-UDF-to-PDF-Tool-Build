/*
 * TUR244 CI-only JDI controller for a SECOND, ephemeral debug-enabled jpackage
 * image. Not part of Maven sources, the shaded JAR or the standard app-image.
 * No user document contents or filesystem paths are logged.
 */
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class PackagedRenderBoundaryController {
    private static final String TARGET_CLASS = "com.embasic.publicapp.PdfExporter";
    private PackagedRenderBoundaryController() {}

    private static Path boundedUdf(String arg, Path root) throws Exception {
        Path candidate = Path.of(arg).toAbsolutePath().normalize();
        if (!candidate.getFileName().toString().endsWith(".udf")
                || Files.isSymbolicLink(candidate)
                || !Files.isRegularFile(candidate)
                || !candidate.toRealPath().startsWith(root))
            throw new IllegalArgumentException("Synthetic UDF guard failed");
        return candidate;
    }

    private static void replaceSyntheticUdf(Path source, Path replacement) throws Exception {
        if (!source.getParent().equals(replacement.getParent())
                || source.equals(replacement))
            throw new IllegalArgumentException("Synthetic UDF directory guard failed");
        FileTime previousTime = Files.getLastModifiedTime(source);
        byte[] before = Files.readAllBytes(source);
        byte[] after = Files.readAllBytes(replacement);
        if (MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(before),
                                  MessageDigest.getInstance("SHA-256").digest(after)))
            throw new IllegalArgumentException("Synthetic UDF mutation is not distinct");
        Arrays.fill(before, (byte)0);
        Arrays.fill(after, (byte)0);
        Files.move(replacement, source, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(source, previousTime);
        if (!previousTime.equals(Files.getLastModifiedTime(source)))
            throw new IllegalStateException("Synthetic source timestamp not restored");
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException("Expected loopback port/line/source/replacement/tempRoot");
        int port = Integer.parseInt(args[0]);
        int line = Integer.parseInt(args[1]);
        if (port < 1024 || port > 65535 || line < 1)
            throw new IllegalArgumentException("Invalid debugger endpoint");
        Path root = Path.of(args[4]).toRealPath();
        Path source = boundedUdf(args[2], root);
        Path replacement = boundedUdf(args[3], root);
        // Never accept arbitrary remote JDWP endpoints. The packaged JVM
        // itself has a separate localhost-only server option.
        AttachingConnector socket = Bootstrap.virtualMachineManager()
                .attachingConnectors().stream()
                .filter(c -> "com.sun.jdi.SocketAttach".equals(c.name()))
                .findFirst().orElseThrow();
        Map<String, Connector.Argument> options = socket.defaultArguments();
        options.get("hostname").setValue("127.0.0.1");
        options.get("port").setValue(Integer.toString(port));
        options.get("timeout").setValue("15000");
        VirtualMachine vm = socket.attach(options);
        BreakpointRequest request = null;
        try {
            List<ReferenceType> loaded = vm.classesByName(TARGET_CLASS);
            if (loaded.size() != 1)
                throw new IllegalStateException("Expected exactly one loaded PDF renderer");
            List<Location> locations = loaded.get(0).locationsOfLine(line).stream()
                    .filter(x -> "renderInternal".equals(x.method().name()))
                    .toList();
            if (locations.size() != 1)
                throw new IllegalStateException("No unique real render boundary line");
            request = vm.eventRequestManager().createBreakpointRequest(locations.get(0));
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.enable();
            // Controller is armed ONLY after the original approved PDF has
            // already been physically created in the packaged Swing app.
            System.out.println("EMBASIC_TUR244_JDI_READY renderer=LOADED"
                    + " realBreakpoint=ENABLED loopback=ONLY");
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            boolean matched = false;
            while (System.nanoTime() < deadline && !matched) {
                EventSet eventSet = vm.eventQueue().remove(2000L);
                if (eventSet == null) continue;
                try {
                    for (Event event : eventSet) {
                        if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent)
                            throw new IllegalStateException("Packaged VM disconnected before boundary");
                        if (!(event instanceof BreakpointEvent hit) || hit.request() != request)
                            continue;
                        // Preview rendering also uses renderInternal. Mutate
                        // ONLY on a real save() call stack, while its actual
                        // render thread is suspended by the VM breakpoint.
                        boolean isSaving = hit.thread().frames().stream().anyMatch(frame ->
                                TARGET_CLASS.equals(frame.location().declaringType().name())
                                && "save".equals(frame.location().method().name()));
                        if (!isSaving) continue;
                        replaceSyntheticUdf(source, replacement);
                        request.disable();
                        matched = true;
                        // This line is printed while the event thread is still
                        // suspended. The event-set finally resumes that thread.
                        System.out.println("EMBASIC_TUR244_JDI_RENDER_BOUNDARY_HIT"
                                + " realSaveStack=TRUE thread=SUSPENDED"
                                + " separateProcessMutation=COMPLETE mtime=RESTORED");
                        break;
                    }
                } finally {
                    eventSet.resume();
                }
            }
            if (!matched)
                throw new IllegalStateException("Real packaged render breakpoint NOT_OBSERVED");
        } finally {
            try { if (request != null) request.disable(); } catch (Exception ignored) {}
            try { vm.dispose(); } catch (Exception ignored) {}
        }
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable failure) {
            // No source path, filename, port, content or stack trace on CI.
            System.err.println("EMBASIC_TUR244_JDI_NOT_VERIFIED: "
                    + failure.getClass().getSimpleName());
            System.exit(2);
        }
    }
}
