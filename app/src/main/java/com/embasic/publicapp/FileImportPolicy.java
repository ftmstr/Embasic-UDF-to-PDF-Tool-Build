package com.embasic.publicapp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded selection planning without disk IO; avoids duplicate cards and huge UI import bursts. */
public final class FileImportPolicy {
    public static final int MAX_CARDS = 160;
    private FileImportPolicy() {}
    public record Selection(List<Path> accepted, boolean limitReached) {
        public Selection { accepted = List.copyOf(accepted); }
    }
    public static Selection plan(List<Path> current, List<Path> candidates) {
        if (current == null || candidates == null) throw new IllegalArgumentException("Dosya listesi eksik");
        Set<Path> known = new HashSet<>();
        for (Path p : current) if (p != null) known.add(p.toAbsolutePath().normalize());
        List<Path> accepted = new ArrayList<>();
        boolean full = false;
        for (Path p : candidates) {
            if (p == null) continue;
            Path normalized = p.toAbsolutePath().normalize();
            Path name = normalized.getFileName();
            if (name == null || !name.toString().toLowerCase(Locale.ROOT).endsWith(".udf"))
                continue;
            if (known.contains(normalized)) continue;
            if (known.size() >= MAX_CARDS) { full = true; continue; }
            known.add(normalized);
            accepted.add(normalized);
        }
        return new Selection(accepted, full);
    }
}
