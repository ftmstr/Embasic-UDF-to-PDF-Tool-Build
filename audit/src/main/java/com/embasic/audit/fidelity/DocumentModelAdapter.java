package com.embasic.audit.fidelity;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.Printable;
import java.util.*;
/** Small, independent synthetic document model; NOT a UDF decoder. */
public final class DocumentModelAdapter {
  private DocumentModelAdapter() {}
  public record TextRun(String value, float x, float baseline, float size, boolean bold, Color color) {
    public TextRun {
      if (value == null || color == null || !Float.isFinite(x) || !Float.isFinite(baseline)
          || !Float.isFinite(size) || size <= 0 || size > 256) throw new IllegalArgumentException("Invalid text");
    }
  }
  public record Cell(String text, float width) {
    public Cell {
      if (text == null || !Float.isFinite(width) || width <= 0) throw new IllegalArgumentException("Invalid cell");
    }
  }
  public record TableRow(java.util.List<Cell> cells, float x, float y, float height) {
    public TableRow {
      cells = java.util.List.copyOf(cells);
      if (cells.isEmpty() || !Float.isFinite(x) || !Float.isFinite(y)
          || !Float.isFinite(height) || height <= 0) throw new IllegalArgumentException("Invalid row");
    }
  }
  public record Page(java.util.List<TextRun> runs, java.util.List<TableRow> rows) {
    public Page { runs = java.util.List.copyOf(runs); rows = java.util.List.copyOf(rows); }
  }
  public record DocumentModel(java.util.List<Page> pages) {
    public DocumentModel {
      pages = java.util.List.copyOf(pages);
      if (pages.isEmpty()) throw new IllegalArgumentException("Empty document");
    }
  }
  public static Font font(TextRun run) {
    return new Font("DejaVu Sans", run.bold() ? Font.BOLD : Font.PLAIN, 1).deriveFont(run.size());
  }
  public static Rectangle2D textBounds(TextRun run, FontRenderContext frc) {
    return new TextLayout(run.value().isEmpty() ? " " : run.value(), font(run), frc).getBounds();
  }
  public static float lineHeight(Font font, FontRenderContext frc) {
    TextLayout layout = new TextLayout("AgĞÜ", font, frc);
    return layout.getAscent() + layout.getDescent() + layout.getLeading();
  }
  public static java.util.List<Rectangle2D.Float> tableGeometry(TableRow row) {
    java.util.List<Rectangle2D.Float> bounds = new ArrayList<>();
    float x = row.x();
    for (Cell cell : row.cells()) {
      if (x + cell.width() > PageLayoutProbe.WIDTH) throw new IllegalArgumentException("Table exceeds page");
      bounds.add(new Rectangle2D.Float(x, row.y(), cell.width(), row.height()));
      x += cell.width();
    }
    return java.util.List.copyOf(bounds);
  }
  public static PageLayoutProbe.PageLayout adapt(DocumentModel model) {
    java.util.List<Printable> pages = new ArrayList<>();
    for (Page page : model.pages()) {
      pages.add((graphics, format, index) -> {
        Graphics2D g = (Graphics2D)graphics;
        for (TextRun run : page.runs()) {
          g.setFont(font(run)); g.setColor(run.color()); g.drawString(run.value(), run.x(), run.baseline());
        }
        for (TableRow row : page.rows()) {
          java.util.List<Rectangle2D.Float> rectangles = tableGeometry(row);
          for (int j = 0; j < rectangles.size(); j++) {
            Rectangle2D.Float cell = rectangles.get(j);
            g.setColor(Color.BLACK); g.draw(cell);
            g.setFont(new Font("DejaVu Sans", Font.PLAIN, 10));
            g.drawString(row.cells().get(j).text(), cell.x + 3, cell.y + Math.min(cell.height - 3, 13));
          }
        }
        return Printable.PAGE_EXISTS;
      });
    }
    return new PageLayoutProbe.PageLayout(pages);
  }
}
