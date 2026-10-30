package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

final class StructuredUdfRegressionTest {
    private static UdfReader.DocumentSpec parse(String xml) throws Exception {
        return UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8),"synthetic.udf");
    }
    private static String container(String inner,String properties) {
        return "<template format_id='1.0'><content>ÖN SON</content>"+properties+
            "<elements>"+inner+"</elements></template>";
    }
    private static String para(int start,int len) {
        return "<paragraph><content startOffset='"+start+"' length='"+len+"'/></paragraph>";
    }
    private static String png() throws Exception {
        BufferedImage picture=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            picture.setRGB(x,y,((x+y)%2==0?Color.BLUE:Color.RED).getRGB());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(picture,"png",out));
        return "<image mime='image/png' encoding='base64' width='52' height='52'>"+
            Base64.getEncoder().encodeToString(out.toByteArray())+"</image>";
    }
    @Test void mixedOrderedParagraphMergedTablePngAndLetterLandscape() throws Exception {
        String table="<table widths='80,110'><row height='28'><cell colspan='2'>BAŞLIK</cell></row>"+
            "<row height='27'><cell rowspan='2'>A</cell><cell>B</cell></row>"+
            "<row height='27'><cell>C</cell></row></table>";
        var doc=parse(container(para(0,2)+table+png()+para(3,3),
                "<properties pageSize='LETTER' orientation='landscape' margin='36'/>"));
        assertEquals(4,doc.blocks().size());
        assertEquals(792f,doc.page().width(),.01);
        assertEquals(612f,doc.page().height(),.01);
        var geometry=TableLayout.calculate((DocumentElements.TableBlock)doc.blocks().get(1));
        assertEquals(4,geometry.cells().size());
        assertEquals(190f,geometry.cells().get(0).width(),.01);
        assertEquals(54f,geometry.cells().get(1).height(),.01);
        try(PDDocument pdf=PdfExporter.render(List.of(doc))) {
            assertEquals(1,pdf.getNumberOfPages());
            PDPage page=pdf.getPage(0);
            assertEquals(792f,page.getMediaBox().getWidth(),.01);
            assertEquals(612f,page.getMediaBox().getHeight(),.01);
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.indexOf("ÖN")>=0,text);
            assertTrue(text.indexOf("BAŞLIK")>text.indexOf("ÖN"),text);
            assertTrue(text.indexOf("SON")>text.indexOf("BAŞLIK"),text);
            boolean image=false;
            PDResources resources=page.getResources();
            for(var name:resources.getXObjectNames())
                if(resources.getXObject(name) instanceof PDImageXObject)image=true;
            assertTrue(image,"PNG must be embedded as PDF image XObject");
        }
    }
    @Test void longTablePaginatesInRowOrderAndKeepsRowspanTogether() throws Exception {
        StringBuilder rows=new StringBuilder();
        for(int i=0;i<38;i++) {
            if(i==23) {
                rows.append("<row height='36'><cell rowspan='2'>SPAN</cell><cell>R23</cell></row>");
            } else if(i==24) {
                rows.append("<row height='36'><cell>R24</cell></row>");
            } else {
                rows.append("<row height='36'><cell colspan='2'>R").append(i).append("</cell></row>");
            }
        }
        var doc=parse(container("<table widths='80,100'>"+rows+"</table>",
                "<properties pageSize='A4' orientation='portrait' margin='48'/>")
                .replace("<content>ÖN SON</content>", "<content/>"));
        var table=(DocumentElements.TableBlock)doc.blocks().get(0);
        var groups=TableLayout.pageGroups(TableLayout.calculate(table));
        assertTrue(groups.stream().anyMatch(g->g[0]==23&&g[1]==25));
        try(PDDocument pdf=PdfExporter.render(List.of(doc))) {
            assertTrue(pdf.getNumberOfPages()>1);
            var strip=new PDFTextStripper();
            String combined=strip.getText(pdf);
            assertTrue(combined.indexOf("R0")>=0);
            assertTrue(combined.indexOf("R37")>combined.indexOf("R0"));
            for(int i=0;i<38;i++)assertTrue(combined.contains("R"+i),"Missing table row "+i);
            for(int i=1;i<38;i++)
                assertTrue(combined.indexOf("R"+i)>combined.indexOf("R"+(i-1)),"Row order "+i);
            for(int page=1;page<=pdf.getNumberOfPages();page++) {
                strip.setStartPage(page);strip.setEndPage(page);
                var contents=strip.getText(pdf);
                if(contents.contains("R23"))assertTrue(contents.contains("R24"),"Rowspan split across PDF pages");
            }
        }
    }
    @Test void zippedSyntheticMixedUdfProducesVerifiedPdf(@TempDir Path folder) throws Exception {
        String contentXml=container(para(0,2)
                + "<table widths='90'><row height='28'><cell>ZIP-TABLO</cell></row></table>"
                + png()+para(3,3),"<properties pageSize='A4' margin='48'/>");
        Path udf=folder.resolve("mixed.udf");
        try(ZipOutputStream archive=new ZipOutputStream(Files.newOutputStream(udf))) {
            archive.putNextEntry(new ZipEntry("content.xml"));
            archive.write(contentXml.getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        UdfReader.DocumentSpec input=UdfReader.read(udf);
        assertEquals(4,input.blocks().size());
        Path output=folder.resolve("mixed.pdf");
        assertEquals(1,PdfExporter.save(List.of(input),output,false));
        try(PDDocument pdf=Loader.loadPDF(output.toFile())) {
            String extracted=new PDFTextStripper().getText(pdf);
            assertTrue(extracted.contains("ZIP-TABLO"),extracted);
            assertTrue(extracted.indexOf("SON")>extracted.indexOf("ZIP-TABLO"),extracted);
        }
        byte[] preserved=Files.readAllBytes(output);
        assertThrows(IllegalArgumentException.class,()->
                PdfExporter.save(List.of(input),output,false));
        assertArrayEquals(preserved,Files.readAllBytes(output));
    }

    @Test void unsupportedStructuresAndBadGeometryFailClosed() throws Exception {
        var noTableWidth="<table><row height='25'><cell>A</cell></row></table>";
        assertThrows(IllegalArgumentException.class,()->parse(container(noTableWidth,"")));
        var badSpan="<table widths='80,100'><row height='25'><cell colspan='3'>X</cell></row></table>";
        assertThrows(IllegalArgumentException.class,()->parse(container(badSpan,"")));
        var missingCell="<table widths='80,100'><row height='25'><cell>X</cell></row></table>";
        var partial=parse(container(missingCell,"")
                .replace("<content>ÖN SON</content>", "<content/>"));
        assertThrows(IllegalArgumentException.class,()->PdfExporter.render(List.of(partial)));
        assertThrows(IllegalArgumentException.class,()->parse(container(para(0,2),
            "<properties pageSize='A3'/>")));
        assertThrows(IllegalArgumentException.class,()->parse(container(para(0,2),
            "<properties pageSize='A4' custom='silent-data-loss'/>")));
        assertThrows(IllegalArgumentException.class,()->parse(container(
            "<image mime='image/jpeg' encoding='base64' width='20' height='20'>AAAA</image>","")));
        assertThrows(IllegalArgumentException.class,()->parse(container(
            "<image mime='image/png' encoding='base64' width='20' height='20'>!</image>","")));
        String nested="<table widths='100'><row height='30'><cell><paragraph/></cell></row></table>";
        assertThrows(IllegalArgumentException.class,()->parse(container(nested,"")));
    }
    @Test void unknownFormatVersionsCannotBeRenderedAsSyntheticOneZero() throws Exception {
        String valid = "<template format_id='1.0'><content>ABC</content>"
                + "<elements><paragraph><content startOffset='0' length='3'/>"
                + "</paragraph></elements></template>";
        assertEquals("1.0", parse(valid).formatId());
        for (String version : List.of("1.1", "2.0", "1.0 ", "unknown", "")) {
            String altered = valid.replace("format_id='1.0'", "format_id='" + version + "'");
            assertThrows(IllegalArgumentException.class, () -> parse(altered),
                    "Unverified schema version must fail closed: " + version);
        }
    }

    @Test void syntheticXmlMetadataAndMalformedBoldFailClosed() throws Exception {
        String plain = "<template format_id='1.0'><content>ABC</content>"
                + "<elements><paragraph><content startOffset='0' length='3'/>"
                + "</paragraph></elements></template>";
        for (String invalid : List.of(
                plain.replace("format_id='1.0'", "format_id='1.0' pageMode='unknown'"),
                plain.replace("<content>ABC</content>", "<content hidden='true'>ABC</content>"),
                plain.replace("<elements>", "<elements reverse='true'>"),
                plain.replace("<paragraph>", "<paragraph pageBreak='true'>"),
                plain.replace("length='3'", "length='3' letterSpacing='4'"),
                plain.replace("<content>ABC</content>", "<content><span>ABC</span></content>"),
                plain.replace("length='3'", "length='3' bold='maybe'"),
                plain.replace("<elements>", "<styles><style name='strong' bold='maybe'/></styles><elements>"),
                plain.replace("<elements>", "<styles><style name='strong' unsupported='1'/></styles><elements>")
        )) {
            assertThrows(IllegalArgumentException.class, () -> parse(invalid), invalid);
        }
        String valid = plain.replace("<elements>",
                "<styles><style name='strong' bold='true'/></styles><elements>")
                .replace("length='3'", "length='3' resolver='strong'");
        assertTrue(parse(valid).paragraphs().get(0).runs().get(0).style().bold());
    }


    @Test void utf16RangesCannotSkipDuplicateOrReorderVisibleContent() throws Exception {
        String prefix = "<template format_id='1.0'><content>";
        String middle = "</content><elements><paragraph>";
        String suffix = "</paragraph></elements></template>";
        String emoji = prefix + "A😀B" + middle
                + para(0,1).replace("<paragraph>", "").replace("</paragraph>", "")
                + para(1,2).replace("<paragraph>", "").replace("</paragraph>", "")
                + para(3,1).replace("<paragraph>", "").replace("</paragraph>", "") + suffix;
        var parsed = parse(emoji);
        assertEquals("A😀B", parsed.paragraphs().get(0).runs().stream()
                .map(UdfReader.Run::text).reduce("", String::concat));

        // Whitespace between disjoint runs may be layout-only in this limited contract.
        String spaced = prefix + "A B" + middle
                + "<content startOffset='0' length='1'/>"
                + "<content startOffset='2' length='1'/>" + suffix;
        assertEquals(2, parse(spaced).paragraphs().get(0).runs().size());

        for (String ranges : List.of(
                "<content startOffset='0' length='1'/><content startOffset='2' length='1'/>",
                "<content startOffset='0' length='2'/><content startOffset='1' length='2'/>",
                "<content startOffset='2' length='1'/><content startOffset='0' length='2'/>",
                "<content startOffset='0' length='2'/>",
                "<content startOffset='1' length='2'/>"
        )) {
            assertThrows(IllegalArgumentException.class,
                    () -> parse(prefix + "ABC" + middle + ranges + suffix),
                    "Unrendered, repeated or reordered visible text must fail closed: " + ranges);
        }
        assertThrows(IllegalArgumentException.class, () -> parse(
                prefix + "ABC" + "</content><elements>"
                        + "<table widths='100'><row height='25'><cell>X</cell></row></table>"
                        + "</elements></template>"),
                "Raw text cannot disappear when the document only contains a table");
    }

    @Test void structuralTextCannotDisappearFromSyntheticPdf() throws Exception {
        String valid = container(
            "\n<table widths='100'>\n<row height='25'>\n<cell>A</cell>\n</row>\n</table>\n"
                + para(0,6), "");
        assertEquals(2, parse(valid).blocks().size(),
            "Indentation whitespace between structural nodes remains supported");

        for (String invalid : List.of(
                container("<table widths='100'>HIDDEN<row height='25'><cell>A</cell></row></table>"
                    + para(0,2), ""),
                container("<table widths='100'><row height='25'>HIDDEN<cell>A</cell></row></table>"
                    + para(0,2), ""),
                container("<paragraph>HIDDEN<content startOffset='0' length='2'/></paragraph>", ""),
                container(para(0,2), "").replace("<elements>", "<elements>HIDDEN"),
                container(para(0,2), "").replace("<template format_id='1.0'>",
                    "<template format_id='1.0'>HIDDEN"),
                container(para(0,2), "").replace("<elements>",
                    "<styles>HIDDEN<style name='s' size='11'/></styles><elements>")
        )) {
            assertThrows(IllegalArgumentException.class, () -> parse(invalid),
                "Structural text must not silently disappear: " + invalid);
        }
    }

    @Test void maximumSyntheticTableRowsKeepEveryRowspanGroupInOrder() {
        // A 500-row alternating rowspan fixture exercises the largest supported
        // table without relying on a flaky wall-clock performance assertion.
        List<DocumentElements.Row> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            if (i % 2 == 0)
                rows.add(new DocumentElements.Row(List.of(
                        new DocumentElements.Cell("SPAN" + i, 1, 2),
                        new DocumentElements.Cell("R" + i, 1, 1)), 18f));
            else
                rows.add(new DocumentElements.Row(List.of(
                        new DocumentElements.Cell("R" + i, 1, 1)), 18f));
        }
        var table = new DocumentElements.TableBlock(List.of(90f, 90f), rows);
        var geometry = TableLayout.calculate(table);
        assertEquals(750, geometry.cells().size());
        var groups = TableLayout.pageGroups(geometry);
        assertEquals(250, groups.size());
        for (int i = 0; i < groups.size(); i++)
            assertArrayEquals(new int[]{2 * i, 2 * i + 2}, groups.get(i));
    }
}
