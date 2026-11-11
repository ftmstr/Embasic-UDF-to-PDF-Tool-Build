package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** All fixtures invented for this repository; never use privileged UYAP case documents. */
final class ReferencedMediaRegressionTest {
    @TempDir Path folder;

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            image.setRGB(x,y,((x+y)%2==0?Color.BLUE:Color.ORANGE).getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image,"PNG",output));
        return output.toByteArray();
    }
    private static byte[] insertPngChunk(byte[] original, String type, byte[] payload)
            throws Exception {
        // Java ImageIO emits an eight-byte PNG signature and 25-byte IHDR.
        assertEquals(13, java.nio.ByteBuffer.wrap(original, 8, 4).getInt());
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(chunk)) {
            byte[] name = type.getBytes(StandardCharsets.US_ASCII);
            data.writeInt(payload.length);
            data.write(name);
            data.write(payload);
            var crc = new java.util.zip.CRC32();
            crc.update(name);
            crc.update(payload);
            data.writeInt((int) crc.getValue());
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(original, 0, 33);
        result.write(chunk.toByteArray());
        result.write(original, 33, original.length - 33);
        return result.toByteArray();
    }
    private static String page(String imageElement, String size) {
        return "<template format_id='1.0'><content>BEFORE AFTER</content>"
            +"<properties pageSize='"+size+"' margin='32' orientation='landscape'/>"
            +"<elements><paragraph><content startOffset='0' length='6'/></paragraph>"
            +imageElement
            +"<paragraph><content startOffset='7' length='5'/></paragraph></elements></template>";
    }
    private static String ref(String path) {
        return "<image ref='"+path+"' mime='image/png' width='40' height='40'/>";
    }
    private Path archive(String name, Map<String,byte[]> entries) throws Exception {
        Path file=folder.resolve(name);
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(file))) {
            for(var entry:entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }
    private static Map<String,byte[]> items(String xml,String resource,byte[] bytes) {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        entries.put("content.xml",xml.getBytes(StandardCharsets.UTF_8));
        if(resource!=null)entries.put(resource,bytes);
        return entries;
    }

    @Test void zippedImageReferenceAndPageFormatsRoundTrip() throws Exception {
        for(String size:List.of("A5","LEGAL")) {
            Map<String,byte[]> canonical=items(page(ref("media/logo.png"),size),
                    "media/logo.png",png());
            // A normal ZIP directory marker remains valid; only ambiguous
            // member aliases and path separators are rejected.
            canonical.put("media/",new byte[0]);
            var spec=UdfReader.read(archive("fixture-"+size+".udf",canonical));
            assertEquals(3,spec.blocks().size());
            assertTrue(spec.blocks().get(1) instanceof DocumentElements.ImageBlock);
            assertEquals(size.equals("A5")?595.28f:1008f,spec.page().width(),0.1);
            Path target=folder.resolve("fixture-"+size+".pdf");
            assertEquals(1,PdfExporter.save(List.of(spec),target,false));
            try(PDDocument pdf=Loader.loadPDF(target.toFile())) {
                String text=new PDFTextStripper().getText(pdf);
                assertTrue(text.indexOf("BEFORE")>=0,text);
                assertTrue(text.indexOf("AFTER")>text.indexOf("BEFORE"),text);
                boolean foundImage=false;
                for(var n:pdf.getPage(0).getResources().getXObjectNames())
                    if(pdf.getPage(0).getResources().getXObject(n) instanceof PDImageXObject)
                        foundImage=true;
                assertTrue(foundImage,"referenced PNG PDF XObject");
            }
        }
    }
    @Test void invalidMissingOrExternalReferencesAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
            ()->UdfReader.read(archive("missing.udf",items(page(ref("media/missing.png"),"A4"),null,null))));
        for(String invalid:List.of("../media/logo.png","https://server.invalid/p.png",
            "media/../logo.png","C:/images/logo.png","media/%2e%2e/x.png",
            "media\\logo.png","/media/logo.png")) {
            assertThrows(IllegalArgumentException.class,
                ()->UdfReader.parse(page(ref(invalid),"A4").getBytes(StandardCharsets.UTF_8),"x.udf"),
                invalid);
        }
        String mixed="<image ref='media/logo.png' mime='image/png' width='40' height='40'>AA==</image>";
        assertThrows(IllegalArgumentException.class,
            ()->UdfReader.read(archive("mixed.udf",items(page(mixed,"A4"),"media/logo.png",png()))));
    }
    @Test void spoofedPayloadDuplicateAndResourceBudgetsAreRejected() throws Exception {
        byte[] spoofed="not a PNG".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(
            archive("spoof.udf",items(page(ref("images/logo.png"),"A4"),"images/logo.png",spoofed))));
        byte[] tooLarge=new byte[1_048_577];
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(
            archive("large.udf",items(page(ref("media/logo.png"),"A4"),"media/logo.png",tooLarge))));
        Map<String,byte[]> entries=items(page(ref("media/logo.png"),"A4"),"media/logo.png",png());
        entries.put("media/LOGO.png",png());
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(archive("duplicate.udf",entries)));
        // Previously these names were rewritten into media/logo.png and
        // could silently change the ZIP's intended resource identity.
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(archive(
            "backslash.udf",items(page(ref("media/logo.png"),"A4"),"media\\\\logo.png",png()))));
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(archive(
            "dot-segment.udf",items(page(ref("media/logo.png"),"A4"),"./media/logo.png",png()))));
        // A directory entry is not a place to hide discarded document data.
        // Keep canonical empty media/ markers valid (covered above), but
        // reject unknown directory namespaces and nonempty directory records.
        Map<String,byte[]> unknownDirectory=items(page(ref("media/logo.png"),"A4"),
                "media/logo.png",png());
        unknownDirectory.put("unrecognized/",new byte[0]);
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(
            archive("unknown-directory.udf",unknownDirectory)));
        Map<String,byte[]> dataDirectory=items(page(ref("media/logo.png"),"A4"),
                "media/logo.png",png());
        dataDirectory.put("media/",new byte[]{42});
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(
            archive("directory-payload.udf",dataDirectory)));
        Map<String,byte[]> invalidNestedDirectory=items(page(ref("media/logo.png"),"A4"),
                "media/logo.png",png());
        invalidNestedDirectory.put("media/unexpected%path/",new byte[0]);
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(
            archive("invalid-directory-path.udf",invalidNestedDirectory)));
        Map<String,byte[]> excessive=items(page(ref("media/0.png"),"A4"),null,null);
        for(int i=0;i<65;i++) excessive.put("media/"+i+".png",png());
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(archive("many.udf",excessive)));
    }

    @Test void directImageBlocksObeyPngByteAndPixelBudgetsBeforePdfPublication() throws Exception {
        // Reject animation that ImageIO could flatten to its first frame,
        // and trailing bytes that ImageIO could silently ignore after IEND.
        byte[] canonical = png();
        byte[] animated = insertPngChunk(canonical, "acTL",
                new byte[]{0, 0, 0, 2, 0, 0, 0, 0});
        byte[] trailing = java.util.Arrays.copyOf(canonical, canonical.length + 3);
        trailing[canonical.length] = 42;
        for (int index = 0; index < 2; index++) {
            byte[] invalid = index == 0 ? animated : trailing;
            String name = index == 0 ? "animated" : "trailing";
            assertThrows(IllegalArgumentException.class, () -> UdfReader.read(
                    archive(name + ".udf", items(page(ref("media/logo.png"), "A4"),
                            "media/logo.png", invalid))), name);
            var direct = new UdfReader.DocumentSpec(name + ".udf", "1.0",
                    List.of(), List.of(new DocumentElements.ImageBlock(invalid, 40f, 40f)),
                    DocumentElements.PageSpec.a4());
            Path rejected = folder.resolve(name + ".pdf");
            assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.save(List.of(direct), rejected, false), name);
            assertFalse(Files.exists(rejected), name + " must not publish a partial PDF");
        }
        // Public ImageBlock constructors bypass XML parsing. A valid direct
        // PNG still renders, but oversized or dimension-forged PNGs must not
        // allocate a huge raster or publish a misleading partial PDF.
        var good = new UdfReader.DocumentSpec("direct-good.udf", "1.0", List.of(),
                List.of(new DocumentElements.ImageBlock(png(), 40f, 40f)),
                DocumentElements.PageSpec.a4());
        try (PDDocument pdf = PdfExporter.render(List.of(good))) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }

        byte[] oversized = new byte[1_048_577];
        oversized[0] = (byte) 0x89;
        oversized[1] = 'P'; oversized[2] = 'N'; oversized[3] = 'G';
        var tooManyBytes = new UdfReader.DocumentSpec("direct-large.udf", "1.0", List.of(),
                List.of(new DocumentElements.ImageBlock(oversized, 40f, 40f)),
                DocumentElements.PageSpec.a4());
        assertThrows(IllegalArgumentException.class,
                () -> { try (PDDocument ignored = PdfExporter.render(List.of(tooManyBytes))) {} });
        Path rejected = folder.resolve("direct-large.pdf");
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(tooManyBytes), rejected, false));
        assertFalse(Files.exists(rejected));

        // Forge an IHDR claiming 4,001,000 pixels while retaining a tiny PNG
        // body. Update its CRC so metadata is parseable. The pixel guard must
        // reject the image before ImageReader.read(0) can allocate its raster.
        byte[] forged = png();
        java.nio.ByteBuffer.wrap(forged).putInt(16, 4001).putInt(20, 1000);
        var crc = new java.util.zip.CRC32();
        crc.update(forged, 12, 17);
        java.nio.ByteBuffer.wrap(forged).putInt(29, (int) crc.getValue());
        var tooManyPixels = new UdfReader.DocumentSpec("direct-pixels.udf", "1.0", List.of(),
                List.of(new DocumentElements.ImageBlock(forged, 40f, 40f)),
                DocumentElements.PageSpec.a4());
        var failure = assertThrows(IllegalArgumentException.class,
                () -> { try (PDDocument ignored = PdfExporter.render(List.of(tooManyPixels))) {} });
        assertTrue(failure.getMessage().contains("piksel"), failure.getMessage());

        // Direct public records do not pass through the XML geometry parser.
        // Negative, zero and non-finite placement must fail before a PDF is
        // staged, even when the PNG bytes themselves are valid.
        for (float invalid : List.of(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            for (boolean invalidWidth : List.of(true, false)) {
                var block = new DocumentElements.ImageBlock(png(),
                        invalidWidth ? invalid : 40f, invalidWidth ? 40f : invalid);
                var spec = new UdfReader.DocumentSpec("bad-image-geometry.udf", "1.0",
                        List.of(), List.of(block), DocumentElements.PageSpec.a4());
                var error = assertThrows(IllegalArgumentException.class,
                        () -> { try (PDDocument ignored = PdfExporter.render(List.of(spec))) {} });
                assertTrue(error.getMessage().contains("geometrisi"), error.getMessage());
            }
        }
        for (float invalid : List.of(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            for (int component = 0; component < 3; component++) {
                var pageSpec = new DocumentElements.PageSpec(
                        component == 0 ? invalid : 595.28f,
                        component == 1 ? invalid : 841.89f,
                        component == 2 ? invalid : 48f);
                var spec = new UdfReader.DocumentSpec("bad-page-geometry.udf", "1.0",
                        List.of(), List.of(new DocumentElements.ImageBlock(png(), 40f, 40f)),
                        pageSpec);
                var error = assertThrows(IllegalArgumentException.class,
                        () -> { try (PDDocument ignored = PdfExporter.render(List.of(spec))) {} });
                assertTrue(error.getMessage().contains("sayfa geometrisi"), error.getMessage());
            }
        }
        // Direct PageSpec bypasses the XML reader's A4/A5/Letter/Legal
        // allowlist and 100-point margin ceiling. Reject unsupported
        // geometry before staging any PDF, even for an image-only document.
        for (var pageSpec : List.of(
                new DocumentElements.PageSpec(14400f, 841.89f, 48f),
                new DocumentElements.PageSpec(700f, 700f, 48f),
                new DocumentElements.PageSpec(595.28f, 841.89f, 101f))) {
            var spec = new UdfReader.DocumentSpec("unsupported-page.udf", "1.0",
                    List.of(), List.of(new DocumentElements.ImageBlock(png(), 40f, 40f)),
                    pageSpec);
            var error = assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(List.of(spec))) {} });
            assertTrue(error.getMessage().contains("sayfa geometrisi"), error.getMessage());
            Path rejectedPageOutput = folder.resolve("unsupported-page.pdf");
            assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.save(List.of(spec), rejectedPageOutput, false));
            assertFalse(Files.exists(rejectedPageOutput), "Unsupported page must not publish a PDF");
        }
        // Valid XML-equivalent landscape sizes and margins remain accepted.
        for (var pageSpec : List.of(
                new DocumentElements.PageSpec(792f, 612f, 36f),
                new DocumentElements.PageSpec(595.28f, 419.53f, 100f))) {
            var spec = new UdfReader.DocumentSpec("landscape.udf", "1.0",
                    List.of(), List.of(new DocumentElements.ImageBlock(png(), 40f, 40f)),
                    pageSpec);
            try (PDDocument pdf = PdfExporter.render(List.of(spec))) {
                assertEquals(1, pdf.getNumberOfPages());
                assertEquals(pageSpec.width(), pdf.getPage(0).getMediaBox().getWidth(), .01f);
                assertEquals(pageSpec.height(), pdf.getPage(0).getMediaBox().getHeight(), .01f);
            }
        }
        Path invalidTarget = folder.resolve("invalid-geometry.pdf");
        var invalidSpec = new UdfReader.DocumentSpec("invalid.udf", "1.0", List.of(),
                List.of(new DocumentElements.ImageBlock(png(), Float.NaN, 40f)),
                DocumentElements.PageSpec.a4());
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(invalidSpec), invalidTarget, false));
        assertFalse(Files.exists(invalidTarget));
    }
}
