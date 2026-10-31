package com.embasic.publicapp;

import java.awt.Rectangle;

/**
 * Paint-only drop indicator. It is never a Swing child and cannot intercept
 * mouse events or change the logical document order.
 */
public final class GridDropIndicator {
    private GridDropIndicator() {}

    /** Returns an indicator inside the target tile; invalid indexes are hidden. */
    public static Rectangle bounds(VirtualGridLayout geometry, int index, boolean after) {
        if (geometry == null || index < 0 || index >= geometry.count()) return null;
        Rectangle tile=geometry.bounds(index);
        if(index==geometry.count()-1) {
            // Trailing + tile represents "move to end", not a before/after split.
            return new Rectangle(tile.x+4, tile.y+tile.height-6,
                    Math.max(1,tile.width-8), 4);
        }
        int x=after ? tile.x+tile.width-4 : tile.x;
        return new Rectangle(x,tile.y+4,4,Math.max(1,tile.height-8));
    }

    public static int targetAt(VirtualGridLayout geometry, int x, int y) {
        return geometry==null ? -1 : geometry.indexAt(x,y);
    }
}
