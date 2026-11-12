package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
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

/**
 * TUR215: all documents below are original synthetic PDFs; no real UYAP
 * document, editor binary, installed font bytes or personal data involved.
 */
final class OfflinePdfGlyphAuditRegressionTest {
    @TempDir Path temp;

    private Path make(String filename, Standard14Fonts.FontName hiddenFamily,
                      float hiddenX, float dividerY) throws Exception {
        Path output = temp.resolve(filename);
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                // Original, simple two-row/two-column vector table:
                // pixel-band deltas are table-line proxies, NOT cell parsing.
                content.setStrokingColor(Color.BLACK);
                content.setLineWidth(1.4f);
                content.addRect(50, 180, 200, 100);
                content.stroke();
                content.moveTo(50, dividerY);
                content.lineTo(250, dividerY);
                content.stroke();
                content.moveTo(150, 180);
                content.lineTo(150, 280);
                content.stroke();
                content.setNonStrokingColor(Color.BLACK);
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(68, 250);
                content.showText("ROW_ONE");
                content.endText();
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(68, 201);
                content.showText("ROW_TWO");
                content.endText();

                // PDFTextStripper still sees these glyphs although they are
                // white on a white page; changing font/position must not be
                // silently certified merely because the RGB raster matches.
                content.setNonStrokingColor(Color.WHITE);
                content.beginText();
                content.setFont(new PDType1Font(hiddenFamily), 14);
                content.newLineAtOffset(hiddenX, 360);
                content.showText("HIDDEN");
                content.endText();
            }
            pdf.save(output.toFile());
        }
        return output;
    }

    @Test void sameSyntheticFontTextAndVectorTableMatchesWithoutDataLeak() throws Exception {
        Path reference = make("ref.pdf", Standard14Fonts.FontName.HELVETICA, 280, 230);
        Path candidate = make("candidate.pdf", Standard14Fonts.FontName.HELVETICA, 280, 230);
        var result = OfficialPdfReferenceAudit.audit(reference, candidate);
        assertTrue(result.matches());
        var page = result.pages().get(0);
        assertTrue(page.glyphCount() > 8);
        assertEquals(0, page.differentFontGlyphs());
        assertEquals(0, page.differentTextPositions());
        assertEquals(0, page.changedRasterRows());
        assertEquals(0, page.changedRasterColumns());
        assertFalse(result.toString().contains("HIDDEN"));
        assertFalse(page.toString().contains("ROW_ONE"));
        System.out.println("EMBASIC_TUR215_GLYPH_GEOMETRY baseline=PARITY"
                + " fontPosition=PARITY rasterRowsColumns=PARITY noNativeUyap=true");
    }

    @Test void invisibleFontSubstitutionIsRejectedDespiteUnchangedRaster() throws Exception {
        Path reference = make("ref.pdf", Standard14Fonts.FontName.HELVETICA, 280, 230);
        Path candidate = make("font.pdf", Standard14Fonts.FontName.COURIER, 280, 230);
        byte[] saved = Files.readAllBytes(reference);
        var result = OfficialPdfReferenceAudit.audit(reference, candidate);
        var page = result.pages().get(0);
        assertFalse(result.matches());
        assertTrue(page.geometryMatches());
        assertTrue(page.extractedTextMatches());
        assertEquals(0, page.differentPixels());
        assertEquals(0, page.changedRasterRows());
        assertTrue(page.differentFontGlyphs() > 0);
        assertArrayEquals(saved, Files.readAllBytes(reference));
        System.out.println("EMBASIC_TUR215_FONT_FALSE_MATCH unchangedRaster=PASS"
                + " fontSubstitution=REJECT noNativeUyap=true");
    }

    @Test void invisibleGlyphShiftAndVectorTableRowDeltaAreSeparateDiagnostics()
            throws Exception {
        Path reference = make("ref.pdf", Standard14Fonts.FontName.HELVETICA, 280, 230);
        Path moved = make("shift.pdf", Standard14Fonts.FontName.HELVETICA, 292, 230);
        var shift = OfficialPdfReferenceAudit.audit(reference, moved).pages().get(0);
        assertTrue(shift.extractedTextMatches());
        assertTrue(shift.geometryMatches());
        assertTrue(shift.differentTextPositions() > 0);
        assertEquals(0, shift.differentPixels());
        assertEquals(0, shift.differentFontGlyphs());

        Path changedDivider = make("divider.pdf",
                Standard14Fonts.FontName.HELVETICA, 280, 237);
        var border = OfficialPdfReferenceAudit.audit(reference, changedDivider).pages().get(0);
        assertTrue(border.extractedTextMatches());
        assertEquals(0, border.differentFontGlyphs());
        assertEquals(0, border.differentTextPositions());
        assertTrue(border.differentPixels() > 100);
        assertTrue(border.changedRasterRows() > 0);
        assertTrue(border.changedRasterColumns() > 0);
        assertFalse(OfficialPdfReferenceAudit.audit(reference, changedDivider).matches());
        System.out.println("EMBASIC_TUR215_BORDER_BANDS"
                + " invisibleGlyphShift=DETECTED tableRuleRowsColumns=DIFFERENCE"
                + " sourceContent=PRIVATE noNativeUyap=true");
    }
}
