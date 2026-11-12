package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TUR220: real, separate OS process mutates source/target after the first
 * PDFBox DocumentSpec render has ended but before the second has begun.
 * Synchronization is a bounded test-only in-process render callback, not a
 * timing guess. This is NOT physical packaged Windows EXE acceptance.
 */
final class PdfCrossProcessRenderBoundaryTest {
    @TempDir Path folder;

    private Path createUdf(String name, String marker) throws Exception {
        Path file = folder.resolve(name);
        String xml = "<template format_id='1.0'><content>" + marker + "</content>"
                + "<properties pageSize='A4' orientation='portrait' margin='48'/>"
                + "<elements><paragraph><content startOffset='0' length='"
                + marker.length() + "'/></paragraph></elements></template>";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    private record Inputs(List<Path> files,
                          List<UdfReader.DocumentSpec> documents,
                          List<String> fingerprints) {}

    private Inputs makeInputs() throws Exception {
        Path first = createUdf("a.udf", "ALPHA-ONE");
        Path second = createUdf("b.udf", "BRAVO-TWO");
        return new Inputs(List.of(first, second),
                List.of(UdfReader.read(first), UdfReader.read(second)),
                List.of(FileImportPolicy.sourceSha256(first),
                        FileImportPolicy.sourceSha256(second)));
    }

    private static String javaBinary() {
        boolean windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin",
                windows ? "java.exe" : "java").toString();
    }

    private static String workerClasses() throws Exception {
        return Path.of(RenderMutationWorker.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
    }

    private static void invokeChild(String... arguments) throws Exception {
        String[] command = new String[4 + arguments.length];
        command[0] = javaBinary();
        command[1] = "-cp";
        command[2] = workerClasses();
        command[3] = RenderMutationWorker.class.getName();
        System.arraycopy(arguments, 0, command, 4, arguments.length);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).start();
        boolean exited;
        try {
            exited = process.waitFor(15, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        assertTrue(exited, "Separate-JVM synthetic mutator exceeded 15 seconds");
        // Never echo arbitrary subprocess content or file paths into CI logs.
        assertEquals(0, process.exitValue(), "Separate-JVM synthetic mutation failed");
        String marker = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).strip();
        assertEquals("EMBASIC_TUR220_CHILD_MUTATION_DONE", marker);
    }

    private void assertNoStaging() throws Exception {
        try (var files = Files.list(folder)) {
            assertEquals(0, files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(".embasic-pending-")
                        || name.startsWith(".embasic-backup-");
            }).count(), "The PDF save operation left a staging/backup file");
        }
    }

    @Test void independentJvmChangesNextSourceMidRenderAndRestoresTimestamp()
            throws Exception {
        Inputs inputs = makeInputs();
        Path reference = folder.resolve("approved.pdf");
        assertEquals(2, DesktopApp.saveWorkspace(inputs.files(),
                inputs.documents(), inputs.fingerprints(), reference, false));
        byte[] approved = Files.readAllBytes(reference);
        Path replacement = createUdf("replacement.udf", "BRAVO-NEW");
        Path changing = inputs.files().get(1);
        FileTime originalTime = Files.getLastModifiedTime(changing);
        int[] boundaries = {0};

        IllegalArgumentException blocked = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(inputs.documents(), reference, true,
                        () -> DesktopApp.verifySourceFingerprints(
                                inputs.files(), inputs.fingerprints()),
                        finished -> {
                            assertEquals(++boundaries[0], finished);
                            if (finished == 1) {
                                invokeChild("source", replacement.toString(),
                                        changing.toString(),
                                        Long.toString(originalTime.toMillis()));
                                assertEquals(originalTime.toMillis(),
                                        Files.getLastModifiedTime(changing).toMillis());
                                assertNotEquals(inputs.fingerprints().get(1),
                                        FileImportPolicy.sourceSha256(changing));
                            }
                        }));
        assertEquals(2, boundaries[0], "Both documents must be rendered");
        assertTrue(blocked.getMessage().contains("değiştirildi"));
        assertArrayEquals(approved, Files.readAllBytes(reference));
        try (var saved = Loader.loadPDF(reference.toFile())) {
            assertEquals(2, saved.getNumberOfPages());
        }
        assertNoStaging();
        System.out.println("EMBASIC_TUR220_EXTERNAL_SOURCE_RENDER"
                + " independentProcess=PASS mtime=RESTORED output=UNCHANGED"
                + " staging=CLEAN noNativeUyap=true");
    }

    @Test void independentJvmReplacesApprovedPdfDuringRenderAndNewerTargetSurvives()
            throws Exception {
        Inputs inputs = makeInputs();
        Path output = folder.resolve("approved.pdf");
        assertEquals(2, DesktopApp.saveWorkspace(inputs.files(),
                inputs.documents(), inputs.fingerprints(), output, false));
        Path external = folder.resolve("external.pdf");
        assertEquals(1, PdfExporter.save(List.of(inputs.documents().get(0)),
                external, false));
        byte[] newBytes = Files.readAllBytes(external);
        assertFalse(Arrays.equals(newBytes, Files.readAllBytes(output)));
        int[] boundaries = {0};

        IllegalArgumentException blocked = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(inputs.documents(), output, true,
                        () -> DesktopApp.verifySourceFingerprints(
                                inputs.files(), inputs.fingerprints()),
                        finished -> {
                            assertEquals(++boundaries[0], finished);
                            if (finished == 1)
                                invokeChild("target", external.toString(), output.toString());
                        }));
        assertEquals(2, boundaries[0]);
        assertTrue(blocked.getMessage().contains("yeniden onaylayın"));
        assertArrayEquals(newBytes, Files.readAllBytes(output));
        try (var saved = Loader.loadPDF(output.toFile())) {
            assertEquals(1, saved.getNumberOfPages());
        }
        assertNoStaging();
        System.out.println("EMBASIC_TUR220_EXTERNAL_TARGET_RENDER"
                + " independentProcess=PASS externalNewer=RETAINED"
                + " reapproval=REQUIRED staging=CLEAN noNativeUyap=true");
    }

    @Test void independentJvmCreatesUnapprovedDestinationDuringRender()
            throws Exception {
        Inputs inputs = makeInputs();
        Path target = folder.resolve("unapproved.pdf");
        Path external = folder.resolve("external.pdf");
        assertEquals(1, PdfExporter.save(List.of(inputs.documents().get(0)),
                external, false));
        byte[] newer = Files.readAllBytes(external);
        assertFalse(Files.exists(target));
        int[] boundaries = {0};

        IllegalArgumentException blocked = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(inputs.documents(), target, false,
                        () -> DesktopApp.verifySourceFingerprints(
                                inputs.files(), inputs.fingerprints()),
                        finished -> {
                            assertEquals(++boundaries[0], finished);
                            if (finished == 1)
                                invokeChild("newtarget", external.toString(), target.toString());
                        }));
        assertEquals(2, boundaries[0]);
        assertTrue(blocked.getMessage().contains("onayı"));
        assertArrayEquals(newer, Files.readAllBytes(target));
        assertNoStaging();
        System.out.println("EMBASIC_TUR220_EXTERNAL_LATE_TARGET"
                + " independentProcess=PASS noConsent=REJECT"
                + " target=UNTOUCHED staging=CLEAN noNativeUyap=true");
    }
}
