package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The synthetic subset may contain a word split into multiple styled UDF
 * runs. A font-style seam is not permission to wrap one word onto two lines.
 */
final class PdfStyledRunWrapTest {
    private static final UdfReader.Style LARGE=new UdfReader.Style(48, false, Color.BLACK);
    private static final UdfReader.Style NORMAL=new UdfReader.Style(11, false, Color.BLACK);
    private static final UdfReader.Style BOLD=new UdfReader.Style(11, true, Color.BLUE);

    private static UdfReader.DocumentSpec doc(UdfReader.Run... runs) {
        return new UdfReader.DocumentSpec("synthetic-word.udf","1.0",
                List.of(new UdfReader.Paragraph(List.of(runs))));
    }
    @Test void rejectsUnmarkedWordSplitAcrossRunsAtRightMargin() throws Exception {
        // Each fragment fits on A4 independently, but the uninterrupted word
        // does not. Old implementation wrapped at the style seam without error.
        var unsafe=doc(new UdfReader.Run("AAAAAAAAA",LARGE),
                new UdfReader.Run("BBBBBBBBB",LARGE));
        var problem=assertThrows(IllegalArgumentException.class,()->{
            try(PDDocument ignored=PdfExporter.render(List.of(unsafe))) {}
        });
        assertTrue(problem.getMessage().contains("Biçimlendirilmiş kelime"),
                problem.getMessage());
    }
    @Test void allowsARealWordSeparatorToWrapAtMargin() throws Exception {
        var safe=doc(new UdfReader.Run("AAAAAAAAA ",LARGE),
                new UdfReader.Run("BBBBBBBBB",LARGE));
        try(PDDocument pdf=PdfExporter.render(List.of(safe))) {
            assertEquals(1,pdf.getNumberOfPages());
            String extracted=new PDFTextStripper().getText(pdf);
            assertTrue(extracted.contains("AAAAAAAAA"),extracted);
            assertTrue(extracted.contains("BBBBBBBBB"),extracted);
        }
    }
    @Test void keepsShortIntraWordStyleChangesWithoutInventingSpaces() throws Exception {
        var safe=doc(new UdfReader.Run("Mer",NORMAL),new UdfReader.Run("haba",BOLD));
        try(PDDocument pdf=PdfExporter.render(List.of(safe))) {
            assertEquals(1,pdf.getNumberOfPages());
            String extracted=new PDFTextStripper().getText(pdf).replaceAll("\\s+","");
            assertTrue(extracted.contains("Merhaba"),extracted);
        }
    }
    @Test void failedMergeDoesNotReplaceOrStageValidatedOutput(@TempDir Path folder)
            throws Exception {
        Path target=folder.resolve("result.pdf");
        var valid=doc(new UdfReader.Run("ÖNCE",NORMAL));
        assertEquals(1,PdfExporter.save(List.of(valid),target,false));
        byte[] original=Files.readAllBytes(target);
        var unsafe=doc(new UdfReader.Run("AAAAAAAAA",LARGE),
                new UdfReader.Run("BBBBBBBBB",LARGE));
        assertThrows(IllegalArgumentException.class,
                ()->PdfExporter.save(List.of(valid,unsafe),target,true));
        assertArrayEquals(original,Files.readAllBytes(target),
                "Rejected merge must preserve the validated target PDF");
        try(var paths=Files.list(folder)) {
            assertEquals(List.of("result.pdf"),
                    paths.map(p->p.getFileName().toString()).sorted().toList(),
                    "No transient PDF or backup must remain after rejection");
        }
    }
}
