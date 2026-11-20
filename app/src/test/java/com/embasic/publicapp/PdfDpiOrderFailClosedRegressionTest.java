package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TUR 195: original, rights-clean synthetic UDF specifications only.
 * Independent source-pixel and geometry oracles at 72/144 DPI; this does
 * not claim native UYAP PDF parity or support for forbidden PNG chunks.
 */
final class PdfDpiOrderFailClosedRegressionTest {
    @TempDir Path folder;

    private static final DocumentElements.PageSpec A5 =
            new DocumentElements.PageSpec(419.53f, 595.28f, 48f);
    private static final DocumentElements.PageSpec LEGAL =
            new DocumentElements.PageSpec(1008f, 612f, 48f);
    private static final DocumentElements.PageSpec LETTER =
            new DocumentElements.PageSpec(792f, 612f, 48f);

    private record PngFixture(byte[] encoded, BufferedImage original, boolean rgba) {}
    private record RasterPage(String name, DocumentElements.PageSpec paper, PngFixture fixture) {}

    private static PngFixture png(boolean rgba) throws Exception {
        BufferedImage pixels = new BufferedImage(8, 8,
                rgba ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int alpha = !rgba ? 255 : (x == 0 && y == 0) ? 0
                    : (x == 7 && y == 7) ? 255 : (x * 37 + y * 41 + 17) % 256;
            int red = (31 * x + 7 * y + 23) % 256;
            int green = (13 * x + 29 * y + 57) % 256;
            int blue = (17 * x + 11 * y + 149) % 256;
            pixels.setRGB(x, y, new Color(red, green, blue, alpha).getRGB());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, "PNG", output));
        byte[] encoded = output.toByteArray();
        assertEquals(8, encoded[24] & 255, "Only supported PNG bit depth");
        assertEquals(rgba ? 6 : 2, encoded[25] & 255, "Only RGB/RGBA color types");
        return new PngFixture(encoded, pixels, rgba);
    }

    private static UdfReader.DocumentSpec imageDocument(RasterPage fixture) {
        return new UdfReader.DocumentSpec(fixture.name(), "1.0", List.of(),
                List.of(new DocumentElements.ImageBlock(
                        fixture.fixture().encoded(), 80f, 80f)), fixture.paper());
    }

    private static void assertPixel(Color expected, int actual, String where) {
        Color found = new Color(actual, true);
        assertAll(where,
                () -> assertEquals(expected.getRed(), found.getRed(), 8),
                () -> assertEquals(expected.getGreen(), found.getGreen(), 8),
                () -> assertEquals(expected.getBlue(), found.getBlue(), 8));
    }

    private static Color overWhite(int argb) {
        Color c = new Color(argb, true);
        int a = c.getAlpha();
        return new Color(
                (c.getRed() * a + 255 * (255 - a) + 127) / 255,
                (c.getGreen() * a + 255 * (255 - a) + 127) / 255,
                (c.getBlue() * a + 255 * (255 - a) + 127) / 255);
    }

    @Test void rgbAndRgbaFourPageOrderRemainsPixelExactAt72And144Dpi()
            throws Exception {
        PngFixture rgb = png(false), rgba = png(true);
        List<RasterPage> sequence = List.of(
                new RasterPage("rgb-a5.udf", A5, rgb),
                new RasterPage("rgba-legal.udf", LEGAL, rgba),
                new RasterPage("rgba-a4.udf", DocumentElements.PageSpec.a4(), rgba),
                new RasterPage("rgb-letter.udf", LETTER, rgb));
        Path output = folder.resolve("ordered-raster.pdf");
        assertEquals(4, PdfExporter.save(sequence.stream()
                .map(PdfDpiOrderFailClosedRegressionTest::imageDocument).toList(),
                output, false));
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(sequence.size(), pdf.getNumberOfPages());
            PDFRenderer renderer = new PDFRenderer(pdf);
            for (int page = 0; page < sequence.size(); page++) {
                RasterPage expected = sequence.get(page);
                var actual = pdf.getPage(page);
                assertEquals(expected.paper().width(),
                        actual.getMediaBox().getWidth(), .03f, "Page " + page + " width/order");
                assertEquals(expected.paper().height(),
                        actual.getMediaBox().getHeight(), .03f, "Page " + page + " height/order");
                List<PDImageXObject> images = new ArrayList<>();
                for (var key : actual.getResources().getXObjectNames()) {
                    var xObject = actual.getResources().getXObject(key);
                    if (xObject instanceof PDImageXObject image) images.add(image);
                }
                assertEquals(1, images.size(), "One admitted PNG per ordered page");
                PDImageXObject embedded = images.get(0);
                assertEquals(8, embedded.getWidth());
                assertEquals(8, embedded.getHeight());
                if (expected.fixture().rgba())
                    assertNotNull(embedded.getCOSObject().getDictionaryObject(COSName.SMASK),
                            "RGBA must preserve its alpha soft mask");
                else
                    assertNull(embedded.getCOSObject().getDictionaryObject(COSName.SMASK),
                            "RGB must not acquire an unintended alpha mask");
                for (int dpi : new int[] {72, 144}) {
                    int scale = dpi / 72;
                    BufferedImage raster = renderer.renderImageWithDPI(page, dpi);
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                        Color expectedPixel = overWhite(
                                expected.fixture().original().getRGB(x, y));
                        assertPixel(expectedPixel,
                                raster.getRGB((53 + x * 10) * scale,
                                        (53 + y * 10) * scale),
                                "Page " + page + " dpi " + dpi + " source pixel " + x + "," + y);
                    }
                    for (int[] outside : new int[][] {
                            {46, 72}, {130, 72}, {72, 46}, {72, 130}})
                        assertPixel(Color.WHITE,
                                raster.getRGB(outside[0] * scale, outside[1] * scale),
                                "Page " + page + " dpi " + dpi + " outside image");
                }
            }
        }
        System.out.println("EMBASIC_TUR195_DPI_PARITY pages=4 dpi=72,144"
                + " pixels=512 rgb=2 rgba=2 softmask=EXPECTED mediaBox=ORDERED checks=PASS");
    }

    private static UdfReader.DocumentSpec styledTable(String name,
            DocumentElements.PageSpec page, String marker) throws Exception {
        UdfReader.Paragraph title = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("K ", new UdfReader.Style(14f, false, Color.RED)),
                new UdfReader.Run("V", new UdfReader.Style(18f, true, Color.BLUE))));
        DocumentElements.TableBlock table = new DocumentElements.TableBlock(
                List.of(110f, 110f), List.of(
                        new DocumentElements.Row(List.of(
                                new DocumentElements.Cell(marker + "L1", 1, 1),
                                new DocumentElements.Cell(marker + "R1", 1, 1)), 42f),
                        new DocumentElements.Row(List.of(
                                new DocumentElements.Cell(marker + "L2", 1, 1),
                                new DocumentElements.Cell(marker + "R2", 1, 1)), 42f)));
        return new UdfReader.DocumentSpec(name, "1.0", List.of(title),
                List.of(new DocumentElements.ParagraphBlock(title), table), page);
    }

    private static String pageText(PDDocument pdf, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page + 1);
        stripper.setEndPage(page + 1);
        return stripper.getText(pdf);
    }

    private static boolean dark(int argb) {
        Color c = new Color(argb);
        return c.getRed() < 110 && c.getGreen() < 110 && c.getBlue() < 110;
    }

    @Test void styledFontsTableRowsAndOrderedPdfStayIntactOnLateInvalidPng()
            throws Exception {
        List<DocumentElements.PageSpec> papers = List.of(A5, LEGAL,
                DocumentElements.PageSpec.a4());
        List<String> markers = List.of("AAA", "BBB", "CCC");
        List<UdfReader.DocumentSpec> ordered = new ArrayList<>();
        for (int i = 0; i < papers.size(); i++)
            ordered.add(styledTable(markers.get(i) + ".udf", papers.get(i), markers.get(i)));
        Path target = folder.resolve("approved-order.pdf");
        assertEquals(3, PdfExporter.save(ordered, target, false));
        byte[] approved = Files.readAllBytes(target);
        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertEquals(3, pdf.getNumberOfPages());
            for (int i = 0; i < ordered.size(); i++) {
                assertEquals(papers.get(i).width(),
                        pdf.getPage(i).getMediaBox().getWidth(), .03f);
                assertEquals(papers.get(i).height(),
                        pdf.getPage(i).getMediaBox().getHeight(), .03f);
                String text = pageText(pdf, i);
                for (String suffix : List.of("L1", "R1", "L2", "R2"))
                    assertTrue(text.contains(markers.get(i) + suffix),
                            "Missing table cell on ordered page " + i + ": " + text);
                for (int other = 0; other < markers.size(); other++)
                    if (other != i)
                        assertFalse(text.contains(markers.get(other) + "L1"),
                                "Cross-page text leaked from another UDF");
                BufferedImage raster = new PDFRenderer(pdf).renderImageWithDPI(i, 72);
                int horizontalLines = 0;
                for (int y = 75; y < 210; y++) {
                    int darkPixels = 0;
                    for (int x = 51; x < 265; x++)
                        if (dark(raster.getRGB(x, y))) darkPixels++;
                    if (darkPixels > 150) horizontalLines++;
                }
                assertTrue(horizontalLines >= 3,
                        "Table row borders absent on ordered page " + i);
            }
            class Glyphs extends PDFTextStripper {
                final List<TextPosition> positions = new ArrayList<>();
                Glyphs() throws Exception { setStartPage(1); setEndPage(1); }
                @Override protected void processTextPosition(TextPosition p) {
                    positions.add(p);
                    super.processTextPosition(p);
                }
            }
            Glyphs glyphs = new Glyphs();
            glyphs.getText(pdf);
            TextPosition k = glyphs.positions.stream()
                    .filter(p -> "K".equals(p.getUnicode())).findFirst().orElseThrow();
            TextPosition v = glyphs.positions.stream()
                    .filter(p -> "V".equals(p.getUnicode())).findFirst().orElseThrow();
            assertEquals(14f, k.getFontSizeInPt(), .2f);
            assertEquals(18f, v.getFontSizeInPt(), .2f);
            assertTrue(k.getFont().isEmbedded());
            assertTrue(v.getFont().isEmbedded());
            assertEquals(k.getYDirAdj(), v.getYDirAdj(), 1f,
                    "Styled runs must share one baseline");
        }

        var corrupted = new UdfReader.DocumentSpec("late-malformed.udf", "1.0",
                List.of(), List.of(new DocumentElements.ImageBlock(
                        new byte[] {0, 1, 2, 3}, 80f, 80f)), A5);
        List<UdfReader.DocumentSpec> withCorruptedTail = new ArrayList<>(ordered);
        withCorruptedTail.add(corrupted);
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(withCorruptedTail, target, true));
        assertArrayEquals(approved, Files.readAllBytes(target),
                "A late malformed PNG must not replace the committed ordered PDF");
        try (var listed = Files.list(folder)) {
            assertEquals(List.of("approved-order.pdf"),
                    listed.map(p -> p.getFileName().toString()).sorted().toList(),
                    "Fail-closed export must clean its staging files");
        }
        System.out.println("EMBASIC_TUR195_ORDER_FAIL_CLOSED pages=3"
                + " fonts=EMBEDDED tableRows=2 invalidTail=REJECTED"
                + " committedPdf=UNCHANGED staging=CLEAN checks=PASS");
    }
}
