package com.embasic.publicapp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * TUR215: bounded, read-only local PDF text geometry diagnostic.
 *
 * No PDF text, Unicode glyph or filename is exposed through the public
 * result. Only internal, short-lived glyph metrics and sanitized font family
 * identifiers are compared; originals are never written to logs.
 * This is not a font-licensing check or a native UYAP fidelity certificate.
 */
final class OfflinePdfGlyphAudit extends PDFTextStripper {
    private static final int MAX_GLYPHS_PER_PAGE = 50_000;
    private static final float POSITION_TOLERANCE_PT = 0.20f;
    private static final float FONT_SIZE_TOLERANCE_PT = 0.05f;

    private final List<Glyph> glyphs = new ArrayList<>();

    OfflinePdfGlyphAudit() throws IOException {
        setSortByPosition(true);
    }

    private record Glyph(String font, float size, float x, float y,
                         float width, float height, float direction) {}

    record Snapshot(String extractedText, List<Glyph> glyphs) {
        Snapshot {
            glyphs = List.copyOf(glyphs);
        }
    }

    record Comparison(boolean textMatches, int maximumGlyphs,
                      int differentFontGlyphs, int differentPositionGlyphs) {
        boolean layoutMatches() {
            return differentFontGlyphs == 0 && differentPositionGlyphs == 0;
        }
    }

    Snapshot inspectPage(PDDocument pdf, int pageZeroBased) throws IOException {
        glyphs.clear();
        setStartPage(pageZeroBased + 1);
        setEndPage(pageZeroBased + 1);
        String extracted = getText(pdf);
        return new Snapshot(extracted, glyphs);
    }

    private static boolean near(float a, float b, float tolerance) {
        return Float.isFinite(a) && Float.isFinite(b)
                && Math.abs(a - b) <= tolerance;
    }

    private static boolean finite(float... values) {
        for (float value : values)
            if (!Float.isFinite(value)) return false;
        return true;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions)
            throws IOException {
        for (TextPosition position : positions) {
            if (glyphs.size() >= MAX_GLYPHS_PER_PAGE)
                throw new IOException("Glyph audit page budget exceeded");
            if (position.getFont() == null)
                throw new IOException("Font metadata unavailable for glyph audit");
            String rawFont = position.getFont().getName();
            if (rawFont == null || rawFont.isBlank())
                throw new IOException("Font metadata unavailable for glyph audit");
            // Embedded PDF subset prefixes are six uppercase letters followed
            // by '+'. Strip only that standard prefix, not an arbitrary name.
            String family = rawFont.replaceFirst("^[A-Z]{6}\\+", "");
            float size = position.getFontSizeInPt();
            float x = position.getXDirAdj();
            float y = position.getYDirAdj();
            float width = position.getWidthDirAdj();
            float height = position.getHeightDir();
            float direction = position.getDir();
            if (!finite(size, x, y, width, height, direction)
                    || size <= 0 || width < 0 || height < 0)
                throw new IOException("Invalid PDF glyph geometry");
            glyphs.add(new Glyph(family, size, x, y, width, height, direction));
        }
        super.writeString(text, positions);
    }

    static Comparison compare(Snapshot reference, Snapshot candidate) {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(candidate);
        List<Glyph> a = reference.glyphs(), b = candidate.glyphs();
        int missing = Math.abs(a.size() - b.size());
        int differentFont = missing, differentPosition = missing;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            Glyph x = a.get(i), y = b.get(i);
            if (!x.font.equals(y.font)
                    || !near(x.size, y.size, FONT_SIZE_TOLERANCE_PT))
                differentFont++;
            if (!near(x.x, y.x, POSITION_TOLERANCE_PT)
                    || !near(x.y, y.y, POSITION_TOLERANCE_PT)
                    || !near(x.width, y.width, POSITION_TOLERANCE_PT)
                    || !near(x.height, y.height, POSITION_TOLERANCE_PT)
                    || !near(x.direction, y.direction, 0.01f))
                differentPosition++;
        }
        return new Comparison(reference.extractedText().equals(candidate.extractedText()),
                Math.max(a.size(), b.size()), differentFont, differentPosition);
    }
}
