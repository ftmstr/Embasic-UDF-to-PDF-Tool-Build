package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GridReorderTest {
    @Test void dragMultipleOffscreenCardsKeepsTheirFileOrder() {
        List<String> items=new ArrayList<>();
        for(int i=0;i<160;i++)items.add("doc"+i);
        List<String> result=GridReorder.before(items,List.of("doc91","doc5","doc120"),"doc150");
        assertEquals(160,result.size());
        int at=result.indexOf("doc150");
        assertEquals(List.of("doc5","doc91","doc120"),result.subList(at-3,at));
        assertEquals("doc0",result.get(0));
        assertEquals("doc159",result.get(159));
        assertEquals("doc0",items.get(0),"Source list must not be changed");
    }
    @Test void selectedTargetAndInvalidTargetDoNotLoseData() {
        var original=List.of("A","B","C","D");
        assertEquals(original,GridReorder.before(original,List.of("B","D"),"D"));
        assertEquals(original,GridReorder.before(original,List.of(),"A"));
        assertEquals(original,GridReorder.before(original,List.of("removed"),"C"));
        assertThrows(IllegalArgumentException.class,
            ()->GridReorder.before(original,List.of("B"),"removed"));
    }
    @Test void singleCardAndGroupMovementAreStable() {
        var files=List.of("A","B","C","D","E");
        assertEquals(List.of("D","A","B","C","E"),
            GridReorder.before(files,List.of("D"),"A"));
        assertEquals(List.of("A","C","E","B","D"),
            GridReorder.before(files,List.of("B","D"),"E"));
    }
}
