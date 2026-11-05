package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
        // A plain click on an unselected card replaces the selection outright.
        // Do not hash and scan the full virtual grid on this common path.
        if (!shift && !ctrl && !selected.contains(clicked))
            return new Press<>(List.of(clicked), clicked, false);
        // A non-additive Shift-click replaces the old selection with the anchor range.
        // Avoid hashing and scanning all previously selected cards in this case.
        if (shift && !ctrl) {
            T rangeAnchor = ordered.contains(anchor) ? anchor : clicked;
            int first = ordered.indexOf(rangeAnchor), last = ordered.indexOf(clicked);
            List<T> range = new ArrayList<>(Math.abs(last - first) + 1);
            for (int i = Math.min(first, last); i <= Math.max(first, last); i++)
                range.add(ordered.get(i));
            return new Press<>(range, rangeAnchor, false);
        }
        // Ctrl-click needs only one visual-order pass: toggle membership first,
        // then emit selected cards in grid order without a second full scan.
        if (ctrl && !shift) {
            HashSet<T> membership = new HashSet<>(selected);
            if (!membership.add(clicked)) membership.remove(clicked);
            List<T> next = new ArrayList<>(membership.size());
            for (T item : ordered) if (membership.contains(item)) next.add(item);
            return new Press<>(next, clicked, false);
        }
        if (shift) {
            // Ctrl+Shift: union the additive anchor range with the existing
            // selection, then emit once in the grid's canonical visual order.
            T rangeAnchor = ordered.contains(anchor) ? anchor : clicked;
            int first = ordered.indexOf(rangeAnchor), last = ordered.indexOf(clicked);
            HashSet<T> membership = new HashSet<>(selected);
            for (int i = Math.min(first, last); i <= Math.max(first, last); i++)
                membership.add(ordered.get(i));
            List<T> next = new ArrayList<>(membership.size());
            for (T item : ordered) if (membership.contains(item)) next.add(item);
            return new Press<>(next, rangeAnchor, false);
        }
        // Plain press on an already selected card preserves the entire
        // selection until release, allowing drag to start without collapsing.
        HashSet<T> membership = new HashSet<>(selected);
        List<T> next = new ArrayList<>(membership.size());
        for (T item : ordered) if (membership.contains(item)) next.add(item);
        if (next.size() > 1 && next.contains(clicked))
            return new Press<>(next, anchor, true);
        return new Press<>(List.of(clicked), clicked, false);
    }

    /** Canonicalize selection order independently of Ctrl-click insertion order. */
    static <T> List<T> inVisualOrder(List<T> ordered, Collection<T> selected) {
        List<T> result = new ArrayList<>();
        HashSet<T> membership = new HashSet<>(selected);
        for (T item : ordered) if (membership.contains(item)) result.add(item);
        return result;
    }

    public static <T> List<T> release(Collection<T> selected, T clicked,
                                      boolean collapseOnClick, boolean dragged) {
        if (selected == null || (collapseOnClick && !dragged && (clicked == null || !selected.contains(clicked))))
            throw new IllegalArgumentException("Geçersiz kart seçimi");
        return collapseOnClick && !dragged ? List.of(clicked) : List.copyOf(selected);
    }
}
