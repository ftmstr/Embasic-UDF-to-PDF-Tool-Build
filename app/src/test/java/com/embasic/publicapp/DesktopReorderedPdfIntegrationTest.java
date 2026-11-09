package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rights-clean integration proof: the Explorer-style logical card order,
 * real synthetic ZIP inputs and the desktop's SHA-guarded save path must
 * agree on the exact PDF page sequence, geometry, colors and fonts.
 * Not evidence of native UYAP equivalence or a physical Robot UI run.
 */
final class DesktopReorderedPdfIntegrationTest {
    @TempDir Path folder;

    private record Card(Path source, UdfReader.DocumentSpec document, String fingerprint) {}

    private static String image(Color color) throws Exception {
        BufferedImage pixels = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                pixels.setRGB(x, y, color.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, "png", bytes));
        return "<image mime='image/png' encoding='base64' width='80' height='80'>"
                + Base64.getEncoder().encodeToString(bytes.toByteArray()) + "</image>";
    }

    private static String docXml(String word, String page, String style, String blocks) {
        return "<template format_id='1.0'><content>" + word + "</content>"
                + "<properties pageSize='" + page
                + "' orientation='" + ("A4".equals(page) ? "portrait" : "landscape")
                + "' margin='48'/><elements>" + blocks
                + "<paragraph><content startOffset='0' length='" + word.length()
                + "' " + style + "/></paragraph></elements></template>";
    }

    private static String a5TableWithImage() throws Exception {
        StringBuilder table = new StringBuilder("<table widths='110,110'>");
        for (int i = 0; i < 5; i++)
            table.append("<row height='120'><cell>A-ROW-").append(i)
                    .append("</cell><cell>A-RIGHT-").append(i)
                    .append("</cell></row>");
        table.append("</table>");
        return "<template format_id='1.0'><content></content>"
                + "<properties pageSize='A5' orientation='portrait' margin='48'/>"
                + "<elements>" + table + image(new Color(35, 190, 70))
                + "</elements></template>";
    }

    private Card write(String fileName, String xml) throws Exception {
        Path source = folder.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new Card(source, UdfReader.read(source), FileImportPolicy.sourceSha256(source));
    }

    private List<Card> fixtures() throws Exception {
        Card a = write("a5-table.udf", a5TableWithImage());
        Card b = write("letter-red.udf",
                docXml("LETTER-ID", "LETTER", "size='14' foreground='#E61928'",
                        image(new Color(210, 35, 45))));
        Card c = write("a4-blue.udf",
                docXml("A4-FINAL", "A4", "size='16' bold='true' foreground='#1020E8'", ""));
        Card d = write("legal-blue.udf",
                docXml("LEGAL-ID", "LEGAL", "size='18' bold='true' foreground='#1634D8'",
                        image(new Color(35, 55, 220))));
        return List.of(a, b, c, d);
    }

    private static int save(List<Card> queue, Path output, boolean overwrite) throws Exception {
        return DesktopApp.saveWorkspace(
                queue.stream().map(Card::source).toList(),
                queue.stream().map(Card::document).toList(),
                queue.stream().map(Card::fingerprint).toList(), output, overwrite);
    }

    private static String pageText(PDDocument pdf, int oneBasedPage) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(oneBasedPage);
        stripper.setEndPage(oneBasedPage);
        return stripper.getText(pdf);
    }

    private static void assertPage(PDDocument pdf, int index, float width, float height,
                                   String included, String excluded) throws Exception {
        assertEquals(width, pdf.getPage(index).getMediaBox().getWidth(), .03f);
        assertEquals(height, pdf.getPage(index).getMediaBox().getHeight(), .03f);
        String text = pageText(pdf, index + 1);
        assertTrue(text.contains(included), "Page " + (index + 1) + " missing " + included + ": " + text);
        assertFalse(text.contains(excluded), "Page " + (index + 1) + " contains foreign text: " + text);
    }

    private static void assertRgb(Color expected, int pixel, String message) {
        Color found = new Color(pixel, true);
        assertAll(message,
                () -> assertEquals(expected.getRed(), found.getRed(), 7),
                () -> assertEquals(expected.getGreen(), found.getGreen(), 7),
                () -> assertEquals(expected.getBlue(), found.getBlue(), 7));
    }

    private static void assertFont(PDDocument pdf, int oneBasedPage, float expectedSize)
            throws Exception {
        class Positions extends PDFTextStripper {
            final List<TextPosition> positions = new ArrayList<>();
            Positions() throws Exception {
                super();
                setStartPage(oneBasedPage);
                setEndPage(oneBasedPage);
            }
            @Override protected void processTextPosition(TextPosition position) {
                positions.add(position);
                super.processTextPosition(position);
            }
        }
        Positions positions = new Positions();
        positions.getText(pdf);
        assertFalse(positions.positions.isEmpty(), "No text on page " + oneBasedPage);
        TextPosition first = positions.positions.get(0);
        assertEquals(expectedSize, first.getFontSizeInPt(), .3f);
        assertTrue(first.getFont().isEmbedded(), "Saved PDF must embed its regular/bold font");
    }

    private static void assertInk(BufferedImage raster, boolean expectRed) {
        int pixels = 0;
        for (int y = 120; y < 185; y++)
            for (int x = 48; x < 235; x++) {
                int rgb = raster.getRGB(x, y);
                int red = (rgb >>> 16) & 255;
                int green = (rgb >>> 8) & 255;
                int blue = rgb & 255;
                if (expectRed ? red > 110 && red > green + 65 && red > blue + 65
                        : blue > 110 && blue > green + 65 && blue > red + 65)
                    pixels++;
            }
        assertTrue(pixels > 7, "Styled " + (expectRed ? "red" : "blue")
                + " text ink must survive serialization");
    }

    @Test void addTileMultiSelectionDeterminesExactFivePageExport() throws Exception {
        List<Card> imported = fixtures();
        // Ctrl-click order is reverse of visual order. Drag onto the '+' tile
        // must preserve original selection order and move both cards to the end.
        List<Card> queue = GridReorder.toEnd(imported, List.of(imported.get(2), imported.get(0)));
        assertEquals(List.of("letter-red.udf", "legal-blue.udf",
                        "a5-table.udf", "a4-blue.udf"),
                queue.stream().map(x -> x.source().getFileName().toString()).toList());
        assertEquals("a5-table.udf", imported.get(0).source().getFileName().toString(),
                "Reordering must not mutate the import queue itself");
        Path output = folder.resolve("selected-card-order.pdf");
        assertEquals(5, save(queue, output, false));
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(5, pdf.getNumberOfPages());
            assertPage(pdf, 0, 792f, 612f, "LETTER-ID", "LEGAL-ID");
            assertPage(pdf, 1, 1008f, 612f, "LEGAL-ID", "LETTER-ID");
            assertPage(pdf, 2, 419.53f, 595.28f, "A-ROW-0", "A-ROW-4");
            assertPage(pdf, 3, 419.53f, 595.28f, "A-ROW-4", "A-ROW-0");
            assertPage(pdf, 4, 595.28f, 841.89f, "A4-FINAL", "A-ROW-4");
            assertTrue(pageText(pdf, 3).contains("A-ROW-3"),
                    "A5 must retain the first four table rows on page one");
            assertFont(pdf, 1, 14f);
            assertFont(pdf, 2, 18f);
            assertFont(pdf, 5, 16f);

            PDFRenderer renderer = new PDFRenderer(pdf);
            BufferedImage letter = renderer.renderImageWithDPI(0, 72);
            BufferedImage legal = renderer.renderImageWithDPI(1, 72);
            BufferedImage a5second = renderer.renderImageWithDPI(3, 72);
            assertRgb(new Color(210, 35, 45), letter.getRGB(65, 65),
                    "Letter first-page image color/point placement");
            assertRgb(new Color(35, 55, 220), legal.getRGB(65, 65),
                    "Legal first-page image color/point placement");
            // A5 second page: one 120pt row, then an 8pt gap, then the 80pt
            // image begins 176 points from the top of the PDF page.
            assertRgb(new Color(35, 190, 70), a5second.getRGB(65, 195),
                    "A5 second-page image must not escape its own document");
            assertInk(letter, true);
            assertInk(legal, false);
        }
    }

    @Test void keyboardReorderMaintainsCardIdentityAndPdfPageOrder() throws Exception {
        List<Card> imported = fixtures();
        // Ctrl-selected nonadjacent cards move one logical position right,
        // even if their visible thumbnail components are offscreen.
        List<Card> queue = GridReorder.step(imported,
                List.of(imported.get(0), imported.get(2)), 1);
        assertEquals(List.of(imported.get(1), imported.get(0),
                        imported.get(3), imported.get(2)), queue);
        assertEquals(imported, GridReorder.step(queue,
                List.of(imported.get(0), imported.get(2)), -1));
        Path output = folder.resolve("keyboard-order.pdf");
        assertEquals(5, save(queue, output, false));
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertPage(pdf, 0, 792f, 612f, "LETTER-ID", "A-ROW-0");
            assertPage(pdf, 1, 419.53f, 595.28f, "A-ROW-0", "LEGAL-ID");
            assertPage(pdf, 2, 419.53f, 595.28f, "A-ROW-4", "LEGAL-ID");
            assertPage(pdf, 3, 1008f, 612f, "LEGAL-ID", "A4-FINAL");
            assertPage(pdf, 4, 595.28f, 841.89f, "A4-FINAL", "LEGAL-ID");
        }
    }

    @Test void changedReorderedSourcePreservesPreviouslyPublishedPdf() throws Exception {
        List<Card> imported = fixtures();
        List<Card> queue = GridReorder.toEnd(imported,
                List.of(imported.get(2), imported.get(0)));
        Path output = folder.resolve("already-approved.pdf");
        assertEquals(5, save(queue, output, false));
        byte[] original = Files.readAllBytes(output);
        // A card's stale parsed preview must never be used if the source
        // on disk changed after its SHA fingerprint was captured.
        Files.writeString(queue.get(3).source(), "not a ZIP anymore",
                StandardCharsets.UTF_8);
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> save(queue, output, true));
        assertTrue(rejected.getMessage().contains("değiştirildi"),
                rejected.getMessage());
        assertArrayEquals(original, Files.readAllBytes(output));
        try (var paths = Files.list(folder)) {
            assertEquals(0L, paths.filter(p -> p.getFileName().toString()
                    .startsWith(".embasic-pending-")).count());
        }
    }
}
