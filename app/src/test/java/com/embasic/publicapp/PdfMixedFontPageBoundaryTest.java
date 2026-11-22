package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic A5 boundary fixtures; no claim of authentic UYAP layout parity. */
final class PdfMixedFontPageBoundaryTest {
    private static final UdfReader.Style SMALL =
            new UdfReader.Style(6f,false,Color.BLACK);
    private static final UdfReader.Style NORMAL =
            new UdfReader.Style(11f,false,Color.BLACK);
    private static final UdfReader.Style BIG =
            new UdfReader.Style(48f,true,Color.BLUE);
    private static final UdfReader.Style MEDIUM =
            new UdfReader.Style(36f,true,Color.RED);

    private static UdfReader.DocumentSpec a5(UdfReader.Run... runs) {
        var paragraph=new UdfReader.Paragraph(List.of(runs));
        return new UdfReader.DocumentSpec("synthetic-a5.udf","1.0",
                List.of(paragraph),List.of(new DocumentElements.ParagraphBlock(paragraph)),
                new DocumentElements.PageSpec(419.53f,595.28f,48f));
    }

    @Test void rejectsSplittingOnlyLargeSuffixOfAlreadyPaintedLine(@TempDir Path folder)
            throws Exception {
        // A5 top=547.28pt; 29 explicit line breaks leave baseline=54.28pt.
        // The 6pt word fits; a following 48pt word does not fit this line.
        // Moving only the latter to page two would silently change layout.
        var unsafe=a5(new UdfReader.Run("\n".repeat(29)+"a ",SMALL),
                new UdfReader.Run("Z",BIG));
        var problem=assertThrows(IllegalArgumentException.class,
                ()->{try(PDDocument unused=PdfExporter.render(List.of(unsafe))) {}});
        assertTrue(problem.getMessage().contains("Satır içi"),problem.getMessage());

        Path output=folder.resolve("preserved.pdf");
        var original=a5(new UdfReader.Run("GUVENLI",NORMAL));
        assertEquals(1,PdfExporter.save(List.of(original),output,false));
        byte[] before=Files.readAllBytes(output);
        assertThrows(IllegalArgumentException.class,
                ()->PdfExporter.save(List.of(original,unsafe),output,true));
        assertArrayEquals(before,Files.readAllBytes(output));
        try(var files=Files.list(folder)) {
            assertEquals(List.of("preserved.pdf"),
                    files.map(p->p.getFileName().toString()).sorted().toList(),
                    "Failed full export must not publish a transient or partial PDF");
        }
    }

    @Test void allowsLargeWordAtBeginningOfNewPageAndKeepsPreviewFirstPage()
            throws Exception {
        // The same large word may legitimately start the next page when no
        // visible glyph has been placed earlier on its current line.
        var valid=a5(new UdfReader.Run("\n".repeat(29),SMALL),
                new UdfReader.Run("Z",BIG));
        try(PDDocument preview=PdfExporter.renderPreview(valid);
            PDDocument pdf=PdfExporter.render(List.of(valid))) {
            assertEquals(1,preview.getNumberOfPages());
            assertEquals(2,pdf.getNumberOfPages());
            var text=new PDFTextStripper();
            text.setStartPage(2);
            text.setEndPage(2);
            assertTrue(text.getText(pdf).contains("Z"));
        }
    }

    @Test void mixedFontSizesShareBaselineAndNextLineUsesLargestHeight()
            throws Exception {
        var valid=a5(new UdfReader.Run("A ",NORMAL),
                new UdfReader.Run("B\n",MEDIUM),
                new UdfReader.Run("C",NORMAL));
        try(PDDocument pdf=PdfExporter.render(List.of(valid))) {
            assertEquals(1,pdf.getNumberOfPages());
            class Capture extends PDFTextStripper {
                final List<TextPosition> positions=new ArrayList<>();
                Capture() throws IOException { super(); }
                @Override protected void processTextPosition(TextPosition p) {
                    positions.add(p);
                    super.processTextPosition(p);
                }
            }
            var probe=new Capture();
            assertTrue(probe.getText(pdf).contains("A"));
            TextPosition a=probe.positions.stream().filter(p->"A".equals(p.getUnicode()))
                    .findFirst().orElseThrow();
            TextPosition b=probe.positions.stream().filter(p->"B".equals(p.getUnicode()))
                    .findFirst().orElseThrow();
            TextPosition c=probe.positions.stream().filter(p->"C".equals(p.getUnicode()))
                    .findFirst().orElseThrow();
            assertEquals(a.getYDirAdj(),b.getYDirAdj(),1.0f,
                    "Mixed font glyphs on one line should have same baseline");
            assertEquals(36f*1.4f,c.getYDirAdj()-b.getYDirAdj(),2.0f,
                    "Following line must clear the largest styled run");
            assertEquals(a.getXDirAdj(),c.getXDirAdj(),1.0f,
                    "Explicit newline should return to left margin");
        }
    }
}
