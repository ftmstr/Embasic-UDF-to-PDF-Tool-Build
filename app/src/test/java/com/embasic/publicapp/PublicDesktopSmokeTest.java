package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
import java.util.ArrayList;
import org.apache.pdfbox.text.TextPosition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicDesktopSmokeTest {
    @TempDir Path root;

    private static String xml(String value) {
        return "<template format_id='1.0'><content>" + value + "</content>"
            + "<elements><paragraph><content startOffset='0' length='" + value.length() + "'/>"
            + "</paragraph></elements></template>";
    }
    private Path udf(String name, String value) throws Exception {
        Path p = root.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(p))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml(value).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return p;
    }

    @Test void singleAndOrderedMerge() throws Exception {
        var first = UdfReader.read(udf("first.udf", "SMOKE-A İzmir ğüş"));
        var second = UdfReader.read(udf("second.udf", "SMOKE-B"));
        assertEquals("1.0", first.formatId());
        assertTrue(first.paragraphs().get(0).runs().get(0).text().contains("İzmir"));
        Path pdf = root.resolve("combined.pdf");
        assertEquals(2, PdfExporter.save(List.of(first, second), pdf, false));
        try (PDDocument loaded = Loader.loadPDF(pdf.toFile())) {
            assertEquals(2, loaded.getNumberOfPages());
            PDFTextStripper reader = new PDFTextStripper();
            reader.setStartPage(1); reader.setEndPage(1);
            String a = reader.getText(loaded);
            reader.setStartPage(2); reader.setEndPage(2);
            String b = reader.getText(loaded);
            assertTrue(a.contains("SMOKE-A"), a);
            assertTrue(a.contains("İzmir"), a);
            assertTrue(b.contains("SMOKE-B"), b);
        }
        byte[] original = Files.readAllBytes(pdf);
        assertThrows(IllegalArgumentException.class, () -> PdfExporter.save(List.of(first), pdf, false));
        assertArrayEquals(original, Files.readAllBytes(pdf), "Existing PDF must survive a denied overwrite");
    }

    @Test void previewIsRenderedOnFirstPage() throws Exception {
        var spec = UdfReader.read(udf("preview.udf", "Deneme PDF önizleme"));
        var image = PdfExporter.firstPage(spec);
        assertTrue(image.getWidth() > 100);
        assertTrue(image.getHeight() > 200);
    }

    @Test void invalidZipXmlAndUnsupportedTableFailClosed() throws Exception {
        Path broken = root.resolve("broken.udf");
        Files.writeString(broken, "not a zip");
        assertThrows(Exception.class, () -> UdfReader.read(broken));
        assertThrows(IllegalArgumentException.class,
            () -> UdfReader.parse(xml("abc").replace("format_id='1.0'", "").getBytes(StandardCharsets.UTF_8), "x.udf"));
        assertThrows(IllegalArgumentException.class,
            () -> UdfReader.parse(("<template format_id='1.0'><content>x</content>"
                + "<elements><table><row/></table></elements></template>").getBytes(StandardCharsets.UTF_8), "x.udf"));
        assertThrows(Exception.class, () -> UdfReader.parse(
            "<!DOCTYPE template [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><template format_id='1.0'>"
                .getBytes(StandardCharsets.UTF_8), "x.udf"));
    }

    @Test void unknownArchivePayloadsAreRejectedInsteadOfSilentlyIgnored() throws Exception {
        // Only the public synthetic subset is supported: content.xml and ZIP-local PNGs.
        // Unknown members may carry layout or document content and must not disappear.
        int index = 0;
        for (String extra : List.of("metadata.xml", "notes.txt", "assets/hidden.png")) {
            Path input = root.resolve("unknown-member-" + index++ + ".udf");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(input))) {
                zip.putNextEntry(new ZipEntry("content.xml"));
                zip.write(xml("VISIBLE").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(extra));
                zip.write("UNSUPPORTED".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            assertThrows(IllegalArgumentException.class, () -> UdfReader.read(input), extra);
        }
    }

    @Test void surrogateSplittingAndDuplicateXmlFailClosed() throws Exception {
        var xml = "<template format_id='1.0'><content>😀</content><elements><paragraph>"
                + "<content startOffset='1' length='1'/></paragraph></elements></template>";
        assertThrows(IllegalArgumentException.class,
                () -> UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8), "x.udf"));
        Path duplicate = root.resolve("duplicate.udf");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(duplicate))) {
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml("X").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("./content.xml"));
            zip.write(xml("Y").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> UdfReader.read(duplicate));
    }

    @Test void unsupportedPageFormatIsRejectedInsteadOfSilentlyRenderedOnA4() throws Exception {
        var unsupported = "<template format_id='1.0'><content>x</content>"
            + "<properties><pageFormat width='800' height='800'/></properties>"
            + "<elements><paragraph><content startOffset='0' length='1'/></paragraph>"
            + "</elements></template>";
        assertThrows(IllegalArgumentException.class, () ->
            UdfReader.parse(unsupported.getBytes(StandardCharsets.UTF_8), "layout.udf"));
    }

    @Test void wrappedLargeFontDoesNotOverlapFollowingParagraph() throws Exception {
        var plain = new UdfReader.Style(11f, false, Color.BLACK);
        var large = new UdfReader.Style(32f, true, Color.BLACK);
        // Tabs place the cursor near the A4 right margin so LARGE has to wrap.
        var first = new UdfReader.Paragraph(List.of(
            new UdfReader.Run("\t".repeat(13), plain),
            new UdfReader.Run("LARGE", large)));
        var next = new UdfReader.Paragraph(List.of(new UdfReader.Run("FOLLOW", plain)));
        var spec = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(first, next));
        try (PDDocument pdf = PdfExporter.render(List.of(spec))) {
            List<Float> largeY = new ArrayList<>();
            List<Float> followY = new ArrayList<>();
            var extractor = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition glyph) {
                    if ("L".equals(glyph.getUnicode())) largeY.add(glyph.getYDirAdj());
                    if ("F".equals(glyph.getUnicode())) followY.add(glyph.getYDirAdj());
                    super.processTextPosition(glyph);
                }
            };
            String extracted = extractor.getText(pdf);
            assertTrue(extracted.contains("LARGE"), extracted);
            assertTrue(extracted.contains("FOLLOW"), extracted);
            assertFalse(largeY.isEmpty(), "LARGE must be embedded and extractable");
            assertFalse(followY.isEmpty(), "FOLLOW must be embedded and extractable");
            float lineDistance = followY.get(0) - largeY.get(0);
            assertTrue(lineDistance > 38f, "Large-font line must not overlap next paragraph: " + lineDistance);
        }
    }

    @Test void invalidOutputIsRejectedWithoutOverwritingSource() throws Exception {
        Path source = udf("original.udf", "A");
        byte[] before = Files.readAllBytes(source);
        var spec = UdfReader.read(source);
        assertThrows(IllegalArgumentException.class,
            () -> PdfExporter.save(List.of(spec), source, true));
        assertArrayEquals(before, Files.readAllBytes(source));
    }
}
