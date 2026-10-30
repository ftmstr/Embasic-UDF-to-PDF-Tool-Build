package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Explorer-style pointer selection without Swing dependencies.
 * A plain press on an already selected card keeps the multi-selection alive for dragging.
 * Only a completed click (not a drag) collapses it to the clicked card.
 */
public final class GridPointerSelection {
    private GridPointerSelection() {}

    public record Press<T>(List<T> selected, T anchor, boolean collapseOnClick) {
        public Press { selected = List.copyOf(selected); }
    }

    public static <T> Press<T> press(List<T> ordered, Collection<T> selected,
                                    T anchor, T clicked, boolean shift, boolean ctrl) {
        if (ordered == null || selected == null || clicked == null || !ordered.contains(clicked))
            throw new IllegalArgumentException("Geçersiz kart seçimi");
        LinkedHashSet<T> next = new LinkedHashSet<>();
        for (T item : ordered) if (selected.contains(item)) next.add(item);
        if (shift) {
            T rangeAnchor = ordered.contains(anchor) ? anchor : clicked;
            // Ctrl+Shift extends the anchor range without dropping existing
            // disjoint selections, like Explorer's additive range selection.
            if (!ctrl) next.clear();
            int first = ordered.indexOf(rangeAnchor), last = ordered.indexOf(clicked);
            for (int i = Math.min(first, last); i <= Math.max(first, last); i++)
                next.add(ordered.get(i));
            // Keep the original anchor so successive Shift-clicks resize the same range.
            // A disjoint Ctrl+Shift union must still follow the visual file order.
            return new Press<>(inVisualOrder(ordered, next), rangeAnchor, false);
        }
        if (ctrl) {
            if (!next.add(clicked)) next.remove(clicked);
            // Ctrl-click can insert a card between two earlier selections.
            // Keep the selection in visual file order for subsequent actions.
            return new Press<>(inVisualOrder(ordered, next), clicked, false);
        }
        if (next.size() > 1 && next.contains(clicked))
            return new Press<>(new ArrayList<>(next), anchor, true);
        return new Press<>(List.of(clicked), clicked, false);
    }

    /** Canonicalize selection order independently of Ctrl-click insertion order. */
    static <T> List<T> inVisualOrder(List<T> ordered, Collection<T> selected) {
        List<T> result = new ArrayList<>();
        for (T item : ordered) if (selected.contains(item)) result.add(item);
        return result;
    }

    public static <T> List<T> release(Collection<T> selected, T clicked,
                                      boolean collapseOnClick, boolean dragged) {
        if (selected == null) throw new IllegalArgumentException("Geçersiz kart seçimi");
        return collapseOnClick && !dragged ? List.of(clicked) : List.copyOf(selected);
    }
}
