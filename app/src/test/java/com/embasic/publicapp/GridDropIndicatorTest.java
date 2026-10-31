package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Render-free and headless paint tests for a virtualized drag indicator. */
final class GridDropIndicatorTest {
    @Test void markerTracksTargetsAcrossScrollZoomAndWindowWidths() {
        for (int width : List.of(680,1000,1430)) {
            for (int zoom : List.of(132,188,272)) {
                VirtualGridLayout layout=new VirtualGridLayout(161,width,zoom);
                for (int index : List.of(0,1,8,20,54,110,159,160)) {
                    Rectangle tile=layout.bounds(index);
                    for (boolean after : new boolean[]{false,true}) {
                        Rectangle indicator=GridDropIndicator.bounds(layout,index,after);
                        assertNotNull(indicator);
                        assertTrue(tile.contains(indicator.x+indicator.width/2,
                                        indicator.y+indicator.height/2));
                        if (index == 160) {
                            assertEquals(tile.y+tile.height-6,indicator.y);
                        } else if (after) {
                            assertTrue(indicator.x>tile.x+tile.width/2);
                        } else {
                            assertEquals(tile.x,indicator.x);
                        }
                    }
                    assertEquals(index,GridDropIndicator.targetAt(layout,
                            tile.x+tile.width/2,tile.y+tile.height/2));
                }
                assertNull(GridDropIndicator.bounds(layout,-1,false));
                assertNull(GridDropIndicator.bounds(layout,161,false));
                assertEquals(-1,GridDropIndicator.targetAt(layout,-20,-20));
            }
        }
    }

    @Test void paintOnlyIndicatorIsVisibleAndDoesNotModifyContent() {
        VirtualGridLayout layout=new VirtualGridLayout(3,680,188);
        Rectangle tile=layout.bounds(1);
        Rectangle marker=GridDropIndicator.bounds(layout,1,true);
        BufferedImage frame=new BufferedImage(680,270,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=frame.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0,0,frame.getWidth(),frame.getHeight());
            graphics.setColor(Color.BLUE);
            graphics.fillRect(marker.x,marker.y,marker.width,marker.height);
        } finally { graphics.dispose(); }
        assertEquals(Color.BLUE.getRGB(),frame.getRGB(
                marker.x+marker.width/2,marker.y+marker.height/2));
        assertEquals(Color.WHITE.getRGB(),frame.getRGB(tile.x+tile.width/2,tile.y+30));
        assertEquals(3,layout.count());
    }

    @Test void emptyGridAndNullLayoutNeverCreateIndicators() {
        assertNull(GridDropIndicator.bounds(null,0,false));
        assertEquals(-1,GridDropIndicator.targetAt(null,10,10));
        assertNull(GridDropIndicator.bounds(new VirtualGridLayout(0,700,188),0,true));
    }
}
