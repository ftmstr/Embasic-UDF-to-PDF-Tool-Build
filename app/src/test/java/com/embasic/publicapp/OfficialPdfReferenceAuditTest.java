package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Original rights-clean synthetic PDF fixtures; no UYAP source/assets needed. */
final class OfficialPdfReferenceAuditTest {
    @TempDir Path folder;

    private Path pdf(String name, PDRectangle paper, String text,
                     Color square, int pages) throws IOException {
        Path output = folder.resolve(name);
        try (PDDocument pdf = new PDDocument()) {
            for (int index = 0; index < pages; index++) {
                PDPage page = new PDPage(paper);
                pdf.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.setNonStrokingColor(square);
                    stream.addRect(50, 60 + index * 20, 90, 55);
                    stream.fill();
                    stream.setNonStrokingColor(Color.BLACK);
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(60, 190);
                    stream.showText(text + index);
                    stream.endText();
                }
            }
            pdf.save(output.toFile());
        }
        return output;
    }

    @Test void separatelySerializedIdenticalSyntheticPagesMatch() throws Exception {
        Path expected = pdf("reference.pdf", PDRectangle.A4, "SYNTHETIC", Color.BLUE, 2);
        Path candidate = pdf("candidate.pdf", PDRectangle.A4, "SYNTHETIC", Color.BLUE, 2);
        OfficialPdfReferenceAudit.Result result = OfficialPdfReferenceAudit.audit(expected, candidate);
        assertTrue(result.matches());
        assertEquals(2, result.referencePages());
        assertEquals(2, result.candidatePages());
        assertEquals(2, result.pages().size());
        assertEquals(0, result.differentPixels());
        assertTrue(result.pages().stream().allMatch(x -> x.renderedPixels() > 0));
        System.out.println("EMBASIC_TUR211_OFFLINE_REFERENCE identicalSynthetic=PASS"
                + " pages=2 text=PARITY geometry=PARITY pixels=PARITY noNativeUyap=true");
    }

    @Test void changedRasterCannotHideBehindEqualTextAndGeometry() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 1);
        Path candidate = pdf("out.pdf", PDRectangle.A4, "SYNTHETIC", Color.BLUE, 1);
        var result = OfficialPdfReferenceAudit.audit(expected, candidate);
        assertFalse(result.matches());
        assertEquals(1, result.pages().size());
        var page = result.pages().get(0);
        assertTrue(page.geometryMatches());
        assertTrue(page.extractedTextMatches());
        assertTrue(page.differentPixels() > 100);
        assertTrue(page.maximumChannelDifference() > 8);
    }

    @Test void changedTextFailsEvenWithSimilarPageLayout() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "ALPHA", Color.BLUE, 1);
        Path candidate = pdf("out.pdf", PDRectangle.A4, "BETA", Color.BLUE, 1);
        var result = OfficialPdfReferenceAudit.audit(expected, candidate);
        assertFalse(result.matches());
        assertTrue(result.pages().get(0).geometryMatches());
        assertFalse(result.pages().get(0).extractedTextMatches());
    }

    @Test void pageCountAndMediaBoxDifferenceNeverPass() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 2);
        Path fewer = pdf("few.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 1);
        Path legal = pdf("legal.pdf", new PDRectangle(1008, 612),
                "SYNTHETIC", Color.RED, 2);
        var count = OfficialPdfReferenceAudit.audit(expected, fewer);
        assertFalse(count.matches());
        assertEquals(2, count.referencePages());
        assertEquals(1, count.candidatePages());
        var geometry = OfficialPdfReferenceAudit.audit(expected, legal);
        assertFalse(geometry.matches());
        assertTrue(geometry.pages().stream().noneMatch(x -> x.geometryMatches()));
        assertTrue(geometry.pages().stream().allMatch(x -> x.renderedPixels() == 0));
    }

    @Test void samePhysicalFileAndNonPdfAreRejectedWithoutWritingFiles() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 1);
        byte[] before = Files.readAllBytes(expected);
        assertThrows(IOException.class, () -> OfficialPdfReferenceAudit.audit(expected, expected));
        Path bad = folder.resolve("malformed.pdf");
        Files.write(bad, new byte[]{0, 1, 2, 3});
        assertThrows(IOException.class, () -> OfficialPdfReferenceAudit.audit(expected, bad));
        assertArrayEquals(before, Files.readAllBytes(expected));
        try (var listed = Files.list(folder)) {
            assertEquals(2L, listed.count(), "Audit must not create staging or user-content logs");
        }
    }

    @Test void hugePageGeometryFailsBeforeAllocatingRasterMemory() throws Exception {
        PDRectangle huge = new PDRectangle(12000, 12000);
        Path expected = pdf("ref.pdf", huge, "SYNTHETIC", Color.RED, 1);
        Path candidate = pdf("candidate.pdf", huge, "SYNTHETIC", Color.RED, 1);
        assertThrows(IOException.class, () -> OfficialPdfReferenceAudit.audit(expected, candidate));
    }
}
