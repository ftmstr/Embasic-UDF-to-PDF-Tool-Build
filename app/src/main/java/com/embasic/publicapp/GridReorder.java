package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** Stable ordered multi-card move, independent of rendered Swing viewport components. */
public final class GridReorder {
    private GridReorder() {}
    public static <T> List<T> before(List<T> ordered, Collection<T> selected, T target) {
        return move(ordered, selected, target, false);
    }

    /** Drop on the right half of a card: insert the selection immediately after it. */
    public static <T> List<T> after(List<T> ordered, Collection<T> selected, T target) {
        return move(ordered, selected, target, true);
    }

    /** Drop on the trailing + tile: move selected cards to the end, preserving order. */
    public static <T> List<T> toEnd(List<T> ordered, Collection<T> selected) {
        if (ordered == null || selected == null)
            throw new IllegalArgumentException("Geçersiz kart sıralama");
        HashSet<T> selectedSet = new HashSet<>(selected);
        List<T> moving = new ArrayList<>();
        List<T> kept = new ArrayList<>();
        for (T value : ordered) {
            if (selectedSet.contains(value)) moving.add(value);
            else kept.add(value);
        }
        kept.addAll(moving);
        return List.copyOf(kept);
    }

    /**
     * Move each selected card by one adjacent slot, as Explorer-style arrow
     * controls do. Iterate in the direction opposite to the move so adjacent
     * selected cards remain in their original order and travel as a group.
     * The source list and selection are never mutated.
     */
    public static <T> List<T> step(List<T> ordered, Collection<T> selected, int direction) {
        if (ordered == null || selected == null || (direction != -1 && direction != 1))
            throw new IllegalArgumentException("Geçersiz kart taşıma yönü");
        List<T> result = new ArrayList<>(ordered);
        HashSet<T> selectedSet = new HashSet<>(selected);
        if (direction < 0) {
            for (int i = 1; i < result.size(); i++)
                if (selectedSet.contains(result.get(i)) && !selectedSet.contains(result.get(i - 1))) {
                    T card = result.get(i);
                    result.set(i, result.get(i - 1));
                    result.set(i - 1, card);
                }
        } else {
            for (int i = result.size() - 2; i >= 0; i--)
                if (selectedSet.contains(result.get(i)) && !selectedSet.contains(result.get(i + 1))) {
                    T card = result.get(i);
                    result.set(i, result.get(i + 1));
                    result.set(i + 1, card);
                }
        }
        return List.copyOf(result);
    }

    private static <T> List<T> move(List<T> ordered, Collection<T> selected,
                                    T target, boolean after) {
        if (ordered == null || selected == null || target == null || !ordered.contains(target))
            throw new IllegalArgumentException("Geçersiz kart sıralama hedefi");
        HashSet<T> selectedSet = new HashSet<>(selected);
        if (selectedSet.isEmpty() || selectedSet.contains(target)) return List.copyOf(ordered);
        List<T> moving = new ArrayList<>();
        List<T> kept = new ArrayList<>();
        for (T value : ordered) {
            if (selectedSet.contains(value)) moving.add(value);
            else kept.add(value);
        }
        if (moving.isEmpty()) return List.copyOf(ordered);
        int insert = kept.indexOf(target);
        if (insert < 0) throw new IllegalArgumentException("Hedef kart bulunamadı");
        kept.addAll(insert + (after ? 1 : 0), moving);
        return List.copyOf(kept);
    }
}
