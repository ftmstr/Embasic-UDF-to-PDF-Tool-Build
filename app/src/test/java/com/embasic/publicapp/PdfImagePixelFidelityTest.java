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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rights-clean, original RGB fixtures. Expected pixels and placement come
 * from independently declared source colors and point coordinates, not from
 * comparing the exporter against its own prior output.
 * No native UYAP/PDF equivalence is claimed.
 */
final class PdfImagePixelFidelityTest {
    @TempDir Path folder;

    private static final Color[] QUADRANTS = {
        new Color(240, 25, 35), new Color(20, 200, 40),
        new Color(25, 45, 230), new Color(240, 205, 20)
    };

    private static byte[] fourColorPng() throws Exception {
        BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++)
            for (int x = 0; x < source.getWidth(); x++)
                source.setRGB(x, y, QUADRANTS[(y >= 4 ? 2 : 0) + (x >= 4 ? 1 : 0)].getRGB());
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "PNG", encoded));
        return encoded.toByteArray();
    }

    private static UdfReader.DocumentSpec imageOnly(String name, byte[] png,
                                                      DocumentElements.PageSpec paper) {
        return new UdfReader.DocumentSpec(name, "1.0", List.of(),
                List.of(new DocumentElements.ImageBlock(png, 80f, 80f)), paper);
    }

    private static void assertSameRgb(Color expected, int actual, String context) {
        Color found = new Color(actual, true);
        assertAll(context,
                () -> assertEquals(expected.getRed(), found.getRed(), 8),
                () -> assertEquals(expected.getGreen(), found.getGreen(), 8),
                () -> assertEquals(expected.getBlue(), found.getBlue(), 8));
    }

    @Test void savedImageKeepsQuadrantPixelsAndPointPlacementOnBothPageSizes()
            throws Exception {
        byte[] png = fourColorPng();
        var landscape = new DocumentElements.PageSpec(841.89f, 595.28f, 48f);
        var portrait = new DocumentElements.PageSpec(419.53f, 595.28f, 48f);
        Path output = folder.resolve("image-layout.pdf");
        assertEquals(2, PdfExporter.save(List.of(
                imageOnly("landscape.udf", png, landscape),
                imageOnly("portrait.udf", png, portrait)), output, false));

        try (PDDocument saved = Loader.loadPDF(output.toFile())) {
            assertEquals(2, saved.getNumberOfPages());
            var renderer = new PDFRenderer(saved);
            for (int pageNumber = 0; pageNumber < 2; pageNumber++) {
                var paper = pageNumber == 0 ? landscape : portrait;
                var page = saved.getPage(pageNumber);
                assertEquals(paper.width(), page.getMediaBox().getWidth(), .03f);
                assertEquals(paper.height(), page.getMediaBox().getHeight(), .03f);
                // At 72 DPI, a PDF point is one raster pixel. Image upper left
                // is at the page's 48pt top/left margin; each quadrant is 40pt.
                BufferedImage raster = renderer.renderImageWithDPI(pageNumber, 72);
                int[][] centers = {{68, 68}, {108, 68}, {68, 108}, {108, 108}};
                for (int quadrant = 0; quadrant < centers.length; quadrant++) {
                    int[] center = centers[quadrant];
                    assertSameRgb(QUADRANTS[quadrant],
                            raster.getRGB(center[0], center[1]),
                            "Page " + pageNumber + ", visual quadrant " + quadrant);
                }

                List<PDImageXObject> images = new ArrayList<>();
                for (var name : page.getResources().getXObjectNames()) {
                    var object = page.getResources().getXObject(name);
                    if (object instanceof PDImageXObject image) images.add(image);
                }
                assertEquals(1, images.size(), "Image must survive serialization");
                BufferedImage decoded = images.get(0).getImage();
                assertEquals(8, decoded.getWidth());
                assertEquals(8, decoded.getHeight());
                int[][] originalCenters = {{2, 2}, {6, 2}, {2, 6}, {6, 6}};
                for (int quadrant = 0; quadrant < originalCenters.length; quadrant++) {
                    int[] center = originalCenters[quadrant];
                    assertSameRgb(QUADRANTS[quadrant],
                            decoded.getRGB(center[0], center[1]),
                            "Page " + pageNumber + ", embedded PNG quadrant " + quadrant);
                }
            }
        }
    }

    @Test void mixedStyledParagraphTableAndImageSurviveSingleSerializedPage()
            throws Exception {
        byte[] png = fourColorPng();
        var paragraph = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("KIRMIZI ", new UdfReader.Style(14f, false, Color.RED)),
                new UdfReader.Run("MAVI", new UdfReader.Style(18f, true, Color.BLUE))));
        var table = new DocumentElements.TableBlock(List.of(120f, 120f), List.of(
                new DocumentElements.Row(List.of(
                        new DocumentElements.Cell("SOL", 1, 1),
                        new DocumentElements.Cell("SAG", 1, 1)), 32f)));
        var spec = new UdfReader.DocumentSpec("mixed-synthetic.udf", "1.0",
                List.of(paragraph), List.of(
                        new DocumentElements.ParagraphBlock(paragraph), table,
                        new DocumentElements.ImageBlock(png, 80f, 80f)),
                DocumentElements.PageSpec.a4());
        Path output = folder.resolve("mixed-content.pdf");
        assertEquals(1, PdfExporter.save(List.of(spec), output, false));
        try (PDDocument saved = Loader.loadPDF(output.toFile())) {
            assertEquals(1, saved.getNumberOfPages());
            String text = new PDFTextStripper().getText(saved);
            for (String expected : List.of("KIRMIZI", "MAVI", "SOL", "SAG"))
                assertTrue(text.contains(expected), "Missing " + expected + ": " + text);
            long images = 0;
            for (var name : saved.getPage(0).getResources().getXObjectNames())
                if (saved.getPage(0).getResources().getXObject(name) instanceof PDImageXObject)
                    images++;
            assertEquals(1, images, "Image must not disappear after the table");
        }
    }

    @Test void malformedLaterImageCannotReplaceAnAlreadyPublishedPdf() throws Exception {
        byte[] valid = fourColorPng();
        var first = imageOnly("first.udf", valid, DocumentElements.PageSpec.a4());
        Path target = folder.resolve("published.pdf");
        assertEquals(1, PdfExporter.save(List.of(first), target, false));
        byte[] before = Files.readAllBytes(target);
        var damaged = imageOnly("damaged.udf", new byte[] {1, 2, 3, 4},
                DocumentElements.PageSpec.a4());

        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(first, damaged), target, true));
        assertArrayEquals(before, Files.readAllBytes(target),
                "Late invalid PNG must not replace a previous valid PDF");
        try (var pending = Files.list(folder)) {
            assertEquals(List.of("published.pdf"),
                    pending.map(path -> path.getFileName().toString()).sorted().toList(),
                    "Failed conversion must clean staging files");
        }
    }
}
