package com.embasic.audit.fidelity;
import java.awt.*;
import java.awt.print.Printable;
import java.util.*;
/** Synthetic table drawing bridge. Independent of the proprietary UYAP implementation. */
public final class TablePageRenderer {
  private TablePageRenderer() {}
  public static PageLayoutProbe.PageLayout render(TableGeometry.Result geometry,float pageHeight) {
    if(geometry==null||!Float.isFinite(pageHeight)||pageHeight<=0)throw new IllegalArgumentException("Invalid page");
    java.util.List<Printable> pages=new ArrayList<>();
    for(int page=0;page<geometry.pages();page++){
      final int current=page;
      int first=geometry.pageStarts().get(page);
      int end=page+1<geometry.pages()?geometry.pageStarts().get(page+1):Integer.MAX_VALUE;
      java.util.List<TableGeometry.Placement> cells=new ArrayList<>();
      for(var cell:geometry.cells())if(cell.row()>=first&&cell.row()<end){
        // Geometry guarantees a span cannot cross page boundary.
        float localY=cell.y();
        if(first>0){
          float min=Float.POSITIVE_INFINITY;
          for(var item:geometry.cells())if(item.row()==first)min=Math.min(min,item.y());
          if(Float.isFinite(min))localY=cell.y()-min+48;
        }
        if(localY<0||localY+cell.height()>pageHeight)
          throw new IllegalArgumentException("Cell outside printable page");
        cells.add(new TableGeometry.Placement(cell.row(),cell.column(),cell.colspan(),cell.rowspan(),
           cell.x(),localY,cell.width(),cell.height(),cell.text()));
      }
      pages.add((graphics,format,index)->{
        Graphics2D g=(Graphics2D)graphics;
        g.setColor(Color.BLACK);g.setStroke(new BasicStroke(0.8f));
        g.setFont(new Font("DejaVu Sans",Font.PLAIN,10));
        for(var cell:cells){
          g.draw(new java.awt.geom.Rectangle2D.Float(cell.x(),cell.y(),cell.width(),cell.height()));
          // Fail closed rather than clip or silently omit text.
          FontMetrics metrics=g.getFontMetrics();
          if(metrics.stringWidth(cell.text())>cell.width()-6||metrics.getHeight()>cell.height()-4)
            throw new IllegalArgumentException("Cell text exceeds geometry");
          g.drawString(cell.text(),cell.x()+3,cell.y()+3+metrics.getAscent());
        }
        return Printable.PAGE_EXISTS;
      });
    }
    return new PageLayoutProbe.PageLayout(pages);
  }
  public static PageLayoutProbe.PageLayout synthetic(java.util.List<TableGeometry.Row> rows,float[] columns) {
    return render(TableGeometry.layout(rows,columns,48,48,PageLayoutProbe.HEIGHT-96),PageLayoutProbe.HEIGHT-48);
  }
}
