package com.embasic.publicapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** Stable ordered multi-card move, independent of rendered Swing viewport components. */
public final class GridReorder {
    private GridReorder() {}
    public static <T> List<T> before(List<T> ordered, Collection<T> selected, T target) {
        if (ordered == null || selected == null || target == null || !ordered.contains(target))
            throw new IllegalArgumentException("Geçersiz kart sıralama hedefi");
        HashSet<T> selectedSet=new HashSet<>(selected);
        if(selectedSet.isEmpty() || selectedSet.contains(target))return List.copyOf(ordered);
        List<T> moving=new ArrayList<>();
        List<T> kept=new ArrayList<>();
        for(T value:ordered) {
            if(selectedSet.contains(value))moving.add(value);
            else kept.add(value);
        }
        if(moving.isEmpty())return List.copyOf(ordered);
        int insert=kept.indexOf(target);
        if(insert<0)throw new IllegalArgumentException("Hedef kart bulunamadı");
        kept.addAll(insert,moving);
        return List.copyOf(kept);
    }
}
