package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
        Map<String,byte[]> excessive=items(page(ref("media/0.png"),"A4"),null,null);
        for(int i=0;i<65;i++) excessive.put("media/"+i+".png",png());
        assertThrows(IllegalArgumentException.class,()->UdfReader.read(archive("many.udf",excessive)));
    }
}
