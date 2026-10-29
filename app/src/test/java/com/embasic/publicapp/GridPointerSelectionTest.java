package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GridPointerSelectionTest {
    private static final List<String> FILES = List.of("A", "B", "C", "D", "E");

    @Test void dragFromSelectedCardRetainsAllSelectedCards() {
        var pressed = GridPointerSelection.press(FILES, List.of("B", "D"), "B", "D", false, false);
        assertEquals(List.of("B", "D"), pressed.selected());
        assertTrue(pressed.collapseOnClick());
        assertEquals(List.of("B", "D"),
                GridPointerSelection.release(pressed.selected(), "D", pressed.collapseOnClick(), true));
    }

    @Test void ordinaryClickOnSelectedCardCollapsesOnlyAfterRelease() {
        var pressed = GridPointerSelection.press(FILES, List.of("B", "D"), "B", "D", false, false);
        assertEquals(List.of("D"),
                GridPointerSelection.release(pressed.selected(), "D", pressed.collapseOnClick(), false));
        var newClick = GridPointerSelection.press(FILES, List.of("B", "D"), "B", "C", false, false);
        assertEquals(List.of("C"), newClick.selected());
        assertFalse(newClick.collapseOnClick());
    }

    @Test void repeatedShiftClicksKeepOriginalRangeAnchor() {
        var first = GridPointerSelection.press(FILES, List.of("B"), "B", "E", true, false);
        assertEquals(List.of("B", "C", "D", "E"), first.selected());
        assertEquals("B", first.anchor());
        var second = GridPointerSelection.press(FILES, first.selected(), first.anchor(), "C", true, false);
        assertEquals(List.of("B", "C"), second.selected());
        assertEquals("B", second.anchor());
    }

    @Test void ctrlClickTogglesWithoutDestroyingOtherSelection() {
        var pressed = GridPointerSelection.press(FILES, List.of("B", "D"), "B", "D", false, true);
        assertEquals(List.of("B"), pressed.selected());
        assertEquals("D", pressed.anchor());
        assertFalse(pressed.collapseOnClick());
    }

    @Test void removedAnchorAndUnknownClickedCardAreHandledSafely() {
        var pressed = GridPointerSelection.press(FILES, List.of("B"), "removed", "D", true, false);
        assertEquals(List.of("D"), pressed.selected());
        assertEquals("D", pressed.anchor());
        assertThrows(IllegalArgumentException.class,
                () -> GridPointerSelection.press(FILES, List.of("B"), "B", "removed", false, false));
    }
}
