package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.List;

/** Deterministic point-based geometry for a strictly synthetic flat table XML subset. */
public final class TableLayout {
    private TableLayout() {}
    public record CellPosition(int row, int column, int colspan, int rowspan, String text,
                               float x, float top, float width, float height) {}
    public record Geometry(List<CellPosition> cells, List<Float> rowHeights, List<Float> widths) {
        public Geometry { cells=List.copyOf(cells); rowHeights=List.copyOf(rowHeights); widths=List.copyOf(widths); }
    }
    public static Geometry calculate(DocumentElements.TableBlock table) {
        int columns=table.widths().size(), rowCount=table.rows().size();
        int[] occupiedUntil=new int[columns];
        List<CellPosition> result=new ArrayList<>();
        float y=0;
        for(int r=0;r<rowCount;r++) {
            DocumentElements.Row row=table.rows().get(r);
            int cursor=0;
            for(DocumentElements.Cell cell:row.cells()) {
                while(cursor<columns && occupiedUntil[cursor]>r) cursor++;
                if(cursor+cell.colspan()>columns || r+cell.rowspan()>rowCount)
                    throw new IllegalArgumentException("Tablo hücresi ızgara dışına taşıyor");
                float x=0,w=0,h=0;
                for(int c=0;c<cursor;c++)x+=table.widths().get(c);
                for(int c=cursor;c<cursor+cell.colspan();c++) {
                    if(occupiedUntil[c]>r) throw new IllegalArgumentException("Birleşik tablo hücresi çakışıyor");
                    w+=table.widths().get(c);
                    occupiedUntil[c]=r+cell.rowspan();
                }
                for(int k=r;k<r+cell.rowspan();k++)h+=table.rows().get(k).height();
                result.add(new CellPosition(r,cursor,cell.colspan(),cell.rowspan(),
                        cell.text(),x,y,w,h));
                cursor+=cell.colspan();
            }
            for(int c=0;c<columns;c++)
                if(occupiedUntil[c]<=r)throw new IllegalArgumentException("Eksik tablo sütunu, satır: "+r);
            y+=row.height();
        }
        return new Geometry(result,table.rows().stream().map(DocumentElements.Row::height).toList(),table.widths());
    }
    /** Rowspan-connected rows must remain together on one PDF page. */
    public static List<int[]> pageGroups(Geometry geometry) {
        List<int[]> groups=new ArrayList<>();
        int r=0,count=geometry.rowHeights().size();
        while(r<count) {
            int end=r+1;
            for(int cursor=r;cursor<end;cursor++)
                for(CellPosition cell:geometry.cells())
                    if(cell.row()==cursor)end=Math.max(end,cursor+cell.rowspan());
            if(end>count)throw new IllegalArgumentException("Rowspan sınır dışı");
            groups.add(new int[]{r,end});
            r=end;
        }
        return groups;
    }
}
