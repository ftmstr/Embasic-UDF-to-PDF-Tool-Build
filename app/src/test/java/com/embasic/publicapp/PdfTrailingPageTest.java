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

    @Test void whitespaceOnlyUdfCannotSilentlyProduceBlankPdf(@TempDir Path folder) throws Exception {
        // Terminal whitespace is trimmed to avoid spurious pages, but an entire
        // whitespace-only UDF must not be reported as a successful conversion.
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        var blank = new UdfReader.DocumentSpec("blank.udf", "1.0", List.of(
                new UdfReader.Paragraph(List.of(
                        new UdfReader.Run("\r\n \t", style),
                        new UdfReader.Run("", style)))));
        var visible = paragraphs(1);
        for (List<UdfReader.DocumentSpec> documents : List.of(
                List.of(blank), List.of(visible, blank), List.of(blank, visible))) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(documents)) {} });
            assertTrue(error.getMessage().contains("blank.udf"), error.getMessage());
            Path target = folder.resolve("merged.pdf");
            assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.save(documents, target, false));
            assertFalse(Files.exists(target), "A rejected merge must not publish a PDF");
        }
        assertThrows(IllegalArgumentException.class,
                () -> { try (PDDocument ignored = PdfExporter.renderPreview(blank)) {} });
        // An empty table cell still draws a visible grid and is not a blank UDF.
        var grid = new DocumentElements.TableBlock(List.of(120f),
                List.of(new DocumentElements.Row(
                        List.of(new DocumentElements.Cell("", 1, 1)), 24f)));
        var tableDoc = new UdfReader.DocumentSpec("grid.udf", "1.0", List.of(),
                List.of(grid), DocumentElements.PageSpec.a4());
        // A border-only table needs no font glyphs. An unavailable system
        // font must not delay or block rendering its visible cell geometry.
        String fontProperty = "embasic.font.regular";
        String previousFont = System.getProperty(fontProperty);
        System.setProperty(fontProperty, "__embasic_missing_grid_font_test__.ttf");
        try {
            try (PDDocument pdf = PdfExporter.render(List.of(tableDoc))) {
                assertEquals(1, pdf.getNumberOfPages());
            }
            // Space-padded empty cells are visually border-only too. They
            // must not require a Unicode font or produce phantom PDF text.
            var paddedGrid = new DocumentElements.TableBlock(List.of(120f),
                    List.of(new DocumentElements.Row(
                            List.of(new DocumentElements.Cell("   ", 1, 1)), 24f)));
            var paddedDoc = new UdfReader.DocumentSpec("padded-grid.udf", "1.0",
                    List.of(), List.of(paddedGrid), DocumentElements.PageSpec.a4());
            try (PDDocument pdf = PdfExporter.render(List.of(paddedDoc))) {
                assertEquals(1, pdf.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(pdf).isBlank(),
                        "Space-only cells must draw borders without emitting text");
            }
            // CR/LF/tab-only editor runs do not measure glyphs. They must not
            // force a system font scan when the visible content is a grid.
            var breaks = new UdfReader.Paragraph(List.of(
                    new UdfReader.Run("\r\n\t", style)));
            var gridWithBreaks = new UdfReader.DocumentSpec("grid-breaks.udf", "1.0",
                    List.of(breaks), List.of(new DocumentElements.ParagraphBlock(breaks), grid),
                    DocumentElements.PageSpec.a4());
            try (PDDocument pdf = PdfExporter.render(List.of(gridWithBreaks))) {
                assertEquals(1, pdf.getNumberOfPages());
            }
            // The optimization must not silently drop actual cell text.
            var textGrid = new DocumentElements.TableBlock(List.of(120f),
                    List.of(new DocumentElements.Row(
                            List.of(new DocumentElements.Cell("TEXT", 1, 1)), 24f)));
            var textDoc = new UdfReader.DocumentSpec("text-grid.udf", "1.0",
                    List.of(), List.of(textGrid), DocumentElements.PageSpec.a4());
            assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(List.of(textDoc))) {} },
                    "A table with text must still require a usable Unicode font");
            var paddedTextGrid = new DocumentElements.TableBlock(List.of(120f),
                    List.of(new DocumentElements.Row(
                            List.of(new DocumentElements.Cell(" TEXT ", 1, 1)), 24f)));
            var paddedTextDoc = new UdfReader.DocumentSpec("padded-text.udf", "1.0",
                    List.of(), List.of(paddedTextGrid), DocumentElements.PageSpec.a4());
            assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(List.of(paddedTextDoc))) {} },
                    "A cell containing real text must still fail closed when its font is absent");
        } finally {
            if (previousFont == null) System.clearProperty(fontProperty);
            else System.setProperty(fontProperty, previousFont);
        }
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

    @Test void terminalNewlinesAcrossRunsDoNotCreateBlankPages() throws Exception {
        // 33 preceding paragraphs leave just enough room for LINE33. Two
        // terminal newlines previously allocated an otherwise empty page two.
        // Both a single run and a whitespace-only trailing run must behave.
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        for (boolean splitRuns : new boolean[]{false, true}) {
            List<UdfReader.Paragraph> content = new ArrayList<>(paragraphs(33).paragraphs());
            List<UdfReader.Run> tail = splitRuns
                    ? List.of(new UdfReader.Run("LINE33", style),
                              new UdfReader.Run("\n\n \t", style))
                    : List.of(new UdfReader.Run("LINE33\n\n \t", style));
            content.add(new UdfReader.Paragraph(tail));
            var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0", content);
            try (PDDocument pdf = PdfExporter.render(List.of(document))) {
                assertEquals(1, pdf.getNumberOfPages(),
                        "Terminal line breaks must not create an empty page");
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("LINE0"), text);
                assertTrue(text.contains("LINE33"), text);
            }
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

    @Test void thumbnailLaysOutOnlyFirstPageWhileFullExportKeepsAllPages() throws Exception {
        // A thumbnail must not render the other pages of a long document.
        // The export path must still produce all pages in the original order.
        var document = paragraphs(100);
        try (PDDocument preview = PdfExporter.renderPreview(document);
             PDDocument exported = PdfExporter.render(List.of(document))) {
            assertEquals(1, preview.getNumberOfPages());
            assertTrue(exported.getNumberOfPages() > 1);
            var firstPageText = new PDFTextStripper();
            firstPageText.setStartPage(1);
            firstPageText.setEndPage(1);
            String expected = firstPageText.getText(exported);
            String actual = new PDFTextStripper().getText(preview);
            assertEquals(expected, actual, "Preview must preserve first-page text and order");
            assertTrue(actual.contains("LINE0"));
            assertFalse(actual.contains("LINE99"), "Later pages must not enter the thumbnail");
            var allPages = new PDFTextStripper().getText(exported);
            assertTrue(allPages.contains("LINE99"), "Full export must retain the final paragraph");
        }
    }

    @Test void largeFontMovedToNewPagePreservesNextParagraphSpacing() throws Exception {
        // 33 regular paragraphs leave the first page almost full. A 48pt run
        // moves to page two; its line advance must remain >= 48*1.4pt even
        // though newPage() initializes the default 17pt line height.
        List<DocumentElements.Block> blocks = new ArrayList<>(paragraphs(33).blocks());
        blocks.add(new DocumentElements.ParagraphBlock(new UdfReader.Paragraph(List.of(
                new UdfReader.Run("X", new UdfReader.Style(48f, false, Color.BLACK))))));
        blocks.add(new DocumentElements.ParagraphBlock(new UdfReader.Paragraph(List.of(
                new UdfReader.Run("Y", new UdfReader.Style(11f, false, Color.BLACK))))));
        var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(),
                blocks, DocumentElements.PageSpec.a4());
        try (PDDocument pdf = PdfExporter.render(List.of(document))) {
            assertEquals(2, pdf.getNumberOfPages());
            class Baselines extends PDFTextStripper {
                Float large, following;
                Baselines() throws IOException { super(); }
                @Override protected void processTextPosition(TextPosition position) {
                    if ("X".equals(position.getUnicode())) large = position.getYDirAdj();
                    if ("Y".equals(position.getUnicode())) following = position.getYDirAdj();
                    super.processTextPosition(position);
                }
            }
            Baselines positions = new Baselines();
            positions.setStartPage(2);
            positions.setEndPage(2);
            positions.getText(pdf);
            assertNotNull(positions.large, "Large run must appear on page two");
            assertNotNull(positions.following, "Following paragraph must appear on page two");
            assertTrue(positions.following - positions.large > 55f,
                    "Following baseline must clear the 48pt line, not use a 17pt advance");
        }
    }

    @Test void regularOnlyPreviewDoesNotLoadUnusedBoldFont() throws Exception {
        // A missing bold font must not block a document that never requests bold.
        // A genuinely bold run must still fail closed rather than lose styling.
        String property = "embasic.font.bold";
        String previous = System.getProperty(property);
        System.setProperty(property, "__embasic_missing_bold_font_test__.ttf");
        try {
            try (PDDocument pdf = PdfExporter.render(List.of(paragraphs(1)))) {
                assertEquals(1, pdf.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(pdf).contains("LINE0"));
            }
            var regularStyle = new UdfReader.Style(11f, false, Color.BLACK);
            var boldStyle = new UdfReader.Style(11f, true, Color.BLACK);
            // Empty and terminal whitespace-only bold runs are discarded by
            // layout; they must not make an otherwise regular PDF fail.
            var visible = new UdfReader.Paragraph(List.of(
                    new UdfReader.Run("VISIBLE", regularStyle),
                    new UdfReader.Run("", boldStyle),
                    new UdfReader.Run(" \r\n\t", boldStyle)));
            var trailing = new UdfReader.Paragraph(List.of(
                    new UdfReader.Run("\r\n\t", boldStyle)));
            var noBoldGlyphs = new UdfReader.DocumentSpec("unused-bold.udf", "1.0",
                    List.of(visible, trailing));
            try (PDDocument pdf = PdfExporter.render(List.of(noBoldGlyphs))) {
                assertEquals(1, pdf.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(pdf).contains("VISIBLE"));
            }
            // A bold glyph on a later page must not make the first-page
            // thumbnail depend on that missing font. Full export must still
            // reject the same document instead of silently losing the glyph.
            List<DocumentElements.Block> lateBlocks = new ArrayList<>(paragraphs(100).blocks());
            lateBlocks.set(99, new DocumentElements.ParagraphBlock(
                    new UdfReader.Paragraph(List.of(
                            new UdfReader.Run("LATE_BOLD", boldStyle)))));
            var lateBold = new UdfReader.DocumentSpec("late-bold.udf", "1.0",
                    List.of(), lateBlocks, DocumentElements.PageSpec.a4());
            try (PDDocument preview = PdfExporter.renderPreview(lateBold)) {
                assertEquals(1, preview.getNumberOfPages());
                String text = new PDFTextStripper().getText(preview);
                assertTrue(text.contains("LINE0"));
                assertFalse(text.contains("LATE_BOLD"));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(List.of(lateBold))) {} },
                    "Full export must still require the bold font on later pages");
            // Interior bold spaces still require their own font for width.
            var measuredBoldSpace = new UdfReader.DocumentSpec("bold-space.udf", "1.0",
                    List.of(new UdfReader.Paragraph(List.of(
                            new UdfReader.Run("A", regularStyle),
                            new UdfReader.Run(" ", boldStyle),
                            new UdfReader.Run("B", regularStyle)))));
            assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.render(List.of(measuredBoldSpace)),
                    "Measured bold whitespace must never silently use the regular font");
            var bold = new UdfReader.DocumentSpec("bold.udf", "1.0", List.of(
                    new UdfReader.Paragraph(List.of(
                            new UdfReader.Run("BOLD", new UdfReader.Style(11f, true, Color.BLACK))))));
            assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.render(List.of(bold)),
                    "Bold text must not silently fall back to a regular font");
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }

        // A bold-only paragraph must not probe an unused regular font.
        // Mixed-style documents must still fail closed if regular is missing.
        String regularProperty = "embasic.font.regular";
        String priorRegular = System.getProperty(regularProperty);
        System.setProperty(regularProperty, "__embasic_missing_regular_font_test__.ttf");
        try {
            var boldStyle = new UdfReader.Style(11f, true, Color.BLACK);
            var plainStyle = new UdfReader.Style(11f, false, Color.BLACK);
            var boldOnly = new UdfReader.DocumentSpec("bold-only.udf", "1.0",
                    List.of(new UdfReader.Paragraph(List.of(
                            new UdfReader.Run("BOLDONLY", boldStyle)))));
            try (PDDocument pdf = PdfExporter.render(List.of(boldOnly))) {
                assertEquals(1, pdf.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(pdf).contains("BOLDONLY"));
            }
            var mixed = new UdfReader.DocumentSpec("mixed.udf", "1.0",
                    List.of(new UdfReader.Paragraph(List.of(
                            new UdfReader.Run("BOLD", boldStyle),
                            new UdfReader.Run("REGULAR", plainStyle)))));
            assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(List.of(mixed))) {} },
                    "A mixed-style PDF must not silently omit its regular font");
        } finally {
            if (priorRegular == null) System.clearProperty(regularProperty);
            else System.setProperty(regularProperty, priorRegular);
        }
    }


    @Test void standaloneCrAndSplitRunCrLfPreservePdfLinePositions() throws Exception {
        // A lone CR is a line break; CRLF must advance only once, even when
        // its two code points belong to differently styled runs.
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        var paragraphs = List.of(
                new UdfReader.Paragraph(List.of(new UdfReader.Run("A\rB", style))),
                new UdfReader.Paragraph(List.of(
                        new UdfReader.Run("C\r", style),
                        new UdfReader.Run("\nD", style))),
                new UdfReader.Paragraph(List.of(new UdfReader.Run("E\r\nF", style))));
        var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0", paragraphs);
        try (PDDocument pdf = PdfExporter.render(List.of(document))) {
            assertEquals(1, pdf.getNumberOfPages());
            var baselines = new java.util.HashMap<String, Float>();
            var extractor = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition glyph) {
                    baselines.put(glyph.getUnicode(), glyph.getYDirAdj());
                    super.processTextPosition(glyph);
                }
            };
            String extracted = extractor.getText(pdf);
            for (String letter : List.of("A", "B", "C", "D", "E", "F"))
                assertTrue(baselines.containsKey(letter), "Missing PDF glyph " + letter + ": " + extracted);
            assertEquals(6, baselines.size(), "Every synthetic glyph must remain visible");
            assertEquals(17f, baselines.get("B") - baselines.get("A"), 1f,
                    "Standalone CR must move the second glyph to the next line");
            assertEquals(17f, baselines.get("D") - baselines.get("C"), 1f,
                    "Split-run CRLF must advance exactly one line");
            assertEquals(17f, baselines.get("F") - baselines.get("E"), 1f,
                    "Same-run CRLF must continue to advance exactly one line");
        }
    }

    @Test void unicodeWhitespaceCannotSilentlyCollapseToAsciiSpace() {
        // The renderer advances one ordinary-space width for Character.isWhitespace.
        // Wide Unicode spaces would otherwise be silently narrowed in paragraphs;
        // reject them before trimming, previewing, or writing any PDF output.
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        for (int cp : new int[]{0x1680, 0x2002, 0x2003, 0x3000}) {
            String wideSpace = new String(Character.toChars(cp));
            String codePoint = "U+" + Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT);
            for (String value : List.of("A" + wideSpace + "B", "A" + wideSpace)) {
                var paragraph = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                        List.of(new UdfReader.Paragraph(List.of(new UdfReader.Run(value, style)))));
                var error = assertThrows(IllegalArgumentException.class,
                        () -> { try (PDDocument ignored = PdfExporter.render(List.of(paragraph))) {} });
                assertTrue(error.getMessage().contains(codePoint), error.getMessage());
            }
            var table = new DocumentElements.TableBlock(
                    List.of(150f), List.of(new DocumentElements.Row(
                            List.of(new DocumentElements.Cell("A" + wideSpace + "B", 1, 1)), 30f)));
            var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                    List.of(), List.of(table), DocumentElements.PageSpec.a4());
            var error = assertThrows(IllegalArgumentException.class,
                    () -> { try (PDDocument ignored = PdfExporter.render(List.of(document))) {} });
            assertTrue(error.getMessage().contains(codePoint), error.getMessage());
        }
    }

    @Test void unsupportedUnicodeSeparatorsAndControlsFailClosed() {
        // XML 1.0 can carry U+2028/U+2029. Rendering them as a single
        // ordinary space silently changes line/paragraph geometry.
        // Unspecified controls must also fail closed, not disappear.
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        for (String invalid : List.of("A\\u2028B", "A\\u2029B",
                "A\\u000BB", "A\\u000CB", "A\\u0085B")) {
            // Convert the literal escape to the intended code point so the
            // fixture source stays readable and contains no control bytes.
            int cp = Integer.parseInt(invalid.substring(3, 7), 16);
            String text = "A" + new String(Character.toChars(cp)) + "B";
            var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                    List.of(new UdfReader.Paragraph(List.of(new UdfReader.Run(text, style)))));
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.render(List.of(document)), "U+" + Integer.toHexString(cp));
            assertTrue(failure.getMessage().contains("U+"),
                    "Unsupported separator must produce a visible diagnostic");
        }
    }

    @Test void trailingUnsupportedControlsCannotBeTrimmedOrSkipped() {
        // U+2028/U+2029 are Java whitespace. stripTrailing() and isBlank()
        // could previously erase them in the last run or paragraph, bypassing
        // the fail-closed validation inside Layout.add().
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        for (int cp : new int[]{0x2028, 0x2029, 0x000B, 0x000C, 0x0085}) {
            String unsupported = new String(Character.toChars(cp));
            var visible = new UdfReader.Run("VISIBLE", style);
            var cases = List.of(
                    List.of(new UdfReader.Paragraph(List.of(
                            new UdfReader.Run("VISIBLE" + unsupported, style)))),
                    List.of(new UdfReader.Paragraph(List.of(
                            visible, new UdfReader.Run(unsupported, style)))),
                    List.of(new UdfReader.Paragraph(List.of(visible)),
                            new UdfReader.Paragraph(List.of(
                                    new UdfReader.Run(unsupported, style)))));
            for (var paragraphs : cases) {
                var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0", paragraphs);
                var failure = assertThrows(IllegalArgumentException.class,
                        () -> PdfExporter.render(List.of(document)),
                        "Trailing unsupported U+" + Integer.toHexString(cp) + " must fail closed");
                assertTrue(failure.getMessage().contains("U+"),
                        "The rejected character must be identified to the user");
            }
        }
    }

    @Test void trailingEmptyEditorParagraphsDoNotCreateBlankPage() throws Exception {
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        var empty = new DocumentElements.ParagraphBlock(new UdfReader.Paragraph(List.of(
                new UdfReader.Run("\n \t", style))));
        List<DocumentElements.Block> blocks = new ArrayList<>(paragraphs(34).blocks());
        blocks.add(empty);
        blocks.add(new DocumentElements.ParagraphBlock(new UdfReader.Paragraph(List.of(
                new UdfReader.Run("", style)))));
        var document = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(),
                blocks, DocumentElements.PageSpec.a4());
        try (PDDocument pdf = PdfExporter.render(List.of(document))) {
            assertEquals(1, pdf.getNumberOfPages(),
                    "Trailing empty editor paragraphs must not allocate page two");
            assertTrue(new PDFTextStripper().getText(pdf).contains("LINE33"));
        }

        // An empty paragraph in the middle is meaningful spacing and must remain.
        List<DocumentElements.Block> interior = new ArrayList<>(paragraphs(33).blocks());
        interior.add(empty);
        interior.add(new DocumentElements.ParagraphBlock(new UdfReader.Paragraph(List.of(
                new UdfReader.Run("LAST", style)))));
        var withInteriorGap = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                List.of(), interior, DocumentElements.PageSpec.a4());
        try (PDDocument pdf = PdfExporter.render(List.of(withInteriorGap))) {
            assertEquals(2, pdf.getNumberOfPages(),
                    "Interior blank paragraph must still consume vertical space");
            var secondPage = new PDFTextStripper();
            secondPage.setStartPage(2);
            secondPage.setEndPage(2);
            assertTrue(secondPage.getText(pdf).contains("LAST"));
        }
    }

    @Test void invisibleFormatControlsAndUnpairedSurrogatesFailClosedBeforePdfOutput() {
        // Bidi overrides/isolates and zero-width format controls must never
        // disappear or reorder visible content in the controlled synthetic subset.
        // An unpaired UTF-16 surrogate is not a valid Unicode scalar value.
        var style = new UdfReader.Style(11f, false, Color.BLACK);
        for (int cp : new int[]{0x00AD, 0x200B, 0x200D, 0x202E, 0x2066, 0xFEFF, 0xD800}) {
            String unsupported = String.valueOf((char) cp);
            String text = "A" + unsupported + "B";
            var paragraph = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                    List.of(new UdfReader.Paragraph(List.of(new UdfReader.Run(text, style)))));
            var table = new DocumentElements.TableBlock(List.of(120f),
                    List.of(new DocumentElements.Row(List.of(
                            new DocumentElements.Cell(text, 1, 1)), 24f)));
            var tableDocument = new UdfReader.DocumentSpec("synthetic.udf", "1.0",
                    List.of(), List.of(table), DocumentElements.PageSpec.a4());
            for (var document : List.of(paragraph, tableDocument)) {
                var failure = assertThrows(IllegalArgumentException.class,
                        () -> PdfExporter.render(List.of(document)),
                        "Unsupported U+" + Integer.toHexString(cp) + " must fail closed");
                assertTrue(failure.getMessage().contains("U+"
                                + Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT)),
                        "Diagnostic must identify the rejected code point");
            }
        }
    }
}
