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
    @Test void ctrlShiftRangeKeepsDisjointSelectionInVisualOrder() {
        // Previously [A,I] plus the B-D range returned [A,I,B,C,D],
        // which does not match the left-to-right card order.
        var forward = GridKeyboardSelection.move(FILES,List.of("A","I"),"B","B",
                2,true,true);
        assertEquals(List.of("A","B","C","D","I"),forward.selected());
        assertEquals("B",forward.anchor());
        assertEquals("D",forward.focused());

        var reverse = GridKeyboardSelection.move(FILES,List.of("A","I"),"H","H",
                -2,true,true);
        assertEquals(List.of("A","F","G","H","I"),reverse.selected());
        assertEquals("H",reverse.anchor());
        assertEquals("F",reverse.focused());

        var toEnd = GridKeyboardSelection.move(FILES,List.of("A","I"),"B","B",
                Integer.MAX_VALUE,true,true);
        assertEquals(FILES,toEnd.selected());
        assertEquals("B",toEnd.anchor());
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

        // Ctrl+A must retain keyboard focus even when the focused card is
        // virtualized/offscreen; the next arrow must start from that card.
        var all = GridKeyboardSelection.selectAll(FILES, "F");
        assertEquals(FILES, all.selected());
        assertEquals("F", all.focused());
        assertEquals("F", all.anchor());
        var afterAll = GridKeyboardSelection.move(FILES, all.selected(),
                all.anchor(), all.focused(), 1, false, false);
        assertEquals(List.of("G"), afterAll.selected());
        assertEquals("G", afterAll.focused());
        // Immutable List.of() throws on contains(null)/indexOf(null). An
        // uninitialized keyboard focus must not crash the real grid.
        var firstArrow = GridKeyboardSelection.move(FILES, List.of(),
                null, null, 1, false, false);
        assertEquals(List.of("A"), firstArrow.selected());
        assertEquals("A", firstArrow.focused());
        var reverseFirst = GridKeyboardSelection.move(FILES, List.of(),
                null, null, -1, false, false);
        assertEquals(List.of("I"), reverseFirst.selected());
        assertEquals("I", reverseFirst.focused());
        // The first vertical arrow uses the first/last logical card, not
        // column-1 or size-column when focus has never been established.
        var firstDown = GridKeyboardSelection.move(FILES, List.of(),
                null, null, 3, false, false);
        assertEquals(List.of("A"), firstDown.selected());
        assertEquals("A", firstDown.focused());
        var firstUp = GridKeyboardSelection.move(FILES, List.of(),
                null, null, -3, false, false);
        assertEquals(List.of("I"), firstUp.selected());
        assertEquals("I", firstUp.focused());
        // A stale anchor from a previous selection cannot extend the first
        // Shift+Down to an unrelated offscreen card.
        var firstShiftDown = GridKeyboardSelection.move(FILES, List.of(),
                "G", null, 3, true, false);
        assertEquals(List.of("A"), firstShiftDown.selected());
        assertEquals("A", firstShiftDown.anchor());
        var firstCtrlDown = GridKeyboardSelection.move(FILES, List.of(),
                null, null, 3, false, true);
        assertTrue(firstCtrlDown.selected().isEmpty());
        assertEquals("A", firstCtrlDown.focused());
        // Existing logical selection still provides the navigation origin.
        var fromSelection = GridKeyboardSelection.move(FILES, List.of("F"),
                "F", null, 3, false, false);
        assertEquals(List.of("I"), fromSelection.selected());
        var noFocus = GridKeyboardSelection.selectAll(FILES, null);
        assertEquals("A", noFocus.focused());
        assertEquals("A", noFocus.anchor());
        assertTrue(GridKeyboardSelection.selectAll(List.<String>of(), null).selected().isEmpty());
        assertNull(GridKeyboardSelection.selectAll(List.<String>of(), null).focused());

        var reverseRange = GridKeyboardSelection.move(FILES,List.of("F"),"F","F",
                -Integer.MAX_VALUE,true,false);
        assertEquals(List.of("A","B","C","D","E","F"),reverseRange.selected());
        assertEquals("F",reverseRange.anchor());
    }

    // Deleting selected cards must not leave a stale keyboard anchor. The
    // next arrow starts at the neighboring logical card, even offscreen.
    @Test void deletingCardsKeepsNearestExplorerFocus() {
        var middle = GridKeyboardSelection.afterRemoval(FILES, List.of("B", "C"), "C");
        assertEquals(List.of("D"), middle.selected());
        assertEquals("D", middle.anchor());
        assertEquals("D", middle.focused());

        var tail = GridKeyboardSelection.afterRemoval(FILES, List.of("H", "I"), "I");
        assertEquals(List.of("G"), tail.selected());
        assertEquals("G", tail.focused());

        var withoutFocus = GridKeyboardSelection.afterRemoval(FILES, List.of("B", "C"), null);
        assertEquals(List.of("D"), withoutFocus.selected());
        var unselectedFocus = GridKeyboardSelection.afterRemoval(FILES, List.of("B"), "F");
        assertEquals(List.of("F"), unselectedFocus.selected());

        var all = GridKeyboardSelection.afterRemoval(FILES, FILES, "E");
        assertTrue(all.selected().isEmpty());
        assertNull(all.anchor());
        assertNull(all.focused());
    }

    @Test void unorderedSelectionWithoutFocusNavigatesFromFirstVisualCard() {
        // HashSet iteration order must not decide the keyboard navigation origin.
        var unordered = new java.util.HashSet<>(List.of("F", "B"));
        var next = GridKeyboardSelection.move(FILES, unordered, null, null,
                1, false, false);
        assertEquals(List.of("C"), next.selected());
        assertEquals("C", next.focused());
        var previous = GridKeyboardSelection.move(FILES, unordered, null, null,
                -1, false, false);
        assertEquals(List.of("A"), previous.selected());
    }

    @Test void emptyFilesReturnEmptySelection() {
        var result=GridKeyboardSelection.move(List.<String>of(),List.<String>of(),
                "A","A",1,true,false);
        assertTrue(result.selected().isEmpty());
        assertNull(result.focused());
    }
}
