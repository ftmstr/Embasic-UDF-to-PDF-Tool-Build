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
        if (ctrl) {
            if (!next.add(clicked)) next.remove(clicked);
            return new Press<>(new ArrayList<>(next), clicked, false);
        }
        if (shift) {
            T rangeAnchor = ordered.contains(anchor) ? anchor : clicked;
            next.clear();
            int first = ordered.indexOf(rangeAnchor), last = ordered.indexOf(clicked);
            for (int i = Math.min(first, last); i <= Math.max(first, last); i++)
                next.add(ordered.get(i));
            // Keep the original anchor so successive Shift-clicks resize the same range.
            return new Press<>(new ArrayList<>(next), rangeAnchor, false);
        }
        if (next.size() > 1 && next.contains(clicked))
            return new Press<>(new ArrayList<>(next), anchor, true);
        return new Press<>(List.of(clicked), clicked, false);
    }

    public static <T> List<T> release(Collection<T> selected, T clicked,
                                      boolean collapseOnClick, boolean dragged) {
        if (selected == null) throw new IllegalArgumentException("Geçersiz kart seçimi");
        return collapseOnClick && !dragged ? List.of(clicked) : List.copyOf(selected);
    }
}
