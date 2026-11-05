package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Original synthetic fixtures; expected geometry comes from the declared
 * points/colors, not from a PDF produced by the same export implementation.
 * Does not establish native UYAP/PDF pixel equivalence.
 */
final class PdfVisualGeometryRegressionTest {
    private static final UdfReader.Style RED =
            new UdfReader.Style(14f,false,new Color(230,20,20));
    private static final UdfReader.Style BLUE =
            new UdfReader.Style(18f,true,new Color(10,35,230));

    private static UdfReader.DocumentSpec paragraph(UdfReader.Run... runs) {
        return new UdfReader.DocumentSpec("synthetic-colors.udf","1.0",
                List.of(new UdfReader.Paragraph(List.of(runs))));
    }

    private static int[] inkPixels(BufferedImage image) {
        int reds=0,blues=0;
        int bottom=Math.min(180,image.getHeight());
        for(int y=0;y<bottom;y++) for(int x=0;x<image.getWidth();x++) {
            int rgb=image.getRGB(x,y);
            int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
            if(r>110 && r>g+70 && r>b+70)reds++;
            if(b>110 && b>g+70 && b>r+70)blues++;
        }
        return new int[]{reds,blues};
    }

    @Test void rendersOpaqueStyledRunsWithMeasurableInkAndEmbeddedFonts() throws Exception {
        var document=paragraph(new UdfReader.Run("KIRMIZI ",RED),
                new UdfReader.Run("BULUT",BLUE));
        try(PDDocument pdf=PdfExporter.render(List.of(document))) {
            assertEquals(1,pdf.getNumberOfPages());
            BufferedImage raster=new PDFRenderer(pdf).renderImageWithDPI(0,72);
            int[] pixels=inkPixels(raster);
            assertTrue(pixels[0]>10,"Opaque red text not visible in PDF raster: "+pixels[0]);
            assertTrue(pixels[1]>10,"Opaque blue text not visible in PDF raster: "+pixels[1]);
            class Positions extends PDFTextStripper {
                final List<TextPosition> positions=new ArrayList<>();
                Positions() throws IOException { super(); }
                @Override protected void processTextPosition(TextPosition value) {
                    positions.add(value);
                    super.processTextPosition(value);
                }
            }
            var probe=new Positions();
            assertTrue(probe.getText(pdf).contains("KIRMIZI"));
            assertTrue(probe.getText(pdf).contains("BULUT"));
            TextPosition firstRed=probe.positions.stream().filter(p->"K".equals(p.getUnicode()))
                    .findFirst().orElseThrow();
            TextPosition firstBlue=probe.positions.stream().filter(p->"B".equals(p.getUnicode()))
                    .findFirst().orElseThrow();
            assertEquals(14f,firstRed.getFontSizeInPt(),0.2f);
            assertEquals(18f,firstBlue.getFontSizeInPt(),0.2f);
            assertTrue(firstRed.getFont().isEmbedded(),"Regular font must be embedded");
            assertTrue(firstBlue.getFont().isEmbedded(),"Bold font must be embedded");
        }
    }

    @Test void rejectsTranslucentDirectStyleBeforePublishing(@TempDir Path folder)
            throws Exception {
        for(int alpha:new int[]{0,35,160,254}) {
            var bad=new UdfReader.Style(14f,false,new Color(230,20,20,alpha));
            var document=paragraph(new UdfReader.Run("YARISAYDAM",bad));
            assertThrows(IllegalArgumentException.class,
                    ()->{try(PDDocument ignored=PdfExporter.render(List.of(document))){}});
            Path output=folder.resolve("alpha-"+alpha+".pdf");
            assertThrows(IllegalArgumentException.class,
                    ()->PdfExporter.save(List.of(document),output,false));
            assertFalse(Files.exists(output),"Translucent text must not publish an opaque PDF");
        }
    }

    private static boolean darkNear(BufferedImage image,int x,int y) {
        for(int yy=Math.max(0,y-1);yy<=Math.min(image.getHeight()-1,y+1);yy++)
            for(int xx=Math.max(0,x-1);xx<=Math.min(image.getWidth()-1,x+1);xx++) {
                int rgb=image.getRGB(xx,yy);
                if(((rgb>>>16)&255)<100 && ((rgb>>>8)&255)<100 && (rgb&255)<100)
                    return true;
            }
        return false;
    }

    @Test void mergedTableDrawsNoPhantomInteriorRowBorder() throws Exception {
        var table=new DocumentElements.TableBlock(List.of(100f,100f), List.of(
                new DocumentElements.Row(List.of(
                        new DocumentElements.Cell("MERGED",1,2),
                        new DocumentElements.Cell("TOP",1,1)),32f),
                new DocumentElements.Row(List.of(
                        new DocumentElements.Cell("BOTTOM",1,1)),32f)));
        var geometry=TableLayout.calculate(table);
        assertEquals(3,geometry.cells().size());
        assertEquals(64f,geometry.cells().get(0).height(),0.001f);
        assertEquals(100f,geometry.cells().get(1).x(),0.001f);
        assertEquals(32f,geometry.cells().get(2).top(),0.001f);
        var document=new UdfReader.DocumentSpec("synthetic-grid.udf","1.0",
                List.of(),List.of(table),DocumentElements.PageSpec.a4());
        try(PDDocument pdf=PdfExporter.render(List.of(document))) {
            assertEquals(1,pdf.getNumberOfPages());
            BufferedImage raster=new PDFRenderer(pdf).renderImageWithDPI(0,72);
            int seamY=48+32;
            assertTrue(darkNear(raster,48+100+50,seamY),
                    "Second-column row boundary must be visible");
            assertFalse(darkNear(raster,48+50,seamY),
                    "Rowspan cell must not have an internal horizontal border");
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("MERGED") && text.contains("TOP")
                    && text.contains("BOTTOM"),text);
        }
    }

    @Test void syntheticUdfPageSettingsRetainExactDeclaredMediaBoxes() throws Exception {
        record Paper(String format,String orientation,float width,float height){}
        for(var paper:List.of(
                new Paper("A4","portrait",595.28f,841.89f),
                new Paper("A5","landscape",595.28f,419.53f),
                new Paper("LEGAL","landscape",1008f,612f),
                new Paper("LETTER","portrait",612f,792f))) {
            String xml="<template format_id='1.0'><content>OK</content>"
                    +"<elements><paragraph><content startOffset='0' length='2'/>"
                    +"</paragraph></elements><properties pageSize='"+paper.format
                    +"' orientation='"+paper.orientation+"' margin='48'/></template>";
            var document=UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8),
                    "synthetic-"+paper.format+".udf");
            try(PDDocument pdf=PdfExporter.render(List.of(document))) {
                assertEquals(1,pdf.getNumberOfPages());
                var rectangle=pdf.getPage(0).getMediaBox();
                assertEquals(paper.width,rectangle.getWidth(),0.03f);
                assertEquals(paper.height,rectangle.getHeight(),0.03f);
                assertTrue(new PDFTextStripper().getText(pdf).contains("OK"));
            }
        }
    }
}
