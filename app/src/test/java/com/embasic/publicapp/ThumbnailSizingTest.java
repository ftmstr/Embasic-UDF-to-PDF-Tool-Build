package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class ThumbnailSizingTest {
    @Test void clampsZoomWithoutOverflow() {
        assertEquals(132, ThumbnailSizing.at(-1).cardWidth());
        assertEquals(272, ThumbnailSizing.at(99999).cardWidth());
        assertEquals(272, ThumbnailSizing.zoomBy(250, 1000000));
        assertEquals(132, ThumbnailSizing.zoomBy(142, -1000000));
        assertEquals(188, ThumbnailSizing.at(188).cardWidth());
    }
    @Test void responsiveColumnsDecreaseAsCardsGrow() {
        int small=ThumbnailSizing.columns(1000,132);
        int medium=ThumbnailSizing.columns(1000,188);
        int large=ThumbnailSizing.columns(1000,272);
        assertTrue(small > medium);
        assertTrue(medium > large);
        assertEquals(1,ThumbnailSizing.columns(0,272));
    }
    @Test void cardAndImageRemainPositiveAndConsistent() {
        for(int width=132;width<=272;width++) {
            var size=ThumbnailSizing.at(width);
            assertTrue(size.cardHeight()>size.imageHeight());
            assertTrue(size.cardWidth()>size.imageWidth());
            assertTrue(size.imageHeight()>0 && size.imageWidth()>0);
            assertEquals(width,size.cardWidth());
        }
    }
    @Test void keyboardAndSliderUseSameCardRange() {
        int value=ThumbnailSizing.DEFAULT_WIDTH;
        for(int i=0;i<30;i++)value=ThumbnailSizing.zoomBy(value,1);
        assertEquals(272,value);
        for(int i=0;i<30;i++)value=ThumbnailSizing.zoomBy(value,-1);
        assertEquals(132,value);
    }
}
