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
}
