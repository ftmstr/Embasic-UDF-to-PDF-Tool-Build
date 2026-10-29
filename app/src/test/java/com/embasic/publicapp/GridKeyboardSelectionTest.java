package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GridKeyboardSelectionTest {
    private static final List<String> FILES =
            List.of("A","B","C","D","E","F","G","H","I");

    @Test void plainArrowsSelectOnlyNewFocusedCard() {
        var result = GridKeyboardSelection.move(FILES,List.of("B"),"B","B",3,false,false);
        assertEquals(List.of("E"),result.selected());
        assertEquals("E",result.anchor());
        assertEquals("E",result.focused());
    }
    @Test void shiftNavigationSelectsContinuousRange() {
        var first = GridKeyboardSelection.move(FILES,List.of("B"),"B","B",3,true,false);
        assertEquals(List.of("B","C","D","E"),first.selected());
        var second = GridKeyboardSelection.move(FILES,first.selected(),first.anchor(),
                first.focused(),-1,true,false);
        assertEquals(List.of("B","C","D"),second.selected());
        assertEquals("B",second.anchor());
    }
    @Test void ctrlArrowMovesFocusButDoesNotDestroySelection() {
        var result=GridKeyboardSelection.move(FILES,List.of("B","F"),"B","B",3,false,true);
        assertEquals(List.of("B","F"),result.selected());
        assertEquals("E",result.focused());
        assertEquals("B",result.anchor());
    }
    @Test void clampsEdgesAndIgnoresRemovedAnchor() {
        var first=GridKeyboardSelection.move(FILES,List.of("I"),"I","I",1000,false,false);
        assertEquals("I",first.focused());
        var second=GridKeyboardSelection.move(FILES,List.of("A"),"removed","A",-999,true,false);
        assertEquals(List.of("A"),second.selected());
        assertEquals("A",second.anchor());
        assertEquals("A",second.focused());
    }
    @Test void emptyFilesReturnEmptySelection() {
        var result=GridKeyboardSelection.move(List.<String>of(),List.<String>of(),
                "A","A",1,true,false);
        assertTrue(result.selected().isEmpty());
        assertNull(result.focused());
    }
}
