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
    }
    @Test void emptyAndInvalidViewportsAreSafe() {
        var grid=new VirtualGridLayout(0,0,188);
        assertEquals(0,grid.visible(new Rectangle(0,0,0,0)).count());
        assertTrue(grid.preferredSize().height>0);
        assertThrows(IllegalArgumentException.class,()->new VirtualGridLayout(-1,400,188));
        assertThrows(IndexOutOfBoundsException.class,()->grid.bounds(0));
        var nonempty=new VirtualGridLayout(7,1000,188);
        assertTrue(nonempty.visible(new Rectangle(0,0,0,0)).count()<=nonempty.columns()*2);
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

    @Test void edgeScrollIsBoundedAndStopsAwayFromViewportEdges() {
        assertTrue(VirtualGridLayout.edgeScrollDelta(5,650)<0);
        assertTrue(VirtualGridLayout.edgeScrollDelta(640,650)>0);
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(325,650));
        for (int pointer=-20;pointer<800;pointer++)
            assertTrue(Math.abs(VirtualGridLayout.edgeScrollDelta(pointer,650))<=36);
        assertEquals(0,VirtualGridLayout.edgeScrollDelta(0,0));
    }
}
