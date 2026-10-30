package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure keyboard navigation independent of Swing; preserves order and range-selection anchors. */
public final class GridKeyboardSelection {
    private GridKeyboardSelection() {}

    public record Result<T>(List<T> selected, T anchor, T focused) {
        public Result { selected = List.copyOf(selected); }
    }

    /**
     * Navigates a flattened grid by signed delta (+/-1, +/-column count).
     * Ctrl-only moves the focus without changing selection; Shift extends a contiguous range.
     */
    public static <T> Result<T> move(
            List<T> files, Collection<T> selected, T anchor, T focused,
            int delta, boolean shift, boolean ctrl) {
        if (files == null || selected == null) throw new IllegalArgumentException("Dosya seçimi eksik");
        if (files.isEmpty()) return new Result<>(List.of(), null, null);
        int oldIndex = files.indexOf(focused);
        if (oldIndex < 0) {
            for (T value : selected) {
                oldIndex = files.indexOf(value);
                if (oldIndex >= 0) break;
            }
        }
        if (oldIndex < 0) oldIndex = 0;
        int next = (int) Math.max(0L, Math.min(files.size() - 1L, (long) oldIndex + delta));
        T nextFile = files.get(next);
        LinkedHashSet<T> nextSelection = new LinkedHashSet<>();
        for (T item : files) if (selected.contains(item)) nextSelection.add(item);
        T nextAnchor = files.contains(anchor) ? anchor : files.get(oldIndex);
        if (shift) {
            int start = files.indexOf(nextAnchor);
            if (!ctrl) nextSelection.clear();
            for (int i = Math.min(start, next); i <= Math.max(start, next); i++)
                nextSelection.add(files.get(i));
        } else if (!ctrl) {
            nextSelection.clear();
            nextSelection.add(nextFile);
            nextAnchor = nextFile;
        }
        // Ctrl+Shift may add a range to an earlier disjoint selection. Keep the
        // resulting cards in visual grid order, not insertion order, so keyboard
        // selection matches pointer selection and subsequent PDF reordering.
        List<T> orderedSelection = new ArrayList<>();
        for (T item : files) if (nextSelection.contains(item)) orderedSelection.add(item);
        return new Result<>(orderedSelection, nextAnchor, nextFile);
    }
}
