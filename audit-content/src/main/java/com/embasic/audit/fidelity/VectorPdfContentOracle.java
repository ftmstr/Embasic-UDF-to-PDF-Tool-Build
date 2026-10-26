package com.embasic.audit.fidelity;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Independent PDFBox inspection of generated, fictional Java2D vector PDFs only. */
public final class VectorPdfContentOracle {
  private VectorPdfContentOracle() {}
  private static int checks;
  private static void require(boolean valid, String message) {
    if (!valid) throw new AssertionError(message);
    checks++;
  }
  private static int[] countFonts(PDResources resources, Set<PDResources> visited) throws Exception {
    if (resources == null || !visited.add(resources)) return new int[]{0,0};
    int total=0,embedded=0;
    for (var name : resources.getFontNames()) {
      PDFont font = resources.getFont(name);
      if (font != null) { total++; if (font.isEmbedded()) embedded++; }
    }
    for (var name : resources.getXObjectNames()) {
      PDXObject object = resources.getXObject(name);
      if (object instanceof PDFormXObject form) {
        int[] nested=countFonts(form.getResources(),visited);
        total+=nested[0]; embedded+=nested[1];
      }
    }
    return new int[]{total,embedded};
  }
  private static boolean allFontsEmbedded(PDDocument pdf) throws Exception {
    Set<PDResources> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    int count=0, embedded=0;
    for (PDPage page : pdf.getPages()) {
      int[] value=countFonts(page.getResources(),visited);
      count+=value[0];embedded+=value[1];
    }
    return count>0 && count==embedded;
  }
  /** PDF coordinates: glyph positions must be finite and inside their A4 page. */
  private static int inspectGlyphPositions(PDDocument pdf) throws Exception {
    final int[] glyphs={0};
    PDFTextStripper inspector=new PDFTextStripper(){
      @Override protected void processTextPosition(TextPosition text) {
        float x=text.getXDirAdj(),y=text.getYDirAdj();
        if (!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0
            ||x>PageLayoutProbe.WIDTH+2||y>PageLayoutProbe.HEIGHT+2)
          throw new IllegalStateException("Out-of-page glyph coordinate");
        glyphs[0]++;
        super.processTextPosition(text);
      }
    };
    inspector.getText(pdf);
    return glyphs[0];
  }
  private static Path save(PageLayoutProbe.PageLayout pages) throws Exception {
    Path file = Files.createTempFile("embasic-oracle-", ".pdf");
    Files.delete(file);
    Path fonts = Path.of("/usr/share/fonts/truetype/dejavu");
    PageLayoutProbe.writePdf(pages, file, fonts.resolve("DejaVuSans.ttf"), fonts.resolve("DejaVuSans-Bold.ttf"));
    return file;
  }
  public static void main(String[] args) throws Exception {
    var style = new UdfXmlModelAdapter.Style("DejaVu Sans", 13, false, Color.BLACK);
    var paragraph = new UdfXmlModelAdapter.Paragraph(List.of(new UdfXmlModelAdapter.Run("İzmir ğüşöçı", style)));
    var model = new UnifiedDocumentPipeline.Model("1.0", List.of(
        new UnifiedDocumentPipeline.ParagraphBlock(paragraph),
        new UnifiedDocumentPipeline.TableBlock(new SyntheticXmlTableBridge.Parsed(
            List.of(new TableGeometry.Row(List.of(new TableGeometry.Cell(2, 1, "TABLO")), 30)),
            new float[]{100, 120}))));
    var result = UnifiedDocumentPipeline.render(model);
    Path file = save(result.pages());
    try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
      require(pdf.getNumberOfPages() == 1, "single page");
      String text = new PDFTextStripper().getText(pdf);
      require(text.contains("İzmir"), "Turkish word extraction");
      require(text.contains("ğüşöçı"), "Turkish diacritic extraction");
      require(inspectGlyphPositions(pdf)>5, "glyph coordinates valid");
      require(text.contains("TABLO"), "table text extraction");
      require(allFontsEmbedded(pdf), "embedded font");
      BufferedImage rendered = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
      require(rendered.getWidth() > 590 && rendered.getHeight() > 830, "A4 dimensions");
      int dark = 0;
      for (int yy = 40; yy < 160 && yy < rendered.getHeight(); yy++)
        for (int xx = 40; xx < 290 && xx < rendered.getWidth(); xx++)
          if ((rendered.getRGB(xx, yy) & 0xffffff) != 0xffffff) dark++;
      require(dark > 100, "table and text must render nonwhite pixels");
    } finally { Files.deleteIfExists(file); }

    List<TableGeometry.Row> rows = new ArrayList<>();
    for (int i = 0; i < 60; i++)
      rows.add(new TableGeometry.Row(List.of(new TableGeometry.Cell(2,1, "Row"+i)), 30));
    var multi = TablePageRenderer.synthetic(rows, new float[]{100,120});
    Path multipage = save(multi);
    try (PDDocument pdf = Loader.loadPDF(multipage.toFile())) {
      require(pdf.getNumberOfPages() > 1, "multiple pages");
      String extracted = new PDFTextStripper().getText(pdf);
      require(extracted.indexOf("Row0") >= 0, "first row extracted");
      require(extracted.indexOf("Row59") > extracted.indexOf("Row0"), "last row follows first");
      require(inspectGlyphPositions(pdf)>100, "multipage glyph positions");
      require(containsEmbeddedFont(pdf), "embedded font in multi-page");
    } finally { Files.deleteIfExists(multipage); }
    System.out.println("PASS: " + checks + " independent PDFBox content checks");
  }
}
