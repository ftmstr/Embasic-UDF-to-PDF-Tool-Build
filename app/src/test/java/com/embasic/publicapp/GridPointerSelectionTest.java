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

    @Test void ctrlShiftClickAddsAnchorRangeWithoutDiscardingOtherCards() {
        var range = GridPointerSelection.press(FILES, List.of("A", "E"), "B", "D", true, true);
        assertEquals(List.of("A", "B", "C", "D", "E"), range.selected());
        assertEquals("B", range.anchor());
        assertFalse(range.collapseOnClick());
        // Reversing the additive range must not remove previously selected cards.
        var shorter = GridPointerSelection.press(FILES, List.of("A", "E"), "D", "C", true, true);
        assertEquals(List.of("A", "C", "D", "E"), shorter.selected());
        assertEquals("D", shorter.anchor());
        var ctrlOnly = GridPointerSelection.press(FILES, List.of("A", "E"), "B", "E", false, true);
        assertEquals(List.of("A"), ctrlOnly.selected());
    }

    @Test void removedAnchorAndUnknownClickedCardAreHandledSafely() {
        var pressed = GridPointerSelection.press(FILES, List.of("B"), "removed", "D", true, false);
        assertEquals(List.of("D"), pressed.selected());
        assertEquals("D", pressed.anchor());
        assertThrows(IllegalArgumentException.class,
                () -> GridPointerSelection.press(FILES, List.of("B"), "B", "removed", false, false));
    }
}
