package com.embasic.publicapp;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Original, public-only PDFBox text subset renderer. No authentic UYAP layout parity claims. */
public final class PdfExporter {
    private static final float LEFT = 48f, RIGHT = PDRectangle.A4.getWidth() - 48f;
    private static final float TOP = PDRectangle.A4.getHeight() - 48f, BOTTOM = 48f;
    private PdfExporter() {}

    public static Path systemFont(boolean bold) {
        String override = System.getProperty(bold ? "embasic.font.bold" : "embasic.font.regular");
        if (override != null && !override.isBlank()) {
            Path custom = Path.of(override);
            if (Files.isRegularFile(custom)) return custom;
            throw new IllegalArgumentException("Belirtilen font bulunamadı: " + custom);
        }
        String[] locations = bold
            ? new String[]{"C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/segoeuib.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"}
            : new String[]{"C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/segoeui.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"};
        for (String location : locations) {
            Path candidate = Path.of(location);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unicode PDF fontu bulunamadı; Windows Arial/Segoe UI veya DejaVu Sans gerekli");
    }

    /** Validate original runs before any trailing whitespace is trimmed or skipped. */
    private static void validateSupportedText(String text) {
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            // Zero-width and bidi format controls have no defined placement in
            // the synthetic PDF subset. Reject them before font loading rather
            // than silently dropping, reordering, or hiding visible text.
            int category = Character.getType(cp);
            // PDFBox showText() does not perform Arabic/Indic shaping or
            // bidirectional paragraph layout. Combining marks need tested
            // mark placement. Reject these before any PDF is staged, rather
            // than publishing misleading, apparently successful output.
            int direction=Character.getDirectionality(cp);
            Character.UnicodeScript script=Character.UnicodeScript.of(cp);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || category == Character.NON_SPACING_MARK
                    || category == Character.COMBINING_SPACING_MARK
                    || category == Character.ENCLOSING_MARK
                    || requiresComplexShaping(script))
                throw new IllegalArgumentException("PDF için biçimlendirmesi doğrulanmamış Unicode: U+"
                        + Integer.toHexString(cp).toUpperCase(Locale.ROOT));
            // Layout.add() maps Character.isWhitespace to a single ASCII space.
            // Reject non-ASCII whitespace instead of silently shrinking em/en
            // and ideographic spaces (including at the end of a document).
            if (cp != ' ' && cp != '\r' && cp != '\n' && cp != '\t'
                    && (Character.isISOControl(cp) || Character.isWhitespace(cp)
                        || cp == 0x2028 || cp == 0x2029
                        || category == Character.FORMAT || category == Character.SURROGATE))
                throw new IllegalArgumentException("Desteklenmeyen metin kontrol veya ayırıcı karakteri: U+"
                        + Integer.toHexString(cp).toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Conservative explicit subset; not a claim that other writing systems
     * are impossible to render. They need a separately validated shaping
     * engine before being considered safe for pixel-faithful UDF export.
     */
    private static boolean requiresComplexShaping(Character.UnicodeScript script) {
        return switch (script) {
            case ARABIC, HEBREW, SYRIAC, THAANA, NKO, MANDAIC,
                    SAMARITAN, DEVANAGARI, BENGALI, GURMUKHI, GUJARATI,
                    ORIYA, TAMIL, TELUGU, KANNADA, MALAYALAM, SINHALA,
                    THAI, LAO, TIBETAN, MYANMAR, KHMER, JAVANESE,
                    BALINESE -> true;
            default -> false;
        };
    }

    public static PDDocument render(List<UdfReader.DocumentSpec> docs) throws Exception {
        return renderInternal(docs, false);
    }

    /**
     * A thumbnail only needs the first page. Avoid laying out every later page
     * of a large synthetic document just to render its first-page preview.
     * The full export path is unchanged and still validates every page.
     */
    static PDDocument renderPreview(UdfReader.DocumentSpec doc) throws Exception {
        return renderInternal(List.of(doc), true);
    }

    private static final class PreviewPageComplete extends RuntimeException {
        PreviewPageComplete() { super(null, null, false, false); }
    }

    /**
     * Check font-independent geometry before loading system fonts. An invalid
     * table or oversized image must fail immediately instead of waiting for
     * PDFBox's potentially expensive first-use font discovery on Windows.
     * The actual renderer retains its layout checks as a second safeguard.
     */
    private static void preflightPageGeometry(UdfReader.DocumentSpec document) {
        DocumentElements.PageSpec page = document.page();
        float usableWidth = page.width() - 2 * page.margin();
        float usableHeight = page.height() - 2 * page.margin();
        if (!Float.isFinite(usableWidth) || !Float.isFinite(usableHeight)
                || usableWidth <= 0 || usableHeight <= 0)
            throw new IllegalArgumentException("Geçersiz sayfa geometrisi");
        for (DocumentElements.Block block : document.blocks()) {
            if (block instanceof DocumentElements.TableBlock table) {
                TableLayout.Geometry geometry = TableLayout.calculate(table);
                float width = 0f;
                for (float column : geometry.widths()) width += column;
                if (width > usableWidth)
                    throw new IllegalArgumentException("Tablo sayfa genişliğini aşıyor");
                for (TableLayout.CellPosition cell : geometry.cells())
                    if (cell.height() < 18)
                        throw new IllegalArgumentException("Tablo hücresi taşıyor: " + cell.text());
                for (int[] group : TableLayout.pageGroups(geometry)) {
                    double height = 0;
                    for (int row = group[0]; row < group[1]; row++)
                        height += geometry.rowHeights().get(row);
                    if (height > usableHeight)
                        throw new IllegalArgumentException("Rowspan grubu tek sayfaya sığmıyor");
                }
            } else if (block instanceof DocumentElements.ImageBlock image) {
                if (image.width() > usableWidth || image.height() > usableHeight)
                    throw new IllegalArgumentException("PNG sayfaya sığmıyor");
            }
        }
    }

    private static PDDocument renderInternal(List<UdfReader.DocumentSpec> docs,
                                             boolean previewOnly) throws Exception {
        if (docs == null || docs.isEmpty()) throw new IllegalArgumentException("PDF için en az bir UDF gerekli");
        // Match the 160-card desktop limit for direct API/CLI merges as well.
        // Reject before PDF allocation, font discovery, or output staging.
        if (docs.size() > FileImportPolicy.MAX_CARDS)
            throw new IllegalArgumentException("En fazla " + FileImportPolicy.MAX_CARDS
                    + " UDF dosyası birleştirilebilir");
        // A final run or paragraph may be omitted during layout to avoid blank
        // pages. Validate the ORIGINAL text first: unsupported separators must
        // never disappear through stripTrailing(), isBlank(), or run skipping.
        for (UdfReader.DocumentSpec document : docs) {
            boolean hasVisibleContent = false;
            for (DocumentElements.Block block : document.blocks()) {
                if (block instanceof DocumentElements.ParagraphBlock paragraph) {
                    for (UdfReader.Run run : paragraph.paragraph().runs()) {
                        validateSupportedText(run.text());
                        if (!run.text().isBlank()) hasVisibleContent = true;
                    }
                } else if (block instanceof DocumentElements.TableBlock table) {
                    // Even a table with empty cells draws a visible grid.
                    hasVisibleContent = true;
                    // Table cells are rendered as a single PDF text line. A tab,
                    // newline, or unsupported separator must not be emitted as
                    // an invisible glyph or silently alter the cell geometry.
                    // Validate before font loading and before any PDF is written.
                    for (DocumentElements.Row row : table.rows())
                        for (DocumentElements.Cell cell : row.cells()) {
                            validateSupportedText(cell.text());
                            if (cell.text().indexOf('\t') >= 0
                                    || cell.text().indexOf('\r') >= 0
                                    || cell.text().indexOf('\n') >= 0)
                                throw new IllegalArgumentException(
                                        "Desteklenmeyen tablo hücresi satır sonu veya sekme karakteri");
                        }
                } else if (block instanceof DocumentElements.ImageBlock) {
                    hasVisibleContent = true;
                }
            }
            // A whitespace-only synthetic UDF otherwise saves as a successful
            // but visually empty PDF after trailing editor runs are trimmed.
            // Reject the entire merge rather than silently including blank files.
            if (!hasVisibleContent)
                throw new IllegalArgumentException(
                        "PDF'ye aktarılacak görünür UDF içeriği bulunamadı: " + document.fileName());
            preflightPageGeometry(document);
        }
        PDDocument pdf = new PDDocument();
        try {
            // Load each style only on first actual use, sharing embedded fonts
            // across all pages/documents. A first-page thumbnail must not wait
            // for a bold font used exclusively on a later page.
            FontSet fonts = new FontSet(pdf);
            try {
                for (UdfReader.DocumentSpec document : docs) {
                try (Layout layout = new Layout(pdf, fonts, document.page(), previewOnly)) {
                    layout.newPage();
                    List<DocumentElements.Block> blocks = document.blocks();
                    // Trailing empty editor paragraphs must not allocate an otherwise blank page.
                    // Preserve interior empty paragraphs and at least one block for an empty document.
                    int lastBlock = blocks.size() - 1;
                    while (lastBlock > 0 && blocks.get(lastBlock) instanceof DocumentElements.ParagraphBlock p
                            && p.paragraph().runs().stream().allMatch(run -> run.text().isBlank()))
                        lastBlock--;
                    for (int index = 0; index <= lastBlock; index++) {
                        DocumentElements.Block block = blocks.get(index);
                        if (block instanceof DocumentElements.ParagraphBlock p) {
                            List<UdfReader.Run> runs = p.paragraph().runs();
                            int lastRun = runs.size() - 1;
                            if (index == lastBlock) {
                                // Trailing whitespace has no visible glyphs. In particular,
                                // terminal newlines must not allocate blank PDF pages.
                                // Preserve whitespace between runs and before later blocks.
                                while (lastRun >= 0 && runs.get(lastRun).text().isBlank()) lastRun--;
                            }
                            for (int runIndex = 0; runIndex <= lastRun; runIndex++) {
                                UdfReader.Run run = runs.get(runIndex);
                                if (index == lastBlock && runIndex == lastRun) {
                                    String trimmed = run.text().stripTrailing();
                                    if (!trimmed.equals(run.text()))
                                        run = new UdfReader.Run(trimmed, run.style());
                                }
                                layout.add(run);
                            }
                            // Only add paragraph spacing when another block follows.
                            // A trailing gap at the bottom must not emit an empty PDF page.
                            if (index < lastBlock) layout.endParagraph();
                        } else if (block instanceof DocumentElements.TableBlock table) {
                            layout.table(table);
                        } else if (block instanceof DocumentElements.ImageBlock image) {
                            layout.image(image);
                        } else throw new IllegalArgumentException("Desteklenmeyen belge bloğu");
                    }
                }
                }
            } catch (PreviewPageComplete complete) {
                if (!previewOnly) throw complete;
                // The first page content stream was closed by Layout.close().
            }
            if (pdf.getNumberOfPages() == 0) throw new IllegalStateException("PDF sayfası oluşturulamadı");
            return pdf;
        } catch (Exception e) {
            pdf.close();
            throw e;
        }
    }

    public static BufferedImage firstPage(UdfReader.DocumentSpec doc) throws Exception {
        try (PDDocument pdf = renderPreview(doc)) {
            return new PDFRenderer(pdf).renderImageWithDPI(0, 40);
        }
    }

    /**
     * Reject unusable output paths before a CLI merge parses up to 160 UDFs.
     * This is advisory only: save() repeats the check and publishValidated()
     * still enforces no-replace semantics against concurrent file creation.
     */
    static Path preflightTarget(Path target, boolean overwrite) {
        if (target == null || target.getFileName() == null
                || !target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new IllegalArgumentException("Çıktı dosyası .pdf uzantılı olmalıdır");
        Path abs = target.toAbsolutePath().normalize();
        // Never follow an output-file symlink: replacing it would update the
        // link itself, not necessarily the PDF the user inspected/approved.
        if (Files.isSymbolicLink(abs))
            throw new IllegalArgumentException("PDF hedefi sembolik bağlantı olamaz; gerçek dosyayı seçin");
        Path folder = abs.getParent();
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Çıktı klasörü bulunamadı");
        // A directory named *.pdf is not an overwriteable PDF. Reject it before
        // staging or reading a large batch, instead of failing after rendering.
        if (Files.exists(abs) && !Files.isRegularFile(abs))
            throw new IllegalArgumentException("Çıktı yolu normal bir PDF dosyası olmalıdır");
        if (Files.exists(abs) && !overwrite)
            throw new IllegalArgumentException("PDF zaten var, üzerine yazma onayı gerekli");
        return abs;
    }

    public static int save(List<UdfReader.DocumentSpec> docs, Path target, boolean overwrite) throws Exception {
        return save(docs, target, overwrite, () -> {});
    }

    /** Validate mutable GUI sources after PDF staging but before publishing any output. */
    @FunctionalInterface
    interface BeforePublishCheck {
        void verify() throws Exception;
    }

    static int save(List<UdfReader.DocumentSpec> docs, Path target, boolean overwrite,
                    BeforePublishCheck beforePublish) throws Exception {
        if (beforePublish == null)
            throw new IllegalArgumentException("PDF son kaynak doğrulaması eksik");
        Path abs = preflightTarget(target, overwrite);
        // Overwrite consent applies to the PDF the user actually inspected.
        // Do not silently replace a newer file written by another application
        // while a large UDF merge is rendering.
        TargetSnapshot originalTarget = overwrite ? TargetSnapshot.capture(abs) : null;
        Path folder = abs.getParent();
        Path staging = Files.createTempFile(folder, ".embasic-pending-", ".pdf");
        try {
            int pages;
            try (PDDocument pdf = render(docs)) {
                pages = pdf.getNumberOfPages();
                pdf.save(staging.toFile());
            }
            try (PDDocument reloaded = Loader.loadPDF(staging.toFile())) {
                if (reloaded.getNumberOfPages() != pages || pages < 1)
                    throw new IOException("PDF sayfa doğrulaması başarısız");
            }
            // A source may change during a long 160-document render. Keep the
            // validated PDF private until the caller confirms the same bytes
            // still back its parsed documents; finally always cleans staging.
            beforePublish.verify();
            if (overwrite) TargetSnapshot.verifyUnchanged(abs, originalTarget);
            publishValidated(staging, abs, overwrite);
            return pages;
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /**
     * Advisory optimistic-concurrency guard for approved PDF overwrites.
     * SHA-256 catches edits that preserve size/timestamps; fileKey also catches
     * same-byte atomic replacement when the provider exposes an identity.
     * This does not make the final check-and-move atomic against hostile races.
     */
    private record TargetSnapshot(Object fileKey, String sha256) {
        static TargetSnapshot capture(Path target) throws IOException {
            if (Files.isSymbolicLink(target))
                throw new IllegalArgumentException("PDF hedefi sembolik bağlantı olamaz; gerçek dosyayı seçin");
            if (!Files.exists(target)) return null;
            if (!Files.isRegularFile(target))
                throw new IllegalArgumentException("Çıktı yolu normal bir PDF dosyası olmalıdır");
            Object key = Files.readAttributes(target,
                    java.nio.file.attribute.BasicFileAttributes.class).fileKey();
            return new TargetSnapshot(key, fingerprint(target));
        }

        static void verifyUnchanged(Path target, TargetSnapshot original) throws IOException {
            // A target switched to a symlink during rendering must never be
            // treated as the originally approved regular output file.
            if (Files.isSymbolicLink(target))
                throw new IllegalArgumentException("PDF hedefi işlem sırasında bağlantıya dönüştü; yeniden onaylayın");
            if (original == null) {
                if (Files.exists(target))
                    throw new IllegalArgumentException(
                            "PDF hedefi işlem sırasında oluşturuldu; yeniden onaylayın");
                return;
            }
            if (!Files.isRegularFile(target))
                throw new IllegalArgumentException(
                        "Mevcut PDF işlem sırasında kaldırıldı veya değiştirildi; yeniden onaylayın");
            Object currentKey = Files.readAttributes(target,
                    java.nio.file.attribute.BasicFileAttributes.class).fileKey();
            if ((original.fileKey != null && currentKey != null
                    && !original.fileKey.equals(currentKey))
                    || !original.sha256.equals(fingerprint(target)))
                throw new IllegalArgumentException(
                        "Mevcut PDF işlem sırasında değiştirildi; yeniden onaylayın");
        }

        private static String fingerprint(Path target) throws IOException {
            try {
                java.security.MessageDigest digest =
                        java.security.MessageDigest.getInstance("SHA-256");
                try (java.security.DigestInputStream stream =
                             new java.security.DigestInputStream(Files.newInputStream(target), digest)) {
                    stream.transferTo(java.io.OutputStream.nullOutputStream());
                }
                return java.util.HexFormat.of().formatHex(digest.digest());
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 kullanılamıyor", ex);
            }
        }
    }

    /**
     * Commit an already validated PDF. In particular, a target created after the
     * initial save() check must never be overwritten without user consent.
     * No-replace moves are deliberately NOT marked ATOMIC_MOVE: Java permits
     * provider-specific replacement behavior with ATOMIC_MOVE.
     */
    static void publishValidated(Path staging, Path target, boolean overwrite) throws IOException {
        // Defense in depth for direct callers and a late target-type change.
        // This check is advisory, not an atomic defense against hostile races.
        if (Files.isSymbolicLink(target))
            throw new IllegalArgumentException("PDF hedefi sembolik bağlantı olamaz; gerçek dosyayı seçin");
        if (!overwrite) {
            try {
                Files.move(staging, target);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                throw new IllegalArgumentException("PDF zaten var, üzerine yazma onayı gerekli", e);
            }
            return;
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Preserve the existing PDF until a validated replacement is ready.
            if (Files.exists(target)) {
                Path backup = Files.createTempFile(target.getParent(), ".embasic-backup-", ".pdf");
                Files.deleteIfExists(backup);
                Files.move(target, backup);
                boolean replaced = false;
                try {
                    Files.move(staging, target);
                    replaced = true;
                } finally {
                    if (replaced) Files.deleteIfExists(backup);
                    else Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.move(staging, target);
            }
        }
    }

    /** One font pair per PDF, resolved lazily at the first measured or drawn glyph. */
    private static final class FontSet {
        private final PDDocument pdf;
        private PDType0Font regular, bold;
        FontSet(PDDocument pdf) { this.pdf = pdf; }
        PDType0Font get(boolean useBold) throws IOException {
            if (useBold) {
                if (bold == null) bold = PDType0Font.load(pdf, systemFont(true).toFile());
                return bold;
            }
            if (regular == null) regular = PDType0Font.load(pdf, systemFont(false).toFile());
            return regular;
        }
    }

    private static final class Layout implements AutoCloseable {
        private final PDDocument pdf;
        private final FontSet fonts;
        private final boolean previewOnly;
        private final DocumentElements.PageSpec page;
        private final float left, right, top, bottom;
        private PDPageContentStream stream;
        // ASCII-space glyph metrics are constant for each font/style. Repeated
        // indentation must not ask PDFBox to measure the same glyph per space.
        private final Map<UdfReader.Style, Float> spaceWidths = new HashMap<>();
        private float x, y, lineHeight = 17f;
        // A CRLF pair can be split across differently styled text runs.
        private boolean previousWasCR;
        private Layout(PDDocument pdf, FontSet fonts,
                       DocumentElements.PageSpec page, boolean previewOnly) {
            this.pdf = pdf; this.fonts = fonts; this.page = page;
            this.previewOnly = previewOnly;
            left = page.margin(); right = page.width() - page.margin();
            top = page.height() - page.margin(); bottom = page.margin();
        }
        void newPage() throws IOException {
            // Stop before allocating page two. The try-with-resources caller
            // closes the first page stream even when this short-circuit fires.
            if (previewOnly && stream != null) throw new PreviewPageComplete();
            if (stream != null) stream.close();
            PDPage newPage = new PDPage(new PDRectangle(page.width(), page.height()));
            pdf.addPage(newPage);
            stream = new PDPageContentStream(pdf, newPage);
            x = left; y = top; lineHeight = 17f;
        }
        void newLine() throws IOException {
            x = left;
            y -= lineHeight;
            lineHeight = 17f;
            if (y < bottom) newPage();
        }
        void endParagraph() throws IOException {
            previousWasCR = false;
            newLine();
            y -= 5f;
            if (y < bottom) newPage();
        }
        private float width(String word, PDType0Font font, float size) throws IOException {
            try { return font.getStringWidth(word) * size / 1000f; }
            catch (IllegalArgumentException ex) {
                throw new IOException("PDF fontunda bulunmayan Unicode karakteri: " + word, ex);
            }
        }
        private void word(String text, UdfReader.Style style) throws IOException {
            if (text.isEmpty()) return;
            float size = style.size();
            // A glyph that cannot fit vertically on this page belongs to the
            // next page. In a first-page preview, stop before resolving a font
            // used only by that later page (potentially expensive on Windows).
            if (y - size < bottom) newPage();
            PDType0Font font = fonts.get(style.bold());
            float length = width(text, font, size);
            if (length > right - left)
                throw new IllegalArgumentException("Sayfaya sığmayan kelime: " + text.substring(0, Math.min(30, text.length())));
            // Recompute the *new* line's height after wrapping; otherwise a
            // large-font run can overlap the following text after newLine() resets it.
            if (x + length > right) newLine();
            lineHeight = Math.max(lineHeight, size * 1.4f);
            if (y - size < bottom) {
                newPage();
                // newPage() resets lineHeight to the default. A large run that
                // starts on the next page still needs its own line advance;
                // otherwise the following paragraph can overlap its glyphs.
                lineHeight = Math.max(lineHeight, size * 1.4f);
            }
            stream.beginText();
            stream.setFont(font, size);
            Color color = style.color();
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
            x += length;
        }
        private void spaces(int count, UdfReader.Style style) throws IOException {
            Float spaceWidth = spaceWidths.get(style);
            if (spaceWidth == null) {
                // Still fail closed if the requested font is unavailable: a
                // measured interior space cannot silently use another style.
                spaceWidth = width(" ", fonts.get(style.bold()), style.size());
                spaceWidths.put(style, spaceWidth);
            }
            float length = spaceWidth * count;
            // Preserve explicit leading spaces (including paragraph indentation).
            // Only discard a separator when it itself causes a soft line wrap;
            // otherwise the next word would silently lose its left offset.
            if (x + length > right) {
                newLine();
                return;
            }
            x += length;
        }
        void add(UdfReader.Run run) throws IOException {
            UdfReader.Style style = run.style();
            String text = run.text();
            StringBuilder token = new StringBuilder();
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                // These controls and Unicode separators have no defined layout
                // semantics in the public synthetic subset. Treating them as
                // ordinary spaces would silently change the PDF text geometry.
                if (cp != '\r' && cp != '\n' && cp != '\t'
                        && (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029))
                    throw new IllegalArgumentException("Desteklenmeyen metin kontrol veya ayırıcı karakteri: U+"
                            + Integer.toHexString(cp).toUpperCase(Locale.ROOT));
                if (cp == '\r') {
                    // A lone CR is a real line break, not a character to drop.
                    // Remember it so a following LF (even in another run) does
                    // not advance the cursor a second time.
                    word(token.toString(), style);
                    token.setLength(0);
                    newLine();
                    previousWasCR = true;
                    continue;
                }
                if (cp == '\n' && previousWasCR) {
                    previousWasCR = false;
                    continue;
                }
                previousWasCR = false;
                if (cp == '\n' || cp == '\t' || Character.isWhitespace(cp)) {
                    word(token.toString(), style);
                    token.setLength(0);
                    if (cp == '\n') newLine();
                    else if (cp == '\t') {
                        float stop = left + ((float) Math.floor((x - left) / 36f) + 1) * 36f;
                        if (stop > right) newLine(); else x = stop;
                    } else spaces(1, style);
                } else {
                    if (Character.isISOControl(cp))
                        throw new IllegalArgumentException("Desteklenmeyen metin kontrol karakteri");
                    token.appendCodePoint(cp);
                }
            }
            word(token.toString(), style);
        }
        void table(DocumentElements.TableBlock table) throws IOException {
            TableLayout.Geometry geometry=TableLayout.calculate(table);
            float totalWidth=0;
            for(float width:geometry.widths())totalWidth+=width;
            if(totalWidth>right-left)throw new IllegalArgumentException("Tablo sayfa genişliğini aşıyor");
            // Font-independent cell height checks remain eager. Text widths
            // are checked only when their page group is actually rendered:
            // a first-page thumbnail must not load fonts for later table pages.
            for(TableLayout.CellPosition cell:geometry.cells())
                if(cell.height()<18)
                    throw new IllegalArgumentException("Tablo hücresi taşıyor: "+cell.text());
            List<int[]> groups=TableLayout.pageGroups(geometry);
            // Cells are in row order. A row-height prefix and forward-only cell
            // cursor avoid rescanning the entire table for every page group.
            double[] rowTop=new double[geometry.rowHeights().size()+1];
            for(int i=0;i<geometry.rowHeights().size();i++)
                rowTop[i+1]=rowTop[i]+geometry.rowHeights().get(i);
            List<TableLayout.CellPosition> cells=geometry.cells();
            int cellCursor=0;
            if(x>left) newLine();
            for(int[] group:groups) {
                float height=(float)(rowTop[group[1]]-rowTop[group[0]]);
                if(height>top-bottom)throw new IllegalArgumentException("Rowspan grubu tek sayfaya sığmıyor");
                if(y-height<bottom)newPage();
                while(cellCursor<cells.size() && cells.get(cellCursor).row()<group[1]) {
                    TableLayout.CellPosition cell=cells.get(cellCursor++);
                    // The full export still rejects overflow and missing glyphs.
                    // Preview does not probe fonts belonging only to later pages.
                    if(!cell.text().isBlank()
                            && fonts.get(false).getStringWidth(cell.text())*10/1000f>cell.width()-8)
                        throw new IllegalArgumentException("Tablo hücresi taşıyor: "+cell.text());
                    float rowOffset=(float)(rowTop[cell.row()]-rowTop[group[0]]);
                    float cellTop=y-rowOffset;
                    stream.setStrokingColor(Color.BLACK);
                    stream.setLineWidth(.7f);
                    stream.addRect(left+cell.x(),cellTop-cell.height(),cell.width(),cell.height());
                    stream.stroke();
                    if(!cell.text().isBlank()) {
                        stream.beginText();
                        stream.setNonStrokingColor(Color.BLACK);
                        stream.setFont(fonts.get(false),10);
                        stream.newLineAtOffset(left+cell.x()+4,cellTop-14);
                        stream.showText(cell.text());
                        stream.endText();
                    }
                }
                y-=height;
            }
            x=left;
            y-=8;
            lineHeight=17;
        }
        void image(DocumentElements.ImageBlock block) throws Exception {
            if(block.width()>right-left || block.height()>top-bottom)
                throw new IllegalArgumentException("PNG sayfaya sığmıyor");
            // Position the image before decoding its pixels. A first-page
            // thumbnail stops at the page break, so images that occur only on
            // later pages must not be decoded or embedded during preview.
            if(x>left)newLine();
            if(y-block.height()<bottom)newPage();
            var image=ImageIO.read(new ByteArrayInputStream(block.png()));
            if(image==null)throw new IllegalArgumentException("Geçersiz PNG");
            PDImageXObject xobject=LosslessFactory.createFromImage(pdf,image);
            stream.drawImage(xobject,left,y-block.height(),block.width(),block.height());
            y-=block.height()+8;
            x=left;
            lineHeight=17;
        }
        @Override public void close() throws IOException {
            if (stream != null) { stream.close(); stream = null; }
        }
    }
}
