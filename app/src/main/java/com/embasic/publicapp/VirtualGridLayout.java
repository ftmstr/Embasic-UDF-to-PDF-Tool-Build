package com.embasic.publicapp;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Index-based viewport geometry for a virtualized Swing thumbnail grid.
 * No components or source files are created by this class.
 */
public final class VirtualGridLayout {
    private static final int PADDING = 12;
    private static final int BUFFER_ROWS = 1;
    private final int entries, width, columns;
    private final ThumbnailSizing.Size card;
    private final int rowStride;

    public VirtualGridLayout(int entryCount, int viewportWidth, int cardWidth) {
        if (entryCount < 0 || entryCount > 1_000_000)
            throw new IllegalArgumentException("Grid giriş sayısı geçersiz");
        entries = entryCount;
        width = Math.max(1, viewportWidth);
        card = ThumbnailSizing.at(cardWidth);
        columns = ThumbnailSizing.columns(width,card.cardWidth());
        rowStride = card.cardHeight() + ThumbnailSizing.GAP;
    }
    public int columns() { return columns; }
    public int count() { return entries; }
    public int rows() { return (entries + columns - 1) / columns; }
    public Dimension preferredSize() {
        long height = 2L * PADDING + (long) rows() * rowStride;
        return new Dimension(width,(int)Math.min(Integer.MAX_VALUE,Math.max(1,height)));
    }
    public Rectangle bounds(int index) {
        if(index < 0 || index >= entries) throw new IndexOutOfBoundsException(index);
        int col=index%columns, row=index/columns;
        long x=PADDING+(long)col*(card.cardWidth()+ThumbnailSizing.GAP);
        long y=PADDING+(long)row*rowStride;
        return new Rectangle((int)x,(int)Math.min(Integer.MAX_VALUE,y),
                card.cardWidth(),card.cardHeight());
    }
    public record Visible(int startInclusive, int endExclusive) {
        public Visible {
            if (startInclusive < 0 || endExclusive < startInclusive)
                throw new IllegalArgumentException("Geçersiz görünür aralık");
        }
        public int count() { return endExclusive - startInclusive; }
    }
    /** One extra row on each side avoids flicker while scrolling. */
    public Visible visible(Rectangle viewport) {
        if(entries==0) return new Visible(0,0);
        if (viewport == null || viewport.height <= 0 || viewport.width <= 0)
            return new Visible(0,Math.min(entries,columns*2));
        int firstRow=Math.max(0,viewport.y/rowStride-BUFFER_ROWS);
        long bottom=(long)Math.max(0,viewport.y)+(long)viewport.height;
        int lastRow=(int)Math.min(rows()-1, Math.max(0,bottom/rowStride)+BUFFER_ROWS);
        if(firstRow>lastRow) return new Visible(0,0);
        int start=Math.min(entries,firstRow*columns);
        int end=(int)Math.min(entries,(long)(lastRow+1)*columns);
        return new Visible(start,Math.max(start,end));
    }
}
