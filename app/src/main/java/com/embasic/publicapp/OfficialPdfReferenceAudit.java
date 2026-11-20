package com.embasic.publicapp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * TUR 211: offline, read-only comparison of two user-owned local PDFs.
 * The reference may be manually exported by the official UYAP Editor.
 *
 * This is NOT a bridge to UYAP, a native-conversion API, a license grant or
 * proof of equivalence with any real UYAP document. No PDF content, images,
 * filenames or extracted text are written to logs or the network.
 */
public final class OfficialPdfReferenceAudit {
    private static final int DPI = 96;
    private static final int CHANNEL_TOLERANCE = 8;
    private static final int MAX_PAGES = 64;
    private static final long MAX_INPUT_BYTES = 64L * 1024 * 1024;
    private static final long MAX_PAGE_PIXELS = 8_000_000L;
    private static final float GEOMETRY_EPSILON = 0.05f;

    private OfficialPdfReferenceAudit() {}

    /** Aggregates only: never retain source text or raster images in this record. */
    public record PageCheck(int oneBasedPage, boolean geometryMatches,
                            boolean extractedTextMatches, long differentPixels,
                            long renderedPixels, int maximumChannelDifference,
                            int glyphCount, int differentFontGlyphs,
                            int differentTextPositions, int changedRasterRows,
                            int changedRasterColumns) {
        public boolean matches() {
            return geometryMatches && extractedTextMatches
                    && differentFontGlyphs == 0 && differentTextPositions == 0
                    && renderedPixels > 0 && differentPixels == 0;
        }
    }

    public record Result(int referencePages, int candidatePages,
                         List<PageCheck> pages) {
        public Result { pages = List.copyOf(pages); }
        public boolean matches() {
            return referencePages > 0 && referencePages == candidatePages
                    && pages.size() == referencePages
                    && pages.stream().allMatch(PageCheck::matches);
        }
        public long differentPixels() {
            return pages.stream().mapToLong(PageCheck::differentPixels).sum();
        }
    }

    private static Path validateLocalPdf(Path input) throws IOException {
        if (input == null || input.getFileName() == null
                || !input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new IOException("PDF input required");
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)
                || Files.isSymbolicLink(path))
            throw new IOException("Input must be a readable regular non-link PDF");
        long size = Files.size(path);
        if (size == 0 || size > MAX_INPUT_BYTES)
            throw new IOException("Input exceeds offline audit size boundary");
        return path;
    }

    private static boolean same(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y)
                && Math.abs(x - y) <= GEOMETRY_EPSILON;
    }

    private static boolean boxEquals(PDRectangle a, PDRectangle b) {
        return a != null && b != null
                && same(a.getLowerLeftX(), b.getLowerLeftX())
                && same(a.getLowerLeftY(), b.getLowerLeftY())
                && same(a.getUpperRightX(), b.getUpperRightX())
                && same(a.getUpperRightY(), b.getUpperRightY());
    }

    private static boolean geometryEquals(PDPage a, PDPage b) {
        // Raster pixels and extracted text do not establish physical print size:
        // /UserUnit changes the physical scale even when MediaBox is unchanged.
        // Trim/Bleed/Art boxes likewise affect print and production workflows
        // without necessarily changing the 96-DPI on-screen raster.
        float unitA = a.getUserUnit(), unitB = b.getUserUnit();
        return Float.isFinite(unitA) && Float.isFinite(unitB)
                && unitA > 0 && unitB > 0
                && Float.compare(unitA, unitB) == 0
                && boxEquals(a.getMediaBox(), b.getMediaBox())
                && boxEquals(a.getCropBox(), b.getCropBox())
                && boxEquals(a.getTrimBox(), b.getTrimBox())
                && boxEquals(a.getBleedBox(), b.getBleedBox())
                && boxEquals(a.getArtBox(), b.getArtBox())
                && a.getRotation() == b.getRotation();
    }

    /**
     * Raster and extracted-text parity cannot see invisible annotations,
     * interactive form fields, catalog actions or embedded-file name trees.
     * Reject them before reporting MATCH: this comparator supports only a
     * deliberately bounded static-PDF subset, not general PDF semantics.
     * This is not an exhaustive PDF security scanner.
     */
    private static void requireStaticPdf(PDDocument pdf) throws IOException {
        COSDictionary catalog = pdf.getDocumentCatalog().getCOSObject();
        for (String key : new String[]{
                "OpenAction", "AA", "AcroForm", "Names", "Outlines",
                "Perms", "Collection", "OCProperties"}) {
            if (catalog.containsKey(COSName.getPDFName(key)))
                throw new IOException("Interactive PDF catalog feature unsupported");
        }
        for (PDPage page : pdf.getPages()) {
            COSDictionary dictionary = page.getCOSObject();
            for (String key : new String[]{"Annots", "AA", "PresSteps"}) {
                if (dictionary.containsKey(COSName.getPDFName(key)))
                    throw new IOException("Interactive PDF page feature unsupported");
            }
        }
    }

    private static void requireBoundedRaster(PDPage page) throws IOException {
        PDRectangle crop = page.getCropBox();
        if (crop == null || !Float.isFinite(crop.getWidth())
                || !Float.isFinite(crop.getHeight())
                || crop.getWidth() <= 0 || crop.getHeight() <= 0)
            throw new IOException("Invalid page crop geometry");
        double width = crop.getWidth() * DPI / 72.0;
        double height = crop.getHeight() * DPI / 72.0;
        if (page.getRotation() % 180 != 0) {
            double swap = width; width = height; height = swap;
        }
        if (!Double.isFinite(width) || !Double.isFinite(height)
                || width < 1 || height < 1
                || Math.ceil(width) * Math.ceil(height) > MAX_PAGE_PIXELS)
            throw new IOException("Page rasterization exceeds audit budget");
    }

    /**
     * Fail-closed on malformed/oversized files; no output files are created.
     * Full raster samples use deterministic PDFBox RGB at 96 DPI with a
     * per-channel tolerance of eight; this is a difference screen, not a
     * certification of native UYAP visual or semantic fidelity.
     */
    public static Result audit(Path reference, Path candidate) throws IOException {
        Path trustedReference = validateLocalPdf(reference);
        Path localCandidate = validateLocalPdf(candidate);
        if (Files.isSameFile(trustedReference, localCandidate))
            throw new IOException("Reference and candidate must be separate physical files");
        try (PDDocument first = Loader.loadPDF(trustedReference.toFile());
             PDDocument second = Loader.loadPDF(localCandidate.toFile())) {
            int a = first.getNumberOfPages(), b = second.getNumberOfPages();
            if (a < 1 || b < 1 || a > MAX_PAGES || b > MAX_PAGES)
                throw new IOException("Unsupported page count for bounded audit");
            requireStaticPdf(first);
            requireStaticPdf(second);
            PDFRenderer rasterA = new PDFRenderer(first);
            PDFRenderer rasterB = new PDFRenderer(second);
            OfflinePdfGlyphAudit textA = new OfflinePdfGlyphAudit();
            OfflinePdfGlyphAudit textB = new OfflinePdfGlyphAudit();
            List<PageCheck> checks = new ArrayList<>();
            for (int page = 0; page < Math.min(a, b); page++) {
                PDPage one = first.getPage(page), two = second.getPage(page);
                boolean geometry = geometryEquals(one, two);
                // Glyph metrics are collected only in this method scope and
                // never exposed in the result. Extraction must be bounded and
                // fail closed on missing font metadata or invalid coordinates.
                var referenceGlyphs = textA.inspectPage(first, page);
                var candidateGlyphs = textB.inspectPage(second, page);
                var glyphs = OfflinePdfGlyphAudit.compare(
                        referenceGlyphs, candidateGlyphs);
                boolean textEqual = glyphs.textMatches();
                if (!geometry) {
                    checks.add(new PageCheck(page + 1, false, textEqual, 0, 0, 0,
                            glyphs.maximumGlyphs(), glyphs.differentFontGlyphs(),
                            glyphs.differentPositionGlyphs(), 0, 0));
                    continue;
                }
                requireBoundedRaster(one);
                requireBoundedRaster(two);
                BufferedImage imageA = rasterA.renderImageWithDPI(page, DPI, ImageType.RGB);
                BufferedImage imageB = rasterB.renderImageWithDPI(page, DPI, ImageType.RGB);
                if (imageA.getWidth() != imageB.getWidth()
                        || imageA.getHeight() != imageB.getHeight()) {
                    checks.add(new PageCheck(page + 1, false, textEqual, 0, 0, 0,
                            glyphs.maximumGlyphs(), glyphs.differentFontGlyphs(),
                            glyphs.differentPositionGlyphs(), 0, 0));
                    continue;
                }
                long pixels = (long) imageA.getWidth() * imageA.getHeight();
                if (pixels > MAX_PAGE_PIXELS)
                    throw new IOException("Rendered PDF page exceeded audit pixel limit");
                long different = 0;
                int maximum = 0, changedRows = 0;
                boolean[] changedColumns = new boolean[imageA.getWidth()];
                for (int y = 0; y < imageA.getHeight(); y++) {
                    boolean changedRow = false;
                    for (int x = 0; x < imageA.getWidth(); x++) {
                        int rgbA = imageA.getRGB(x, y), rgbB = imageB.getRGB(x, y);
                        int red = Math.abs(((rgbA >>> 16) & 255) - ((rgbB >>> 16) & 255));
                        int green = Math.abs(((rgbA >>> 8) & 255) - ((rgbB >>> 8) & 255));
                        int blue = Math.abs((rgbA & 255) - (rgbB & 255));
                        int worst = Math.max(red, Math.max(green, blue));
                        if (worst > CHANNEL_TOLERANCE) {
                            different++;
                            changedRow = true;
                            changedColumns[x] = true;
                        }
                        maximum = Math.max(maximum, worst);
                    }
                    if (changedRow) changedRows++;
                }
                int changedColumnCount = 0;
                for (boolean changed : changedColumns)
                    if (changed) changedColumnCount++;
                checks.add(new PageCheck(page + 1, true, textEqual,
                        different, pixels, maximum,
                        glyphs.maximumGlyphs(), glyphs.differentFontGlyphs(),
                        glyphs.differentPositionGlyphs(),
                        changedRows, changedColumnCount));
            }
            return new Result(a, b, checks);
        }
    }

    /**
     * Usage: java -cp embasic-public-desktop-*-all.jar
     *          com.embasic.publicapp.OfficialPdfReferenceAudit
     *          official-export.pdf embasic-output.pdf
     * Exit: 0 only if all compared checks match, 2 on a difference,
     *       3 on malformed/unsafe/unreadable input. Never prints document text.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("REFERENCE_AUDIT: ERROR; expected exactly two local PDF paths");
            System.exit(3);
            return;
        }
        try {
            Result result = audit(Path.of(args[0]), Path.of(args[1]));
            System.out.printf(Locale.ROOT,
                    "REFERENCE_AUDIT: %s referencePages=%d candidatePages=%d "
                    + "checkedPages=%d differingPixels=%d dpi=%d channelTolerance=%d%n",
                    result.matches() ? "MATCH" : "DIFFERENCE",
                    result.referencePages(), result.candidatePages(),
                    result.pages().size(), result.differentPixels(), DPI, CHANNEL_TOLERANCE);
            for (PageCheck page : result.pages()) {
                System.out.printf(Locale.ROOT,
                        "REFERENCE_AUDIT_PAGE: page=%d geometry=%s text=%s "
                        + "differentPixels=%d renderedPixels=%d maximumChannelDelta=%d "
                        + "glyphCount=%d fontGlyphDifferences=%d "
                        + "textPositionDifferences=%d changedRasterRows=%d "
                        + "changedRasterColumns=%d%n",
                        page.oneBasedPage(),
                        page.geometryMatches() ? "MATCH" : "DIFFERENCE",
                        page.extractedTextMatches() ? "MATCH" : "DIFFERENCE",
                        page.differentPixels(), page.renderedPixels(),
                        page.maximumChannelDifference(),
                        page.glyphCount(), page.differentFontGlyphs(),
                        page.differentTextPositions(), page.changedRasterRows(),
                        page.changedRasterColumns());
            }
            if (!result.matches()) System.exit(2);
        } catch (Exception ex) {
            // Privacy: never print PDF contents, filenames or exception stacks.
            System.err.println("REFERENCE_AUDIT: ERROR; invalid, changed, unreadable "
                    + "or over-budget local PDF; no reference verdict");
            System.exit(3);
        }
    }
}
