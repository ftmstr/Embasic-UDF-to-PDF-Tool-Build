package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression for a PDF target appearing after the initial overwrite check. */
final class PdfOutputCommitTest {
    @TempDir Path folder;

    @Test void lateCreatedTargetCannotBeClobberedWithoutConsent() throws Exception {
        Path staging = folder.resolve(".embasic-pending.pdf");
        Path target = folder.resolve("case.pdf");
        Files.writeString(staging, "VALIDATED-PDF", StandardCharsets.UTF_8);
        // Simulates another process creating the target while PDF rendering runs.
        Files.writeString(target, "KEEP-ORIGINAL", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.publishValidated(staging, target, false));
        assertEquals("KEEP-ORIGINAL", Files.readString(target));
        assertEquals("VALIDATED-PDF", Files.readString(staging));
    }

    @Test void approvedOverwriteCanReplaceExistingTarget() throws Exception {
        Path staging = folder.resolve(".embasic-pending.pdf");
        Path target = folder.resolve("case.pdf");
        Files.writeString(staging, "VALIDATED-PDF", StandardCharsets.UTF_8);
        Files.writeString(target, "OLD-PDF", StandardCharsets.UTF_8);
        PdfExporter.publishValidated(staging, target, true);
        assertEquals("VALIDATED-PDF", Files.readString(target));
        assertFalse(Files.exists(staging));
    }

    @Test void newTargetIsCreatedWithoutOverwriteConsent() throws Exception {
        Path staging = folder.resolve(".embasic-pending.pdf");
        Path target = folder.resolve("new.pdf");
        Files.writeString(staging, "VALIDATED-PDF", StandardCharsets.UTF_8);
        PdfExporter.publishValidated(staging, target, false);
        assertEquals("VALIDATED-PDF", Files.readString(target));
        assertFalse(Files.exists(staging));
    }

    @Test void oversizedBatchFailsBeforeCreatingAnyPdfStagingFile() throws Exception {
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Synthetic", style)));
        var doc = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(paragraph));
        Path target = folder.resolve("too-many.pdf");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(java.util.Collections.nCopies(FileImportPolicy.MAX_CARDS + 1, doc),
                        target, false));
        assertTrue(error.getMessage().contains("160"));
        assertFalse(Files.exists(target));
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.count(), "Rejected batch must leave no staging file");
        }
    }

    @Test void invalidDestinationFailsBeforeSourceHashingOrPdfStaging() throws Exception {
        // A failed output choice must not read 160 sources or wait for font
        // discovery. The source deliberately does not exist: output validation
        // must be the first filesystem operation in the GUI save worker.
        Path missing = folder.resolve("missing.udf");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Synthetic", style)));
        var doc = new UdfReader.DocumentSpec("missing.udf", "1.0", List.of(paragraph));
        IllegalArgumentException extension = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(missing), List.of(doc),
                        List.of("not-a-fingerprint"), folder.resolve("invalid.txt"), false));
        assertTrue(extension.getMessage().contains(".pdf"));

        // The save dialog may select an existing directory whose name ends in
        // .pdf. Even with overwrite consent, never stage a PDF against it.
        Path directory = Files.createDirectory(folder.resolve("directory.pdf"));
        IllegalArgumentException nonFile = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(missing), List.of(doc),
                        List.of("not-a-fingerprint"), directory, true));
        assertTrue(nonFile.getMessage().contains("normal bir PDF"));
        assertTrue(Files.isDirectory(directory));
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count());
        }
    }

    @Test void latePhysicalAliasCannotOverwriteExistingOutput() throws Exception {
        // Two independent, byte-identical sources pass the initial fingerprint
        // gate. Replacing the second path with a hard link during rendering
        // must fail the final physical-identity check even though SHA stays equal.
        Path first = Files.writeString(folder.resolve("first.udf"), "synthetic shared bytes");
        Path second = Files.writeString(folder.resolve("second.udf"), "synthetic shared bytes");
        List<Path> sources = List.of(first, second);
        String fingerprint = FileImportPolicy.sourceSha256(first);
        List<String> fingerprints = List.of(fingerprint, fingerprint);
        assertDoesNotThrow(() -> DesktopApp.verifySourceFingerprints(sources, fingerprints));
        Path target = Files.writeString(folder.resolve("existing.pdf"), "KEEP-ORIGINAL");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Public synthetic", style)));
        var doc = new UdfReader.DocumentSpec("first.udf", "1.0", List.of(paragraph));

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(doc, doc), target, true, () -> {
                    Files.delete(second);
                    Files.createLink(second, first);
                    assertEquals(fingerprint, FileImportPolicy.sourceSha256(second));
                    DesktopApp.verifySourceFingerprints(sources, fingerprints);
                }));
        assertTrue(duplicate.getMessage().contains("yinelenen"));
        assertEquals("KEEP-ORIGINAL", Files.readString(target));
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count());
        }
    }

    @Test void sourceMutationDuringPdfRenderCannotOverwriteExistingOutput() throws Exception {
        // Deterministic late mutation: this callback runs only after PDFBox
        // staged and re-opened a valid PDF, immediately before publication.
        Path source = Files.writeString(folder.resolve("source.udf"), "synthetic initial bytes");
        String originalFingerprint = FileImportPolicy.sourceSha256(source);
        Path target = Files.writeString(folder.resolve("existing.pdf"), "KEEP-ORIGINAL");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Public synthetic", style)));
        var doc = new UdfReader.DocumentSpec("source.udf", "1.0", List.of(paragraph));
        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(doc), target, true, () -> {
                    Files.writeString(source, "synthetic changed bytes");
                    DesktopApp.verifySourceFingerprints(List.of(source), List.of(originalFingerprint));
                }));
        assertTrue(changed.getMessage().contains("değiştirildi"));
        assertEquals("KEEP-ORIGINAL", Files.readString(target),
                "A changed source must not replace a previously saved PDF");
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count(),
                    "Rejected staged PDF must be cleaned up");
        }
    }
}
