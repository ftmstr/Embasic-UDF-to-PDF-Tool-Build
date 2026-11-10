package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A user-local font change must not produce a silently corrupted PDF or replace
 * an already validated output. No installed font is modified, no real UDF used.
 */
final class PdfFontSourceChangeFailClosedTest {
    @TempDir Path folder;

    @Test void overwrittenFontFileFailsClosedAndKeepsPreviouslyValidatedPdf()
            throws Exception {
        Path fontCopy = folder.resolve("temporary-test-font.ttf");
        // Read-only copy of the runner's installed font into a disposable test
        // directory. This font file is never included in published artifacts.
        Files.copy(PdfExporter.systemFont(false), fontCopy,
                StandardCopyOption.REPLACE_EXISTING);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run(
                "SYNTHETIC-FONT-UPDATE", new UdfReader.Style(14, false,
                new Color(30, 70, 140)))));
        var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                List.of(paragraph));
        Path target = folder.resolve("approved.pdf");
        String original = System.getProperty("embasic.font.regular");
        try {
            System.setProperty("embasic.font.regular", fontCopy.toString());
            assertEquals(1, PdfExporter.save(List.of(document), target, false));
            byte[] baseline = Files.readAllBytes(target);
            try (var pdf = Loader.loadPDF(target.toFile())) {
                assertEquals(1, pdf.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(pdf)
                        .contains("SYNTHETIC-FONT-UPDATE"));
            }

            // Replace only our temporary *copy* of a font, not any installed
            // Windows/Linux font. PDFBox must not accept the broken update.
            Files.write(fontCopy, "CORRUPTED TEST FONT".getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class, () ->
                    PdfExporter.save(List.of(document), target, true),
                    "A new font with invalid bytes must fail rather than silently substitute");
            assertArrayEquals(baseline, Files.readAllBytes(target),
                    "Approved target must remain byte-identical after font failure");
            try (var files = Files.list(folder)) {
                assertEquals(0, files.filter(path -> path.getFileName().toString()
                        .startsWith(".embasic-pending-")).count(),
                        "Rejected font must not leave a staged PDF behind");
            }
            System.out.println("EMBASIC_FONT_SOURCE_CHANGE originalPdf=UNCHANGED "
                    + "invalidFont=REJECTED stagedFiles=0");
        } finally {
            if (original == null) System.clearProperty("embasic.font.regular");
            else System.setProperty("embasic.font.regular", original);
        }
    }
    /**
     * Inspects the embedded subset itself, not merely the PDF's differing
     * timestamps or randomly generated font subset names.
     */
    private static byte[] embeddedTrueType(Path pdfFile) throws Exception {
        try (var pdf = Loader.loadPDF(pdfFile.toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(pdf)
                    .contains("SYNTHETIC-VALID-FONT-SWAP"));
            var resources = pdf.getPage(0).getResources();
            for (var name : resources.getFontNames()) {
                var font = resources.getFont(name);
                if (font.getFontDescriptor() != null
                        && font.getFontDescriptor().getFontFile2() != null) {
                    try (var stream = font.getFontDescriptor()
                            .getFontFile2().createInputStream()) {
                        return stream.readAllBytes();
                    }
                }
            }
            throw new AssertionError("Rendered PDF has no embedded TrueType subset");
        }
    }

    @Test void validFontReplacementAtSameOverridePathUpdatesEmbeddedPdfSubset()
            throws Exception {
        Path regular = PdfExporter.systemFont(false);
        Path bold = PdfExporter.systemFont(true);
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(regular),
                Files.readAllBytes(bold)),
                "Fixture must use two distinct installed source fonts");
        Path privateFont = folder.resolve("changeable-font.ttf");
        Files.copy(regular, privateFont);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run(
                "SYNTHETIC-VALID-FONT-SWAP", new UdfReader.Style(
                        13, false, new Color(25, 70, 130)))));
        var doc = new UdfReader.DocumentSpec("public-synthetic.udf", "1.0",
                List.of(paragraph));
        Path before = folder.resolve("before.pdf");
        Path after = folder.resolve("after.pdf");
        String original = System.getProperty("embasic.font.regular");
        try {
            System.setProperty("embasic.font.regular", privateFont.toString());
            assertEquals(1, PdfExporter.save(List.of(doc), before, false));
            byte[] initialSubset = embeddedTrueType(before);

            // Only the disposable copy is replaced. All installed Windows
            // and Linux font files remain untouched.
            Files.copy(bold, privateFont, StandardCopyOption.REPLACE_EXISTING);
            assertEquals(1, PdfExporter.save(List.of(doc), after, false));
            byte[] updatedSubset = embeddedTrueType(after);
            assertFalse(java.util.Arrays.equals(initialSubset, updatedSubset),
                    "PDF must embed newly selected valid font bytes, never reuse"
                    + " stale subset content from the old file at the same path");
            assertTrue(Files.size(before) > 1000);
            assertTrue(Files.size(after) > 1000);
            System.out.println("EMBASIC_FONT_VALID_UPDATE embeddedFontChanged=PASS"
                    + " text=PARITY pages=1 systemFonts=UNCHANGED");
        } finally {
            if (original == null) System.clearProperty("embasic.font.regular");
            else System.setProperty("embasic.font.regular", original);
        }
    }

}
