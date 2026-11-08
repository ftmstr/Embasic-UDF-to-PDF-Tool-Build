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
    /**
     * Resolve a drop target by logical geometry, even when no Swing card has
     * been instantiated for the destination. -1 indicates an outside drop.
     */
    public int indexAt(int x, int y) {
        if (entries==0 || x<PADDING || y<PADDING || x>=width ||
                y>=preferredSize().height) return -1;
        int row=(y-PADDING)/rowStride;
        int col=(x-PADDING)/(card.cardWidth()+ThumbnailSizing.GAP);
        if (row>=rows() || col>=columns) return -1;
        long index=(long)row*columns+col;
        // A partial final row has no cards after the last real entry.
        if (index>=entries) return -1;
        // Empty margins and inter-card gaps are not reorder targets.
        // Clamping them to a nearby card could silently move the wrong UDF.
        return bounds((int)index).contains(x,y) ? (int)index : -1;
    }

    /**
     * Keep a logical card row at the top of the viewport when zoom changes the
     * number of columns. A pixel-only scroll position would jump to different
     * UDFs after the thumbnail size changes.
     */
    public int topRowAnchor(int viewportY) {
        if (entries == 0) return -1;
        long relative = (long) viewportY - PADDING;
        int row = (int) Math.min(rows() - 1,
                Math.max(0L, Math.floorDiv(relative, rowStride)));
        return row * columns;
    }

    public int topRowOffset(int viewportY) {
        int anchor = topRowAnchor(viewportY);
        if (anchor < 0) return 0;
        return Math.max(0, Math.min(rowStride - 1, viewportY - bounds(anchor).y));
    }

    public int anchoredScrollY(int anchor, int offset, int viewportHeight) {
        if (entries == 0) return 0;
        int index = Math.max(0, Math.min(entries - 1, anchor));
        long requested = (long) bounds(index).y
                + Math.max(0, Math.min(rowStride - 1, offset));
        int maxScroll = Math.max(0, preferredSize().height - Math.max(1, viewportHeight));
        return (int) Math.max(0L, Math.min((long) maxScroll, requested));
    }

    /**
     * Preserve the first visible logical UDF row when a responsive window
     * resize changes the number of grid columns. Pixel-only Y retention can
     * jump to unrelated documents even though thumbnail zoom is unchanged.
     * Clamp near the final row so a shorter layout never overscrolls.
     */
    int reflowScrollY(VirtualGridLayout next, int viewportY, int viewportHeight) {
        if (next == null) throw new IllegalArgumentException("Yeni grid geometrisi eksik");
        return next.anchoredScrollY(topRowAnchor(viewportY),
                topRowOffset(viewportY), viewportHeight);
    }

    /** Horizontal midpoint used for before/after Explorer-style drops. */
    public boolean insertAfter(int index, int x) {
        Rectangle rectangle=bounds(index);
        return x>=rectangle.x+rectangle.width/2;
    }

    /**
     * Nonblocking scroll request in pixels; the Swing layer owns the timer.
     * The pointer position is viewport-local, not document-local.
     */
    public static int edgeScrollDelta(int pointerY, int viewportHeight) {
        if (viewportHeight<=0 || pointerY<0 || pointerY>=viewportHeight) return 0;
        // Keep top and bottom hot zones disjoint in very short responsive windows.
        // Otherwise a pointer at the bottom can incorrectly request upward scroll.
        int edge=Math.min(viewportHeight/2,Math.min(48,Math.max(12,viewportHeight/4)));
        if (edge == 0) return 0;
        if (pointerY<edge) return -Math.min(36,Math.max(12,edge-pointerY));
        if (pointerY>=viewportHeight-edge)
            return Math.min(36,Math.max(12,pointerY-(viewportHeight-edge)));
        return 0;
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
