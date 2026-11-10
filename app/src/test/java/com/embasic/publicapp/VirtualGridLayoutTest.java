package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

final class VirtualGridLayoutTest {
    @Test void viewportRendersOnlyNearbyRowsAtMaxImportSize() {
        for(int zoom:new int[]{132,188,272}) {
            var grid=new VirtualGridLayout(161,1000,zoom);
            for(int y:new int[]{0,500,2000,8000,20000}) {
                var visible=grid.visible(new Rectangle(0,y,1000,650));
                // A 650 px viewport may touch five partially visible rows plus a one-row buffer on each side.
                assertTrue(visible.count() <= grid.columns()*8,
                    "Too many Swing cards at "+zoom+": "+visible.count());
                assertTrue(visible.endExclusive() <= 161);
                assertEquals(0,visible.startInclusive()%grid.columns());
            }
            assertTrue(grid.preferredSize().height >= grid.bounds(160).y+grid.bounds(160).height);
        }
    }
    @Test void smoothScrollingHasNoMissingRowBetweenViewports() {
        var grid=new VirtualGridLayout(2500,1050,188);
        for(int y=0;y<grid.preferredSize().height;y+=95) {
            var visible=grid.visible(new Rectangle(0,y,1050,700));
            for(int i=0;i<grid.count();i++) {
                Rectangle card=grid.bounds(i);
                if(card.intersects(0,y,1050,700))
                    assertTrue(i>=visible.startInclusive() && i<visible.endExclusive(),
                            "Missing visible index "+i+" for scroll="+y);
            }
        }
    }
    @Test void geometryIsStableForSelectionReorderAndZoom() {
        var grid=new VirtualGridLayout(161,1000,188);
        assertEquals(0,grid.bounds(0).y-grid.bounds(1).y);
        assertTrue(grid.bounds(grid.columns()).y>grid.bounds(0).y);
        assertEquals(161,grid.count()); // 160 files plus the embedded add-file card
        var smaller=new VirtualGridLayout(161,1000,132);
        assertTrue(smaller.columns()>grid.columns());
        var larger=new VirtualGridLayout(161,1000,272);
        assertTrue(larger.columns()<grid.columns());

        // Zoom must retain the logical UDF near the top instead of keeping a
        // stale pixel offset, even when the new grid has fewer columns.
        int oldY = grid.bounds(80).y + 23;
        int anchor = grid.topRowAnchor(oldY);
        assertEquals((80 / grid.columns()) * grid.columns(), anchor);
        assertEquals(23, grid.topRowOffset(oldY));
        for (var resized : new VirtualGridLayout[]{smaller, larger}) {
            int y = resized.anchoredScrollY(anchor, grid.topRowOffset(oldY), 650);
            int newRow = resized.topRowAnchor(y);
            assertTrue(newRow <= anchor && anchor < newRow + resized.columns(),
                    "Zoom must keep the same logical UDF row visible");
            assertEquals(23, resized.topRowOffset(y));
        }
        // Resizing the window at fixed zoom must keep the same logical
        // document near the viewport top, just like changing thumbnail zoom.
        // A 160-file queue otherwise jumps when the column count changes.
        for (int width : new int[]{680, 1430}) {
            var reflowed = new VirtualGridLayout(161, width, 188);
            assertNotEquals(grid.columns(), reflowed.columns());
            int y = grid.reflowScrollY(reflowed, oldY, 650);
            int row = reflowed.topRowAnchor(y);
            assertTrue(row <= anchor && anchor < row + reflowed.columns(),
                    "Window resize must retain the current logical UDF");
            assertEquals(23, reflowed.topRowOffset(y));
        }
        var shortGrid = new VirtualGridLayout(2, 680, 188);
        assertEquals(0, grid.reflowScrollY(shortGrid, oldY, 650),
                "Shrinking the content must clamp the scroll position");
        assertThrows(IllegalArgumentException.class,
                () -> grid.reflowScrollY(null, oldY, 650));
        assertEquals(0, grid.topRowAnchor(0));
        assertEquals(0, grid.topRowOffset(0));
        // Extreme negative scroll coordinates must clamp to zero, not wrap
        // the int subtraction into a positive in-row offset.
        assertEquals(0, grid.topRowOffset(Integer.MIN_VALUE));
        assertEquals(0, grid.topRowAnchor(Integer.MIN_VALUE));
        int gap = grid.bounds(80).y + grid.bounds(80).height + 2;
        assertEquals(anchor, grid.topRowAnchor(gap),
                "Vertical card gaps belong to the preceding logical row");
        assertEquals(0, grid.anchoredScrollY(160, 0, Integer.MAX_VALUE),
                "A viewport taller than the grid must never scroll past its content");
    }
    @Test void extremeViewportWidthDoesNotOverflowRowCountOrLoseCards() {
        // A maximized/invalid layout width can yield millions of logical columns.
        // The former int addition entries + columns - 1 wrapped negative.
        var grid = new VirtualGridLayout(1_000_000, Integer.MAX_VALUE, 132);
        assertEquals(1, grid.rows());
        assertEquals(1_000_000, grid.count());
        assertTrue(grid.preferredSize().height > 0);
        assertEquals(0, grid.topRowAnchor(0));
        assertEquals(0, grid.visible(new Rectangle(0, 0, 1200, 650)).startInclusive());
        var narrow = grid.visible(new Rectangle(0, 0, 1200, 650));
        assertTrue(narrow.endExclusive() < 20,
                "A narrow viewport must not instantiate one million offscreen cards");
        for (int i = 0; i < 1_000_000; i += 997) {
            Rectangle card = grid.bounds(i);
            if (card.intersects(0, 0, 1200, 650))
                assertTrue(i >= narrow.startInclusive() && i < narrow.endExclusive());
        }
        var shifted = grid.visible(new Rectangle(100_000, 0, 1200, 650));
        assertTrue(shifted.count() < 20);
        int visibleIndex = grid.indexAt(100_020, 30);
        if (visibleIndex >= 0)
            assertTrue(visibleIndex >= shifted.startInclusive()
                    && visibleIndex < shifted.endExclusive());
    }

    @Test void extremeHorizontalCoordinatesNeverWrapIntoNegativeCardBounds() {
        var grid = new VirtualGridLayout(1_000_000, Integer.MAX_VALUE, 132);
        assertEquals(1, grid.rows());
        Rectangle farRight = grid.bounds(999_999);
        assertTrue(farRight.x >= 0,
                "Offscreen card coordinates must not wrap into the visible left edge");
        assertEquals(Integer.MAX_VALUE, farRight.x);
        assertEquals(-1, grid.indexAt(1, farRight.y + farRight.height / 2),
                "A far-right card must never become a left-side drop target");
    }

    @Test void emptyAndInvalidViewportsAreSafe() {
        var grid=new VirtualGridLayout(0,0,188);
        assertEquals(0,grid.visible(new Rectangle(0,0,0,0)).count());
        assertTrue(grid.preferredSize().height>0);
        assertThrows(IllegalArgumentException.class,()->new VirtualGridLayout(-1,400,188));
        assertThrows(IndexOutOfBoundsException.class,()->grid.bounds(0));
        var nonempty=new VirtualGridLayout(7,1000,188);
        assertEquals(0,nonempty.visible(new Rectangle(0,0,0,0)).count());
        assertEquals(0,nonempty.visible(new Rectangle(0,0,1000,0)).count());
        assertEquals(0,nonempty.visible(new Rectangle(0,0,0,650)).count());
        assertEquals(0,nonempty.visible(null).count());
        assertTrue(nonempty.visible(new Rectangle(0,0,1000,650)).count()>0);
    }
    @Test void offGridViewportsNeverMaterializeUnrelatedCards() {
        var grid = new VirtualGridLayout(161,1000,188);
        int bottom = grid.preferredSize().height;
        assertEquals(0,grid.visible(new Rectangle(0,-10000,1000,650)).count());
        assertEquals(0,grid.visible(new Rectangle(0,bottom,1000,650)).count());
        assertEquals(0,grid.visible(new Rectangle(-2000,0,650,650)).count());
        assertEquals(0,grid.visible(new Rectangle(1000,0,650,650)).count());
        assertTrue(grid.visible(new Rectangle(0,-100,1000,650)).count()>0,
                "A viewport partly overlapping the first row must still render it");
        assertTrue(grid.visible(new Rectangle(0,bottom-100,1000,650)).count()>0,
                "A viewport partly overlapping the last row must still render it");
        assertEquals(0,grid.visible(new Rectangle(0,Integer.MAX_VALUE,1000,650)).count());
    }

    @Test void targetResolutionAcrossScrollResizeAndZoomNeedsNoCardComponents() {
        for (int width : new int[]{680,1000,1430}) {
            for (int zoom : new int[]{132,188,272}) {
                var grid=new VirtualGridLayout(161,width,zoom);
                for (int index=0;index<161;index++) {
                    Rectangle bounds=grid.bounds(index);
                    int pointX=bounds.x+bounds.width/4;
                    int pointY=bounds.y+bounds.height/2;
                    assertEquals(index,grid.indexAt(pointX,pointY),
                            "logical target "+index+" width "+width+" zoom "+zoom);
                    assertFalse(grid.insertAfter(index,pointX));
                    assertTrue(grid.insertAfter(index,bounds.x+bounds.width*3/4));
                }
                assertEquals(-1,grid.indexAt(-1,20));
                assertEquals(-1,grid.indexAt(20,-1));
                assertEquals(-1,grid.indexAt(width+1,20));
            }
        }
    }

    @Test void whitespaceAndPartialLastRowNeverBecomeAccidentalDropTargets() {
        for (int width : new int[]{680,1000,1430}) {
            for (int zoom : new int[]{132,188,272}) {
                var grid = new VirtualGridLayout(161,width,zoom);
                Rectangle first = grid.bounds(0);
                int centerY = first.y+first.height/2;
                assertEquals(0,grid.indexAt(first.x+first.width/2,centerY));
                assertEquals(-1,grid.indexAt(first.x-1,centerY),
                        "Left margin must not snap to the first file");
                assertEquals(-1,grid.indexAt(first.x+first.width+ThumbnailSizing.GAP/2,centerY),
                        "Horizontal card gap must not trigger a reorder");
                assertEquals(-1,grid.indexAt(first.x+first.width/2,
                        first.y+first.height+ThumbnailSizing.GAP/2),
                        "Vertical card gap must not trigger a reorder");
                assertEquals(-1,grid.indexAt(width-1,centerY),
                        "Unused right-hand grid space must not snap to a card");

                Rectangle last = grid.bounds(160);
                int lastY = last.y+last.height/2;
                assertEquals(160,grid.indexAt(last.x+last.width/2,lastY));
                int afterLast = last.x+last.width+ThumbnailSizing.GAP+1;
                if (afterLast<width)
                    assertEquals(-1,grid.indexAt(afterLast,lastY),
                            "Unoccupied final-row cells must not target the last UDF");
            }
        }
    }

    @Test void edgeScrollIsBoundedAndStopsAwayFromViewportEdges() {
        assertTrue(VirtualGridLayout.edgeScrollDelta(5,650)<0);
        assertTrue(VirtualGridLayout.edgeScrollDelta(640,650)>0);
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(325,650));
        for (int pointer=-20;pointer<800;pointer++)
            assertTrue(Math.abs(VirtualGridLayout.edgeScrollDelta(pointer,650))<=36);
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(0,0));
        // Leaving the viewport must stop scrolling instead of driving the grid
        // while the pointer is outside the active drop area.
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(-1,650));
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(650,650));
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(999,650));
        // A narrow responsive window must not make the top and bottom zones
        // overlap: a pointer near the bottom must always scroll down.
        for (int height : new int[]{2, 8, 16, 23}) {
            assertTrue(VirtualGridLayout.edgeScrollDelta(0,height)<0);
            assertTrue(VirtualGridLayout.edgeScrollDelta(height-1,height)>0);
        }
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(0,1));
    }
}
