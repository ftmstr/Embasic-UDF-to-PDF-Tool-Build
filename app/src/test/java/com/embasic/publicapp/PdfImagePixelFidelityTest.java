package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
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


    /** A standard one-IDAT ImageIO fixture is split or CRC-repacked without UYAP data. */
    private static byte[] idatChunk(byte[] payload) {
        byte[] type = "IDAT".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer chunk = ByteBuffer.allocate(payload.length + 12);
        chunk.putInt(payload.length);
        chunk.put(type);
        chunk.put(payload);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(payload);
        chunk.putInt((int) crc.getValue());
        return chunk.array();
    }

    private static byte[] rebuiltIdat(byte[] source, boolean appendGarbage,
                                      boolean splitIdat) throws Exception {
        // The rights-clean TYPE_INT_RGB 8x8 fixture has IHDR directly before
        // one IDAT, followed by IEND. Verify that structure before modifying.
        int position = 33;
        int length = ByteBuffer.wrap(source, position, 4).getInt();
        assertEquals("IDAT", new String(source, position + 4, 4, StandardCharsets.US_ASCII));
        int after = position + 12 + length;
        assertEquals("IEND", new String(source, after + 4, 4, StandardCharsets.US_ASCII));
        byte[] zlib = Arrays.copyOfRange(source, position + 8, position + 8 + length);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(source, 0, position);
        if (splitIdat) {
            int mid = zlib.length / 2;
            result.write(idatChunk(Arrays.copyOfRange(zlib, 0, mid)));
            result.write(idatChunk(Arrays.copyOfRange(zlib, mid, zlib.length)));
        } else {
            byte[] payload = appendGarbage
                    ? Arrays.copyOf(zlib, zlib.length + 7) : zlib;
            if (appendGarbage) {
                // Valid PNG CRC, but content following the finished zlib
                // Adler-32 stream must not disappear during PDF export.
                System.arraycopy(new byte[]{71,65,82,66,65,71,69}, 0,
                        payload, zlib.length, 7);
            }
            result.write(idatChunk(payload));
        }
        result.write(source, after, source.length - after);
        return result.toByteArray();
    }

    @Test void pngIdatRequiresExactSingleZlibStreamWithoutDiscardingHiddenBytes()
            throws Exception {
        byte[] valid = fourColorPng();
        byte[] twoIdat = rebuiltIdat(valid, false, true);
        byte[] hiddenBytes = rebuiltIdat(valid, true, false);

        // Multiple consecutive IDAT chunks are legal; do not regress their
        // normal pixel content or reject a valid split stream.
        assertEquals(QUADRANTS[0].getRGB() & 0x00ffffff,
                DocumentElements.decodeBoundedPng(twoIdat).getRGB(1, 1) & 0x00ffffff);

        // Independently reproduce the platform's silent data-loss behavior.
        assertEquals(QUADRANTS[0].getRGB() & 0x00ffffff,
                ImageIO.read(new ByteArrayInputStream(hiddenBytes)).getRGB(1, 1)
                        & 0x00ffffff,
                "ImageIO accepts trailing IDAT data and ignores it");
        var error = assertThrows(IllegalArgumentException.class,
                () -> DocumentElements.decodeBoundedPng(hiddenBytes));
        assertTrue(error.getMessage().contains("zlib"), error.getMessage());

        Path output = folder.resolve("existing-approved.pdf");
        assertEquals(1, PdfExporter.save(List.of(imageOnly(
                "valid.udf", twoIdat, DocumentElements.PageSpec.a4())), output, false));
        byte[] approved = Files.readAllBytes(output);
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(imageOnly(
                        "hidden-png.udf", hiddenBytes, DocumentElements.PageSpec.a4())),
                        output, true));
        assertArrayEquals(approved, Files.readAllBytes(output),
                "Rejected malformed IDAT must preserve an already approved PDF");
        try (var files = Files.list(folder)) {
            assertEquals(List.of("existing-approved.pdf"),
                    files.map(path -> path.getFileName().toString()).sorted().toList(),
                    "Failed source must never leave a published staging PDF");
        }
    }


    private static byte[] pngChunk(String type, byte[] payload) throws Exception {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer chunk = ByteBuffer.allocate(payload.length + 12);
        chunk.putInt(payload.length);
        chunk.put(name);
        chunk.put(payload);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        chunk.putInt((int) crc.getValue());
        return chunk.array();
    }

    private static byte[] indexedPng(byte[] filteredRows, int height) throws Exception {
        // Fresh, rights-clean 1-column 8-bit indexed fixture, two PLTE colors.
        // No reliance on real native UYAP documents or external images.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteBuffer header = ByteBuffer.allocate(13);
        header.putInt(1).putInt(height).put((byte) 8).put((byte) 3)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        output.write(pngChunk("IHDR", header.array()));
        output.write(pngChunk("PLTE", new byte[]{
                (byte) 210, 30, 50, 20, (byte) 190, 70}));
        Deflater deflater = new Deflater();
        byte[] compressed = new byte[128];
        try {
            deflater.setInput(filteredRows);
            deflater.finish();
            int size = deflater.deflate(compressed);
            assertTrue(deflater.finished(), "Synthetic zlib must fit the buffer");
            output.write(pngChunk("IDAT", Arrays.copyOf(compressed, size)));
        } finally {
            deflater.end();
        }
        output.write(pngChunk("IEND", new byte[0]));
        return output.toByteArray();
    }

    @Test void indexedPngFilterReconstructionMustRejectOutOfRangePaletteColors()
            throws Exception {
        // Five independently filtered scanlines all reconstruct legal index 1:
        // None, Sub, Up, Average and Paeth. Keep these supported for PDF.
        byte[] filtered = {0, 1, 1, 1, 2, 0, 3, 1, 4, 0};
        byte[] valid = indexedPng(filtered, 5);
        BufferedImage decoded = DocumentElements.decodeBoundedPng(valid);
        for (int row = 0; row < 5; row++)
            assertEquals(new Color(20, 190, 70).getRGB(), decoded.getRGB(0, row),
                    "Valid PNG filter mode " + row + " must preserve palette color");

        byte[] unknownIndex = indexedPng(new byte[]{0, 5}, 1);
        BufferedImage ignored = ImageIO.read(new ByteArrayInputStream(unknownIndex));
        assertNotNull(ignored, "Independent ImageIO decoder accepts invalid index");
        assertEquals(Color.BLACK.getRGB(), ignored.getRGB(0, 0),
                "ImageIO silently invents black when the source palette has no index 5");
        var error = assertThrows(IllegalArgumentException.class,
                () -> DocumentElements.decodeBoundedPng(unknownIndex));
        assertTrue(error.getMessage().contains("palet"), error.getMessage());

        // The invalid sample may be hidden behind a valid PNG Up filter.
        byte[] filteredInvalid = filtered.clone();
        filteredInvalid[5] = 4; // row 2: previous index 1 + encoded 4 -> index 5
        byte[] invalidFiltered = indexedPng(filteredInvalid, 5);
        assertThrows(IllegalArgumentException.class,
                () -> DocumentElements.decodeBoundedPng(invalidFiltered));

        Path output = folder.resolve("approved-indexed.pdf");
        assertEquals(1, PdfExporter.save(List.of(imageOnly("valid.udf", valid,
                DocumentElements.PageSpec.a4())), output, false));
        byte[] approved = Files.readAllBytes(output);
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(imageOnly("bad-index.udf", invalidFiltered,
                        DocumentElements.PageSpec.a4())), output, true));
        assertArrayEquals(approved, Files.readAllBytes(output),
                "Rejected indexed PNG must not overwrite approved PDF bytes");
        try (var files = Files.list(folder)) {
            assertEquals(List.of("approved-indexed.pdf"),
                    files.map(p -> p.getFileName().toString()).sorted().toList(),
                    "Failed PDF export must remove staging files");
        }
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
    /**
     * A transparent 8-bit RGBA PNG must not silently lose its alpha channel
     * when PDFBox embeds it. Inspect the serialized PDF and independently
     * calculate white-page compositing from the original synthetic ARGB pixels.
     * This is a bounded, rights-clean fixture, not native UYAP parity.
     */
    @Test void rgbaPngPreservesAlphaThroughEmbeddedSoftMaskAndRenderedPage()
            throws Exception {
        Color[] sourceColors = {
            new Color(220, 20, 40, 255), new Color(20, 180, 70, 128),
            new Color(35, 55, 215, 64), new Color(180, 30, 90, 0)
        };
        BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                source.setRGB(x, y,
                        sourceColors[(y >= 4 ? 2 : 0) + (x >= 4 ? 1 : 0)].getRGB());
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "PNG", encoded));
        byte[] png = encoded.toByteArray();
        // PNG IHDR: color type 6 is RGBA, bit depth 8; no ancillary
        // color-management or transparency chunks are needed.
        assertEquals(8, png[24] & 255);
        assertEquals(6, png[25] & 255);
        Path output = folder.resolve("rgba-compositing.pdf");
        assertEquals(1, PdfExporter.save(List.of(
                imageOnly("rgba-synthetic.udf", png, DocumentElements.PageSpec.a4())),
                output, false));

        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            List<PDImageXObject> images = new ArrayList<>();
            var resources = pdf.getPage(0).getResources();
            for (var name : resources.getXObjectNames()) {
                var object = resources.getXObject(name);
                if (object instanceof PDImageXObject image) images.add(image);
            }
            assertEquals(1, images.size());
            assertNotNull(images.get(0).getCOSObject()
                    .getDictionaryObject(org.apache.pdfbox.cos.COSName.SMASK),
                    "Embedded RGBA must retain a PDF soft mask");
            BufferedImage page = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
            int[][] centers = {{68, 68}, {108, 68}, {68, 108}, {108, 108}};
            for (int i = 0; i < centers.length; i++) {
                Color sourceColor = sourceColors[i];
                int alpha = sourceColor.getAlpha();
                Color onWhite = new Color(
                        (sourceColor.getRed() * alpha + 255 * (255 - alpha) + 127) / 255,
                        (sourceColor.getGreen() * alpha + 255 * (255 - alpha) + 127) / 255,
                        (sourceColor.getBlue() * alpha + 255 * (255 - alpha) + 127) / 255);
                assertSameRgb(onWhite, page.getRGB(centers[i][0], centers[i][1]),
                        "RGBA source-to-PDF white compositing quadrant " + i);
            }
        }
        System.out.println("EMBASIC_RGBA_PDF_PARITY softMask=PRESENT"
                + " alpha=0,64,128,255 compositing=PASS pages=1");
    }

    /**
     * Across TWO page formats, verify every pixel in a rights-clean 8-bit
     * RGBA PNG after PDF alpha compositing, not only four quadrant centers.
     * At 72 DPI the placed 8x8 image occupies exactly 80x80 PDF points,
     * so every source pixel has a distinct 10x10 destination region.
     */
    @Test void rgbaAlphaGridPreservesAllPixelsAndPageBoundsOnA5AndLegal()
            throws Exception {
        BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int alpha = (x == 0 && y == 0) ? 0
                    : (x == 7 && y == 7) ? 255 : (x * 37 + y * 41 + 17) % 256;
            int red = (31 * x + 7 * y + 23) % 256;
            int green = (13 * x + 29 * y + 57) % 256;
            int blue = (17 * x + 11 * y + 149) % 256;
            source.setRGB(x, y, new Color(red, green, blue, alpha).getRGB());
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "PNG", encoded));
        byte[] png = encoded.toByteArray();
        assertEquals(8, png[24] & 255, "Only the admitted 8-bit PNG subset");
        assertEquals(6, png[25] & 255, "RGBA PNG must not be silently reclassified");
        var a5 = new DocumentElements.PageSpec(419.53f, 595.28f, 48f);
        var legalLandscape = new DocumentElements.PageSpec(1008f, 612f, 48f);
        Path output = folder.resolve("rgba-alpha-grid-two-pages.pdf");
        assertEquals(2, PdfExporter.save(List.of(
                imageOnly("rgba-a5-synthetic.udf", png, a5),
                imageOnly("rgba-legal-synthetic.udf", png, legalLandscape)),
                output, false));
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(2, pdf.getNumberOfPages());
            var renderer = new PDFRenderer(pdf);
            for (int p = 0; p < 2; p++) {
                var expectedPage = p == 0 ? a5 : legalLandscape;
                var page = pdf.getPage(p);
                assertEquals(expectedPage.width(), page.getMediaBox().getWidth(), .03f);
                assertEquals(expectedPage.height(), page.getMediaBox().getHeight(), .03f);
                List<PDImageXObject> images = new ArrayList<>();
                for (var name : page.getResources().getXObjectNames()) {
                    var image = page.getResources().getXObject(name);
                    if (image instanceof PDImageXObject pngImage) images.add(pngImage);
                }
                assertEquals(1, images.size(), "One PNG with no duplicated or lost XObjects");
                assertEquals(8, images.get(0).getWidth());
                assertEquals(8, images.get(0).getHeight());
                assertNotNull(images.get(0).getCOSObject().getDictionaryObject(
                        org.apache.pdfbox.cos.COSName.SMASK),
                        "A distinct page must retain its PNG alpha soft mask");
                BufferedImage raster = renderer.renderImageWithDPI(p, 72);
                // Independent per-pixel oracle from the original raw ARGB,
                // with integer white-composite rounding. No golden PDF used.
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    Color c = new Color(source.getRGB(x, y), true);
                    int a = c.getAlpha();
                    Color expected = new Color(
                            (c.getRed() * a + 255 * (255 - a) + 127) / 255,
                            (c.getGreen() * a + 255 * (255 - a) + 127) / 255,
                            (c.getBlue() * a + 255 * (255 - a) + 127) / 255);
                    assertSameRgb(expected, raster.getRGB(53 + 10 * x, 53 + 10 * y),
                            "Page " + p + " RGBA pixel " + x + "," + y);
                }
                // No image ink may leak outside the exact 48..128pt bounds.
                for (int[] outside : new int[][] {{46, 72}, {130, 72},
                        {72, 46}, {72, 130}})
                    assertSameRgb(Color.WHITE,
                            raster.getRGB(outside[0], outside[1]),
                            "Page " + p + " transparent image outside its box");
            }
        }
        System.out.println("EMBASIC_RGBA_GRID_PARITY pages=2 pixels=128"
                + " alpha=PER_PIXEL softMask=BOTH geometry=A5_LEGAL"
                + " outsideBounds=WHITE checks=PASS");
    }

}
