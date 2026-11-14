package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TUR218: deterministic PDFBox render-phase mutation regression.
 *
 * Changes occur after one complete synthetic UDF has been laid out and
 * BEFORE the next is rendered. It is not a timing-based or physical GUI test,
 * and does not claim Windows atomic TOCTOU protection against hostile races.
 */
final class PdfRenderMutationBoundaryTest {
    @TempDir Path folder;

    private Path udf(String name, String text) throws Exception {
        Path source = folder.resolve(name);
        String xml = "<template format_id='1.0'><content>" + text + "</content>"
                + "<properties pageSize='A4' orientation='portrait' margin='48'/>"
                + "<elements><paragraph><content startOffset='0' length='"
                + text.length() + "'/></paragraph></elements></template>";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return source;
    }

    private record SourceQueue(List<Path> paths,
                               List<UdfReader.DocumentSpec> docs,
                               List<String> fingerprints) {}

    private SourceQueue makeQueue() throws Exception {
        Path first = udf("01-alpha.udf", "ALPHA-ONE");
        Path second = udf("02-bravo.udf", "BRAVO-TWO");
        return new SourceQueue(List.of(first, second),
                List.of(UdfReader.read(first), UdfReader.read(second)),
                List.of(FileImportPolicy.sourceSha256(first),
                        FileImportPolicy.sourceSha256(second)));
    }

    private static void assertPdf(Path target, int expectedPages) throws Exception {
        try (var pdf = Loader.loadPDF(target.toFile())) {
            assertEquals(expectedPages, pdf.getNumberOfPages());
            for (int i = 0; i < expectedPages; i++)
                assertEquals(0, pdf.getPage(i).getRotation());
        }
    }

    private void assertNoStaging() throws Exception {
        try (var files = Files.list(folder)) {
            assertEquals(0, files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(".embasic-pending-")
                        || name.startsWith(".embasic-backup-");
            }).count(), "Rejected render must not leak staging or backup files");
        }
    }

    @Test void sourceChangesMidRenderEvenWithRestoredMtimeCannotReplaceApprovedPdf()
            throws Exception {
        SourceQueue originals = makeQueue();
        Path output = folder.resolve("approved.pdf");
        assertEquals(2, DesktopApp.saveWorkspace(originals.paths(), originals.docs(),
                originals.fingerprints(), output, false));
        byte[] approved = Files.readAllBytes(output);
        assertPdf(output, 2);

        List<Integer> boundaries = new ArrayList<>();
        Path second = originals.paths().get(1);
        FileTime beforeMtime = Files.getLastModifiedTime(second);
        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(originals.docs(), output, true,
                        () -> DesktopApp.verifySourceFingerprints(
                                originals.paths(), originals.fingerprints()),
                        completed -> {
                            boundaries.add(completed);
                            if (completed == 1) {
                                udf("02-bravo.udf", "BRAVO-NEW");
                                Files.setLastModifiedTime(second, beforeMtime);
                                assertNotEquals(originals.fingerprints().get(1),
                                        FileImportPolicy.sourceSha256(second));
                                assertEquals(beforeMtime, Files.getLastModifiedTime(second));
                                // This is DURING the in-memory render: the
                                // second document has not been laid out yet.
                            }
                        }));
        assertTrue(changed.getMessage().contains("değiştirildi"));
        assertEquals(List.of(1, 2), boundaries);
        assertArrayEquals(approved, Files.readAllBytes(output));
        assertPdf(output, 2);
        assertNoStaging();
        System.out.println("EMBASIC_TUR218_RENDER_BOUNDARY source=CHANGED"
                + " mtime=RESTORED stage=REMOVED target=UNTOUCHED noNativeUyap=true");
    }

    @Test void newerExternalPdfDuringRenderWinsOverPreviouslyApprovedTarget()
            throws Exception {
        SourceQueue originals = makeQueue();
        Path output = folder.resolve("approved.pdf");
        assertEquals(2, DesktopApp.saveWorkspace(originals.paths(), originals.docs(),
                originals.fingerprints(), output, false));
        Path external = folder.resolve("externally-saved.pdf");
        assertEquals(1, PdfExporter.save(List.of(originals.docs().get(0)), external, false));
        byte[] newer = Files.readAllBytes(external);
        assertFalse(java.util.Arrays.equals(newer, Files.readAllBytes(output)));
        List<Integer> boundaries = new ArrayList<>();

        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(originals.docs(), output, true,
                        () -> DesktopApp.verifySourceFingerprints(
                                originals.paths(), originals.fingerprints()),
                        completed -> {
                            boundaries.add(completed);
                            if (completed == 1) {
                                Files.copy(external, output,
                                        StandardCopyOption.REPLACE_EXISTING);
                            }
                        }));
        assertTrue(changed.getMessage().contains("yeniden onaylayın"));
        assertEquals(List.of(1, 2), boundaries);
        assertArrayEquals(newer, Files.readAllBytes(output));
        assertPdf(output, 1);
        assertNoStaging();
        System.out.println("EMBASIC_TUR218_TARGET_CONFLICT external=PREVAILS"
                + " explicitReapproval=REQUIRED stage=REMOVED noNativeUyap=true");
    }

    @Test void noOverwriteConsentRejectsNewTargetCreatedDuringRender()
            throws Exception {
        SourceQueue originals = makeQueue();
        Path output = folder.resolve("late-appearing.pdf");
        assertFalse(Files.exists(output));
        Path external = folder.resolve("external.pdf");
        assertEquals(1, PdfExporter.save(List.of(originals.docs().get(0)), external, false));
        byte[] newer = Files.readAllBytes(external);
        List<Integer> boundaries = new ArrayList<>();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(originals.docs(), output, false,
                        () -> DesktopApp.verifySourceFingerprints(
                                originals.paths(), originals.fingerprints()),
                        completed -> {
                            boundaries.add(completed);
                            if (completed == 1) {
                                Files.copy(external, output);
                            }
                        }));
        assertTrue(refused.getMessage().contains("onayı"));
        assertEquals(List.of(1, 2), boundaries);
        assertArrayEquals(newer, Files.readAllBytes(output));
        assertPdf(output, 1);
        assertNoStaging();
        System.out.println("EMBASIC_TUR218_LATE_TARGET noOverwrite=REJECT"
                + " target=UNTOUCHED stage=REMOVED noNativeUyap=true");
    }
}
