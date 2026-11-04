package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure keyboard navigation independent of Swing; preserves order and range-selection anchors. */
public final class GridKeyboardSelection {
    private GridKeyboardSelection() {}

    public record Result<T>(List<T> selected, T anchor, T focused) {
        public Result { selected = List.copyOf(selected); }
    }

    /**
     * Explorer-style Ctrl+A must select the complete logical queue while
     * retaining the current keyboard focus. Without a focused item, the next
     * arrow starts from an unrelated stale anchor or an arbitrary selection.
     * This works even when the focused card is outside the virtual viewport.
     */
    public static <T> Result<T> selectAll(List<T> files, T focused) {
        if (files == null) throw new IllegalArgumentException("Dosya listesi eksik");
        if (files.isEmpty()) return new Result<>(List.of(), null, null);
        T current = focused != null && files.contains(focused) ? focused : files.get(0);
        return new Result<>(files, current, current);
    }

    /**
     * After Delete, keep Explorer keyboard navigation beside the removed cards
     * instead of retaining a stale focus that makes the next arrow jump to the
     * first card. The nearest surviving slot is selected; removing the last
     * cards focuses the previous survivor. Works with offscreen logical cards.
     */
    public static <T> Result<T> afterRemoval(
            List<T> files, Collection<T> removed, T focused) {
        if (files == null || removed == null)
            throw new IllegalArgumentException("Dosya seçimi eksik");
        if (files.isEmpty())
            return new Result<>(List.of(), null, null);
        // An empty delete request must not erase the current keyboard focus.
        // Keep the existing logical card selected rather than resetting the grid.
        if (removed.isEmpty()) {
            T current = focused != null && files.contains(focused) ? focused : null;
            return current == null ? new Result<>(List.of(), null, null)
                    : new Result<>(List.of(current), current, current);
        }
        // Normalize arbitrary collections once: large batch deletions must not
        // scan a list of removed cards for every logical grid item.
        HashSet<T> removedSet = new HashSet<>(removed);
        List<T> survivors = new ArrayList<>();
        int firstRemoved = -1;
        int survivingBeforeFocus = 0;
        int focusedIndex = focused == null ? -1 : files.indexOf(focused);
        for (int i = 0; i < files.size(); i++) {
            T item = files.get(i);
            if (removedSet.contains(item)) {
                if (firstRemoved < 0) firstRemoved = i;
            } else {
                survivors.add(item);
                if (focusedIndex >= 0 && i < focusedIndex) survivingBeforeFocus++;
            }
        }
        if (survivors.isEmpty()) return new Result<>(List.of(), null, null);
        // A stale deletion request may name cards already removed from the grid.
        // When no current card was deleted, preserve a surviving keyboard focus
        // instead of jumping to the first card on the next arrow.
        if (firstRemoved < 0) {
            T current = focusedIndex >= 0 ? files.get(focusedIndex) : null;
            return current == null ? new Result<>(List.of(), null, null)
                    : new Result<>(List.of(current), current, current);
        }
        // Ctrl+arrow may have left focus on an unselected card. Preserve its
        // surviving position; otherwise use the first deleted slot.
        int position;
        if (focusedIndex >= 0) {
            position = survivingBeforeFocus;
        } else {
            position = 0;
            for (int i = 0; i < firstRemoved; i++)
                if (!removedSet.contains(files.get(i))) position++;
        }
        T next = survivors.get(Math.min(position, survivors.size() - 1));
        return new Result<>(List.of(next), next, next);
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
        int oldIndex = focused == null ? -1 : files.indexOf(focused);
        // Reuse one membership index for both fallback focus and selection.
        // Large Ctrl+A queues must not rebuild the same hash table per arrow.
        HashSet<T> selectedSet = new HashSet<>(selected);
        if (oldIndex < 0 && !selected.isEmpty()) {
            // Selection collections may be unordered (e.g. HashSet). With no
            // focused card, navigate from the first card in visual grid order,
            // not whichever selected element the collection iterates first.
            for (int i = 0; i < files.size(); i++) {
                if (selectedSet.contains(files.get(i))) {
                    oldIndex = i;
                    break;
                }
            }
        }
        // Without focus or selection, the first key must land on the first
        // or last card even for a vertical (+/-columns) arrow. Adding delta
        // to a synthetic -1/size origin would otherwise skip an entire row.
        boolean hasOrigin = oldIndex >= 0;
        int next = hasOrigin
                ? (int) Math.max(0L, Math.min(files.size() - 1L, (long) oldIndex + delta))
                : (delta < 0 ? files.size() - 1 : 0);
        T nextFile = files.get(next);
        // Ctrl+arrow changes only keyboard focus. Avoid building and sorting
        // the selection twice on every key repeat in large virtual grids.
        if (ctrl && !shift) {
            List<T> ordered = new ArrayList<>();
            boolean anchorPresent = false;
            for (T item : files) {
                if (selectedSet.contains(item)) ordered.add(item);
                if (anchor != null && anchor.equals(item)) anchorPresent = true;
            }
            T nextAnchor = !hasOrigin ? nextFile
                    : anchorPresent ? anchor : files.get(oldIndex);
            return new Result<>(ordered, nextAnchor, nextFile);
        }
        LinkedHashSet<T> nextSelection = new LinkedHashSet<>();
        for (T item : files) if (selectedSet.contains(item)) nextSelection.add(item);
        // A stale anchor must not turn the first Shift+arrow into a surprise
        // multi-card range when no card is currently focused or selected.
        T nextAnchor = !hasOrigin ? nextFile
                : anchor != null && files.contains(anchor) ? anchor : files.get(oldIndex);
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
