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
    @Test void homeEndNavigationWorksAcrossUnrenderedCardsAndModifiers() {
        // The Swing grid renders only visible cards; logical navigation must
        // reach both ends without relying on instantiated components.
        var end = GridKeyboardSelection.move(FILES,List.of("C"),"C","C",
                Integer.MAX_VALUE,false,false);
        assertEquals("I",end.focused());
        assertEquals(List.of("I"),end.selected());

        var range = GridKeyboardSelection.move(FILES,List.of("C"),"C","C",
                Integer.MAX_VALUE,true,false);
        assertEquals(List.of("C","D","E","F","G","H","I"),range.selected());
        assertEquals("C",range.anchor());

        var focusOnly = GridKeyboardSelection.move(FILES,List.of("C","F"),"C","F",
                -Integer.MAX_VALUE,false,true);
        assertEquals("A",focusOnly.focused());
        assertEquals(List.of("C","F"),focusOnly.selected());

        var reverseRange = GridKeyboardSelection.move(FILES,List.of("F"),"F","F",
                -Integer.MAX_VALUE,true,false);
        assertEquals(List.of("A","B","C","D","E","F"),reverseRange.selected());
        assertEquals("F",reverseRange.anchor());
    }

    @Test void emptyFilesReturnEmptySelection() {
        var result=GridKeyboardSelection.move(List.<String>of(),List.<String>of(),
                "A","A",1,true,false);
        assertTrue(result.selected().isEmpty());
        assertNull(result.focused());
    }
}
