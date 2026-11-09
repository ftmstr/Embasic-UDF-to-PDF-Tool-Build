package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Independent, rights-clean synthetic geometry constants; never native UYAP evidence.
 * Tests a page boundary carrying a merged table group followed by a color PNG.
 */
final class PdfMultipageCompositeRegressionTest {
    @TempDir Path folder;

    private static final Color[] COLORS = {
        new Color(235, 35, 40), new Color(25, 195, 50),
        new Color(20, 45, 230), new Color(240, 210, 25)
    };
    private static final DocumentElements.PageSpec A5 =
            new DocumentElements.PageSpec(419.53f, 595.28f, 48f);

    private static byte[] quadrantPng() throws IOException {
        BufferedImage pixels = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                pixels.setRGB(x, y, COLORS[(y >= 4 ? 2 : 0) + (x >= 4 ? 1 : 0)].getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(pixels, "PNG", out)) throw new IOException("PNG test encoder missing");
        return out.toByteArray();
    }

    private static DocumentElements.Row row(String left, String right) {
        return new DocumentElements.Row(List.of(
                new DocumentElements.Cell(left, 1, 1),
                new DocumentElements.Cell(right, 1, 1)), 120f);
    }

    private static UdfReader.DocumentSpec fixture(boolean secondPageOverflow)
            throws IOException {
        var intro = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("KIRMIZI ", new UdfReader.Style(14f, false,
                        new Color(230, 20, 20))),
                new UdfReader.Run("MAVI", new UdfReader.Style(18f, true,
                        new Color(10, 35, 230)))));
        // A5 usable height is 499.28 pt. The styled paragraph reduces the
        // first page's remaining space; 3 x 120 pt rows still fit, but the
        // next 2-row (240 pt) rowspan group must move *intact* to page two.
        var rows = List.of(
                row("P0L", "P0R"), row("P1L", "P1R"), row("P2L", "P2R"),
                new DocumentElements.Row(List.of(
                        new DocumentElements.Cell(secondPageOverflow
                                ? "W".repeat(100) : "MERGED", 1, 2),
                        new DocumentElements.Cell("P3R", 1, 1)), 120f),
                new DocumentElements.Row(List.of(
                        new DocumentElements.Cell("P4R", 1, 1)), 120f),
                row("P5L", "P5R"));
        var table = new DocumentElements.TableBlock(List.of(110f, 110f), rows);
        return new UdfReader.DocumentSpec("original-a5-composite.udf", "1.0",
                List.of(intro), List.of(new DocumentElements.ParagraphBlock(intro),
                        table, new DocumentElements.ImageBlock(quadrantPng(), 80f, 80f)), A5);
    }

    private static String pageText(PDDocument document, int pageNumber)
            throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(document);
    }

    private static boolean darkNear(BufferedImage image, int x, int y) {
        for (int yy = Math.max(0, y - 1); yy <= Math.min(y + 1, image.getHeight() - 1); yy++)
            for (int xx = Math.max(0, x - 1); xx <= Math.min(x + 1, image.getWidth() - 1); xx++) {
                var c = new Color(image.getRGB(xx, yy));
                if (c.getRed() < 100 && c.getGreen() < 100 && c.getBlue() < 100)
                    return true;
            }
        return false;
    }

    private static void assertRgb(Color source, int actual, String message) {
        Color rendered = new Color(actual, true);
        assertAll(message,
                () -> assertEquals(source.getRed(), rendered.getRed(), 8),
                () -> assertEquals(source.getGreen(), rendered.getGreen(), 8),
                () -> assertEquals(source.getBlue(), rendered.getBlue(), 8));
    }

    @Test void rowspanGroupImageAndColoredFontsKeepExactPageContinuity()
            throws Exception {
        Path output = folder.resolve("composite.pdf");
        assertEquals(2, PdfExporter.save(List.of(fixture(false)), output, false));
        try (PDDocument result = Loader.loadPDF(output.toFile())) {
            assertEquals(2, result.getNumberOfPages());
            for (int page = 0; page < 2; page++) {
                assertEquals(A5.width(), result.getPage(page).getMediaBox().getWidth(), .03);
                assertEquals(A5.height(), result.getPage(page).getMediaBox().getHeight(), .03);
            }
            String first = pageText(result, 1), second = pageText(result, 2);
            for (String token : List.of("KIRMIZI", "MAVI", "P0L", "P1L", "P2L"))
                assertTrue(first.contains(token), "First page lost " + token + ": " + first);
            for (String token : List.of("MERGED", "P3R", "P4R", "P5L", "P5R"))
                assertTrue(second.contains(token), "Second page lost " + token + ": " + second);
            assertFalse(first.contains("MERGED"), "Rowspan group must not be split");
            assertFalse(second.contains("P2L"), "First-page row must not repeat");

            class Glyphs extends PDFTextStripper {
                final List<TextPosition> glyphs = new ArrayList<>();
                Glyphs() throws IOException { super(); setStartPage(1); setEndPage(1); }
                @Override protected void processTextPosition(TextPosition p) {
                    glyphs.add(p);
                    super.processTextPosition(p);
                }
            }
            Glyphs glyphs = new Glyphs();
            glyphs.getText(result);
            TextPosition red = glyphs.glyphs.stream()
                    .filter(p -> p.getUnicode().equals("K")).findFirst().orElseThrow();
            // V appears only in the blue MAVI run; M also exists in KIRMIZI.
            TextPosition blue = glyphs.glyphs.stream()
                    .filter(p -> p.getUnicode().equals("V")).findFirst().orElseThrow();
            assertEquals(14f, red.getFontSizeInPt(), .2f);
            assertEquals(18f, blue.getFontSizeInPt(), .2f);
            assertTrue(red.getFont().isEmbedded(), "Regular font must remain embedded");
            assertTrue(blue.getFont().isEmbedded(), "Bold font must remain embedded");
            assertEquals(red.getYDirAdj(), blue.getYDirAdj(), 1.0f,
                    "Two styled runs on the first line must share a baseline");

            var renderer = new PDFRenderer(result);
            BufferedImage pageOne = renderer.renderImageWithDPI(0, 72);
            int reds = 0, blues = 0;
            for (int y = 45; y < 90; y++)
                for (int x = 48; x < 260; x++) {
                    Color c = new Color(pageOne.getRGB(x, y));
                    if (c.getRed() > 120 && c.getRed() > c.getBlue() + 70
                            && c.getRed() > c.getGreen() + 70) reds++;
                    if (c.getBlue() > 120 && c.getBlue() > c.getRed() + 70
                            && c.getBlue() > c.getGreen() + 70) blues++;
                }
            assertTrue(reds > 10, "Opaque red font ink missing");
            assertTrue(blues > 10, "Opaque blue font ink missing");

            BufferedImage pageTwo = renderer.renderImageWithDPI(1, 72);
            // Page 2: merged rows occupy [48,288], final row [288,408],
            // 8pt inter-block gap, then 80pt image at x=48, y=416.
            assertFalse(darkNear(pageTwo, 48 + 55, 48 + 120),
                    "Merged left cell must not have a phantom horizontal seam");
            assertTrue(darkNear(pageTwo, 48 + 110 + 55, 48 + 120),
                    "Unmerged right-column row boundary must be present");
            int[][] centers = {{68, 436}, {108, 436}, {68, 476}, {108, 476}};
            for (int quadrant = 0; quadrant < centers.length; quadrant++)
                assertRgb(COLORS[quadrant],
                        pageTwo.getRGB(centers[quadrant][0], centers[quadrant][1]),
                        "Second-page placed image quadrant " + quadrant);

            int firstImages = 0, secondImages = 0;
            for (int page = 0; page < 2; page++)
                for (var name : result.getPage(page).getResources().getXObjectNames())
                    if (result.getPage(page).getResources().getXObject(name)
                            instanceof PDImageXObject) {
                        if (page == 0) firstImages++; else secondImages++;
                    }
            assertEquals(0, firstImages, "Image must not leak into page one");
            assertEquals(1, secondImages, "Page two image was silently lost");
        }
    }

    @Test void previewShowsFirstPageButDoesNotCertifyLaterTableCells() throws Exception {
        var invalid = fixture(true);
        try (PDDocument preview = PdfExporter.renderPreview(invalid)) {
            assertEquals(1, preview.getNumberOfPages());
            String text = pageText(preview, 1);
            assertTrue(text.contains("P0L"));
            assertFalse(text.contains("P3R"), "Later page must not enter first-page preview");
        }
        var failure = assertThrows(IllegalArgumentException.class,
                () -> { try (PDDocument ignored = PdfExporter.render(List.of(invalid))) {} });
        assertTrue(failure.getMessage().contains("Tablo hücresi taşıyor"),
                failure.getMessage());
    }

    @Test void lateOverflowOnSecondPageDoesNotReplacePublishedPdf() throws Exception {
        Path output = folder.resolve("approved.pdf");
        assertEquals(2, PdfExporter.save(List.of(fixture(false)), output, false));
        byte[] approved = Files.readAllBytes(output);
        var failure = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(fixture(false), fixture(true)), output, true));
        assertTrue(failure.getMessage().contains("Tablo hücresi taşıyor"),
                failure.getMessage());
        assertArrayEquals(approved, Files.readAllBytes(output),
                "A later-page overflow must never replace validated output");
        try (var listing = Files.list(folder)) {
            assertEquals(List.of("approved.pdf"),
                    listing.map(p -> p.getFileName().toString()).sorted().toList(),
                    "Partial output or temporary PDF must not be published");
        }
    }
}
