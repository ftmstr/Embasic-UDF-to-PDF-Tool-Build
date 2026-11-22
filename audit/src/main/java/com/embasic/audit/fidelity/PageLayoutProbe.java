package com.embasic.audit.fidelity;
import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import java.awt.*;
import java.awt.print.*;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
/** Independent synthetic Java2D proof; no UYAP code or documents. */
public final class PageLayoutProbe {
  public static final float WIDTH = 595.28f, HEIGHT = 841.89f;
  private PageLayoutProbe() {}
  public static final class PageLayout implements Pageable {
    private final java.util.List<Printable> pages;
    public PageLayout(java.util.List<Printable> pages) {
      if (pages == null || pages.isEmpty()) throw new IllegalArgumentException("No pages");
      this.pages = java.util.List.copyOf(pages);
    }
    public int getNumberOfPages() { return pages.size(); }
    public Printable getPrintable(int index) { return pages.get(index); }
    public PageFormat getPageFormat(int index) {
      getPrintable(index);
      Paper p = new Paper();
      p.setSize(WIDTH, HEIGHT); p.setImageableArea(0, 0, WIDTH, HEIGHT);
      PageFormat f = new PageFormat(); f.setPaper(p); return f;
    }
  }
  public static FontMapper fontMapper(Path regular, Path bold) {
    return new FontMapper() {
      public BaseFont awtToPdf(Font font) {
        try {
          Path path = font.isBold() ? bold : regular;
          if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Missing font: " + path);
          return BaseFont.createFont(path.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) { throw new IllegalStateException("Font embedding failed", e); }
      }
      public Font pdfToAwt(BaseFont font, int size) { return new Font("DejaVu Sans", Font.PLAIN, size); }
    };
  }
  public static void writePdf(Pageable layout, Path output, Path regular, Path bold) throws Exception {
    if (layout == null || output == null || Files.exists(output)) throw new IllegalArgumentException("Output exists or input missing");
    try (OutputStream stream = Files.newOutputStream(output)) {
      Document document = new Document(new Rectangle(WIDTH, HEIGHT), 0, 0, 0, 0);
      PdfWriter writer = PdfWriter.getInstance(document, stream);
      document.open();
      try {
        for (int page = 0; page < layout.getNumberOfPages(); page++) {
          if (page > 0) document.newPage();
          PageFormat format = layout.getPageFormat(page);
          PdfContentByte cb = writer.getDirectContent();
          PdfTemplate template = cb.createTemplate((float)format.getWidth(), (float)format.getHeight());
          Graphics2D graphics = template.createGraphics((float)format.getWidth(), (float)format.getHeight(), fontMapper(regular, bold));
          try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (layout.getPrintable(page).print(graphics, format, page) != Printable.PAGE_EXISTS)
              throw new IllegalStateException("Missing page " + page);
          } finally { graphics.dispose(); }
          cb.addTemplate(template, 0, 0);
        }
      } finally { document.close(); }
    } catch (Exception e) { Files.deleteIfExists(output); throw e; }
  }
  public static PageLayout synthetic() {
    java.util.List<Printable> pages = new ArrayList<>();
    for (int page = 0; page < 2; page++) {
      final int n = page;
      pages.add((graphics, format, index) -> {
        Graphics2D g = (Graphics2D)graphics;
        g.setColor(Color.WHITE); g.fillRect(0, 0, 596, 842);
        g.setColor(Color.BLACK); g.setFont(new Font("DejaVu Sans", Font.PLAIN, 13));
        g.drawString(n == 0 ? "Türkçe: İ ğ ş ü ö ç" : "İkinci sayfa", 48, 80);
        g.drawRect(48, 100, 440, 40);
        return Printable.PAGE_EXISTS;
      });
    }
    return new PageLayout(pages);
  }
}
