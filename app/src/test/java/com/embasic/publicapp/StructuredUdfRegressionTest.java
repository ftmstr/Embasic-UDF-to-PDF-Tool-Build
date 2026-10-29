package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
                "<properties pageSize='A4' orientation='portrait' margin='48'/>"));
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
    @Test void unsupportedStructuresAndBadGeometryFailClosed() throws Exception {
        var noTableWidth="<table><row height='25'><cell>A</cell></row></table>";
        assertThrows(IllegalArgumentException.class,()->parse(container(noTableWidth,"")));
        var badSpan="<table widths='80,100'><row height='25'><cell colspan='3'>X</cell></row></table>";
        assertThrows(IllegalArgumentException.class,()->parse(container(badSpan,"")));
        var missingCell="<table widths='80,100'><row height='25'><cell>X</cell></row></table>";
        var partial=parse(container(missingCell,""));
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
}
