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

    public static PDDocument render(List<UdfReader.DocumentSpec> docs) throws Exception {
        if (docs == null || docs.isEmpty()) throw new IllegalArgumentException("PDF için en az bir UDF gerekli");
        PDDocument pdf = new PDDocument();
        try {
            PDType0Font regular = PDType0Font.load(pdf, systemFont(false).toFile());
            PDType0Font bold = PDType0Font.load(pdf, systemFont(true).toFile());
            for (UdfReader.DocumentSpec document : docs) {
                try (Layout layout = new Layout(pdf, regular, bold, document.page())) {
                    layout.newPage();
                    for (DocumentElements.Block block : document.blocks()) {
                        if (block instanceof DocumentElements.ParagraphBlock p) {
                            for (UdfReader.Run run : p.paragraph().runs()) layout.add(run);
                            layout.endParagraph();
                        } else if (block instanceof DocumentElements.TableBlock table) {
                            layout.table(table);
                        } else if (block instanceof DocumentElements.ImageBlock image) {
                            layout.image(image);
                        } else throw new IllegalArgumentException("Desteklenmeyen belge bloğu");
                    }
                }
            }
            if (pdf.getNumberOfPages() == 0) throw new IllegalStateException("PDF sayfası oluşturulamadı");
            return pdf;
        } catch (Exception e) {
            pdf.close();
            throw e;
        }
    }

    public static BufferedImage firstPage(UdfReader.DocumentSpec doc) throws Exception {
        try (PDDocument pdf = render(List.of(doc))) {
            return new PDFRenderer(pdf).renderImageWithDPI(0, 40);
        }
    }

    public static int save(List<UdfReader.DocumentSpec> docs, Path target, boolean overwrite) throws Exception {
        if (target == null || !target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new IllegalArgumentException("Çıktı dosyası .pdf uzantılı olmalıdır");
        Path abs = target.toAbsolutePath().normalize();
        Path folder = abs.getParent();
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Çıktı klasörü bulunamadı");
        if (Files.exists(abs) && !overwrite) throw new IllegalArgumentException("PDF zaten var, üzerine yazma onayı gerekli");
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
            try {
                Files.move(staging, abs, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Never delete an existing document before a validated replacement is available.
                if (Files.exists(abs)) {
                    Path backup = Files.createTempFile(folder, ".embasic-backup-", ".pdf");
                    Files.deleteIfExists(backup);
                    Files.move(abs, backup);
                    boolean replaced = false;
                    try {
                        Files.move(staging, abs);
                        replaced = true;
                    } finally {
                        if (replaced) Files.deleteIfExists(backup);
                        else Files.move(backup, abs, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.move(staging, abs);
                }
            }
            return pages;
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static final class Layout implements AutoCloseable {
        private final PDDocument pdf;
        private final PDType0Font regular, bold;
        private final DocumentElements.PageSpec page;
        private final float left, right, top, bottom;
        private PDPageContentStream stream;
        private float x, y, lineHeight = 17f;
        private Layout(PDDocument pdf, PDType0Font regular, PDType0Font bold,
                       DocumentElements.PageSpec page) {
            this.pdf = pdf; this.regular = regular; this.bold = bold; this.page = page;
            left = page.margin(); right = page.width() - page.margin();
            top = page.height() - page.margin(); bottom = page.margin();
        }
        void newPage() throws IOException {
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
            PDType0Font font = style.bold() ? bold : regular;
            float size = style.size();
            float length = width(text, font, size);
            if (length > right - left)
                throw new IllegalArgumentException("Sayfaya sığmayan kelime: " + text.substring(0, Math.min(30, text.length())));
            // Recompute the *new* line's height after wrapping; otherwise a
            // large-font run can overlap the following text after newLine() resets it.
            if (x + length > right) newLine();
            lineHeight = Math.max(lineHeight, size * 1.4f);
            if (y - size < bottom) newPage();
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
            float length = width(" ", style.bold() ? bold : regular, style.size()) * count;
            if (x + length > right) newLine();
            if (x > left) x += length;
        }
        void add(UdfReader.Run run) throws IOException {
            UdfReader.Style style = run.style();
            String text = run.text();
            StringBuilder token = new StringBuilder();
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '\r') continue;
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
            // Validate all cell text and vertical constraints before writing any cell.
            for(TableLayout.CellPosition cell:geometry.cells()) {
                if(cell.height()<18 || regular.getStringWidth(cell.text())*10/1000f>cell.width()-8)
                    throw new IllegalArgumentException("Tablo hücresi taşıyor: "+cell.text());
            }
            List<int[]> groups=TableLayout.pageGroups(geometry);
            if(x>left) newLine();
            for(int[] group:groups) {
                float height=0;
                for(int i=group[0];i<group[1];i++)height+=geometry.rowHeights().get(i);
                if(height>top-bottom)throw new IllegalArgumentException("Rowspan grubu tek sayfaya sığmıyor");
                if(y-height<bottom)newPage();
                for(TableLayout.CellPosition cell:geometry.cells()) {
                    if(cell.row()<group[0] || cell.row()>=group[1])continue;
                    float rowOffset=0;
                    for(int i=group[0];i<cell.row();i++)rowOffset+=geometry.rowHeights().get(i);
                    float cellTop=y-rowOffset;
                    stream.setStrokingColor(Color.BLACK);
                    stream.setLineWidth(.7f);
                    stream.addRect(left+cell.x(),cellTop-cell.height(),cell.width(),cell.height());
                    stream.stroke();
                    if(!cell.text().isEmpty()) {
                        stream.beginText();
                        stream.setNonStrokingColor(Color.BLACK);
                        stream.setFont(regular,10);
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
            var image=ImageIO.read(new ByteArrayInputStream(block.png()));
            if(image==null)throw new IllegalArgumentException("Geçersiz PNG");
            if(x>left)newLine();
            if(y-block.height()<bottom)newPage();
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
