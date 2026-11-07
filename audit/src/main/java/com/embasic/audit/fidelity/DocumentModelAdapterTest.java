package com.embasic.audit.fidelity;
import java.awt.Color;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.nio.file.*;
import java.util.*;
import static com.embasic.audit.fidelity.DocumentModelAdapter.*;
/** Run as a standalone synthetic smoke test; no third-party source copied. */
public final class DocumentModelAdapterTest {
  private static int checks;
  private static void ok(boolean test, String message) {
    if (!test) throw new AssertionError(message);
    checks++;
  }
  private static void rejects(Runnable action) {
    try { action.run(); throw new AssertionError("Expected rejection"); }
    catch (IllegalArgumentException expected) { checks++; }
  }
  public static void main(String[] args) throws Exception {
    FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
    TextRun first = new TextRun("İstanbul ğüşöçı", 48, 90, 12, false, Color.BLACK);
    ok(textBounds(first, frc).getWidth() > 0, "glyph width");
    ok(font(first).getSize2D() == 12f, "font size");
    ok(font(new TextRun("bold", 0, 20, 16, true, Color.RED)).isBold(), "bold");
    ok(lineHeight(font(first), frc) > 12, "line metric");
    TableRow row = new TableRow(List.of(new Cell("A",110),new Cell("B",145)),48,125,32);
    var rectangles = tableGeometry(row);
    ok(rectangles.size() == 2, "table cells");
    ok(rectangles.get(0).x == 48 && rectangles.get(1).x == 158, "table positions");
    ok(rectangles.get(1).width == 145, "table widths");
    rejects(() -> new TextRun("x", Float.NaN, 20,12,false,Color.BLACK));
    rejects(() -> new TextRun("x",20,20,0,false,Color.BLACK));
    rejects(() -> tableGeometry(new TableRow(List.of(new Cell("wide",600)),10,10,20)));
    DocumentModel model = new DocumentModel(List.of(
        new Page(List.of(first),List.of(row)),
        new Page(List.of(new TextRun("Page two",48,90,11,false,Color.BLACK)),List.of())));
    var layout = adapt(model);
    ok(layout.getNumberOfPages() == 2, "page count");
    ok(layout.getPageFormat(0).getWidth() > 595, "page width");
    rejects(() -> new DocumentModel(List.of()));
    Path target = Files.createTempFile("embasic-fidelity-", ".pdf");
    Files.delete(target);
    try {
      Path fonts = Path.of("/usr/share/fonts/truetype/dejavu");
      PageLayoutProbe.writePdf(layout,target,fonts.resolve("DejaVuSans.ttf"),fonts.resolve("DejaVuSans-Bold.ttf"));
      ok(Files.size(target) > 1000, "PDF creation");
    } finally { Files.deleteIfExists(target); }
    System.out.println("PASS: " + checks + " synthetic adapter checks");
  }
}
