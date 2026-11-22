package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

/**
 * Original synthetic UTF-16 XML fixture. No real UYAP golden is implied.
 * The document's PDF glyph x-coordinates prove indentation is not dropped.
 */
final class UdfStructuralLineIndentTest {
    private static UdfReader.DocumentSpec parse(String content, int secondOffset) throws Exception {
        String xml = "<template format_id='1.0'><content>" + content
                + "</content><elements><paragraph><content startOffset='0' length='1'/>"
                + "</paragraph><paragraph><content startOffset='" + secondOffset
                + "' length='1'/></paragraph></elements></template>";
        return UdfReader.parse(xml.getBytes(StandardCharsets.UTF_8), "synthetic-indent.udf");
    }

    @Test void lfThenAsciiSpacesAreVisiblePdfParagraphIndentation() throws Exception {
        var spec = parse("X\n    Y", 6);
        assertEquals("    Y", spec.paragraphs().get(1).runs().get(0).text());
        try (PDDocument pdf = PdfExporter.render(List.of(spec))) {
            List<Float> x = new ArrayList<>(), y = new ArrayList<>();
            var stripper = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition position) {
                    if ("X".equals(position.getUnicode())) x.add(position.getXDirAdj());
                    if ("Y".equals(position.getUnicode())) y.add(position.getXDirAdj());
                    super.processTextPosition(position);
                }
            };
            String extracted = stripper.getText(pdf);
            assertTrue(extracted.contains("X") && extracted.contains("Y"), extracted);
            assertEquals(1, x.size());
            assertEquals(1, y.size());
            assertTrue(y.get(0) > x.get(0) + 5f,
                    "A stripped paragraph indent shifts actual PDF glyphs left: "
                            + x + " / " + y);
        }
    }

    @Test void crlfAndTabPreserveOnlySpacesAfterLastStructuralBreak() throws Exception {
        // XML char references preserve CRLF rather than XML normalizing raw CR.
        var crlf = parse("A&#13;&#10;   B", 6);
        assertEquals("   B", crlf.paragraphs().get(1).runs().get(0).text());
        var tab = parse("A\n\t  B", 5);
        assertEquals("  B", tab.paragraphs().get(1).runs().get(0).text());
        var structural = parse("A&#13;&#10;B", 3);
        assertEquals("B", structural.paragraphs().get(1).runs().get(0).text(),
                "A structural CRLF without spaces must not duplicate blank lines");
        var trailing = parse("A   \nB", 5);
        assertEquals("B", trailing.paragraphs().get(1).runs().get(0).text(),
                "Spaces BEFORE a structural newline aren't next-paragraph indentation");
    }
}
