package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Regression: a trailing paragraph must not create an empty PDF page. */
final class PdfTrailingPageTest {
    private static UdfReader.DocumentSpec paragraphs(int count) {
        List<UdfReader.Paragraph> paragraphs = new ArrayList<>();
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        for (int i = 0; i < count; i++)
            paragraphs.add(new UdfReader.Paragraph(List.of(
                    new UdfReader.Run("LINE" + i, style))));
        return new UdfReader.DocumentSpec("synthetic.udf", "1.0", paragraphs);
    }

    @Test void lastParagraphAtPageBoundaryDoesNotCreateBlankPage() throws Exception {
        // A4: top=793.89; 33 prior paragraphs * (17 line + 5 gap)
        // leave y=67.89, so LINE33 fits but its trailing gap would overflow.
        try (PDDocument pdf = PdfExporter.render(List.of(paragraphs(34)))) {
            assertEquals(1, pdf.getNumberOfPages(),
                    "The trailing paragraph gap must not emit a blank page");
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("LINE0"), text);
            assertTrue(text.contains("LINE33"), text);
        }
    }

    @Test void followingParagraphStillFlowsToNextPage() throws Exception {
        try (PDDocument pdf = PdfExporter.render(List.of(paragraphs(35)))) {
            assertEquals(2, pdf.getNumberOfPages());
            var stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertTrue(stripper.getText(pdf).contains("LINE34"));
        }
    }
}
