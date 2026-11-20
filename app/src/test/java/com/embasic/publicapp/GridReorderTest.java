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
        assertEquals(List.of("A","C","B","D","E"),
            GridReorder.before(files,List.of("B","D"),"E"));
    }
    @Test void droppingAfterTargetMovesGroupInOriginalOrder() {
        var files = List.of("A", "B", "C", "D", "E", "F");
        assertEquals(List.of("A", "D", "B", "C", "E", "F"),
            GridReorder.after(files, List.of("C", "B"), "D"));
        assertEquals(List.of("A", "C", "D", "E", "B", "F"),
            GridReorder.after(files, List.of("B"), "E"));
        assertEquals(files, GridReorder.after(files, List.of("B", "D"), "D"),
            "Dropping onto a selected target must not split the group");
        assertThrows(IllegalArgumentException.class,
            () -> GridReorder.after(files, List.of("B"), "missing"));
        assertEquals("B", files.get(1), "Source list must remain untouched");
    }

    @Test void toolbarStepMovesMultiSelectionWithoutChangingInternalOrder() {
        var cards = List.of("A", "B", "C", "D", "E", "F");
        var selected = List.of("B", "D");
        assertEquals(List.of("B", "A", "D", "C", "E", "F"),
                GridReorder.step(cards, selected, -1));
        assertEquals(List.of("A", "C", "B", "E", "D", "F"),
                GridReorder.step(cards, selected, 1));
        assertEquals(List.of("C", "A", "B"),
                GridReorder.step(List.of("A", "B", "C"), List.of("A", "B"), 1),
                "Adjacent selection must move as a stable block");
        assertEquals(List.of("B", "C", "A"),
                GridReorder.step(List.of("A", "B", "C"), List.of("B", "C"), -1));
        assertEquals(cards, GridReorder.step(cards, List.of(), -1));
        assertEquals(cards, GridReorder.step(cards, List.of("missing"), 1));
        assertEquals(List.of("A", "B", "C", "D", "F", "E"),
                GridReorder.step(cards, List.of("A", "F"), -1),
                "An edge card stays in place while another selected card can move");
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), cards,
                "Source list must remain unchanged");
        assertThrows(IllegalArgumentException.class, () -> GridReorder.step(cards, selected, 0));
        assertThrows(IllegalArgumentException.class, () -> GridReorder.step(null, selected, 1));
    }

    @Test void droppingOnPlusTileMovesSelectedCardsToEnd() {
        var files = List.of("A", "B", "C", "D", "E");
        assertEquals(List.of("A", "C", "E", "B", "D"),
            GridReorder.toEnd(files, List.of("D", "B")));
        assertEquals(files, GridReorder.toEnd(files, List.of()));
        assertEquals(files, GridReorder.toEnd(files, List.of("missing")));
        assertThrows(IllegalArgumentException.class, () -> GridReorder.toEnd(null, List.of("A")));
    }

}
