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
    public record Selection(List<Path> accepted, boolean limitReached,
                            int unsupportedCount, int duplicateCount) {
        public Selection { accepted = List.copyOf(accepted); }
    }
    /**
     * CLI merges must never silently repeat a file or omit an unsupported
     * argument. Validate the entire batch before reading any archive.
     * Unlike the interactive grid, command-line use cannot display skipped
     * cards or ask the user to correct an incomplete selection.
     */
    public static List<Path> requireCompleteMerge(List<Path> candidates) {
        if (candidates == null || candidates.isEmpty())
            throw new IllegalArgumentException("PDF için en az bir UDF gerekli");
        if (candidates.size() > MAX_CARDS)
            throw new IllegalArgumentException("En fazla " + MAX_CARDS + " UDF dosyası birleştirilebilir");
        Selection plan = plan(List.of(), candidates);
        if (plan.unsupportedCount() > 0)
            throw new IllegalArgumentException("Birleştirme yalnızca .udf dosyalarını kabul eder");
        if (plan.duplicateCount() > 0)
            throw new IllegalArgumentException("Birleştirme yinelenen UDF dosyası içeriyor");
        if (plan.limitReached())
            throw new IllegalArgumentException("En fazla " + MAX_CARDS + " UDF dosyası birleştirilebilir");
        return plan.accepted();
    }

    public static Selection plan(List<Path> current, List<Path> candidates) {
        if (current == null || candidates == null) throw new IllegalArgumentException("Dosya listesi eksik");
        Set<Path> known = new HashSet<>();
        for (Path p : current) if (p != null) known.add(p.toAbsolutePath().normalize());
        List<Path> accepted = new ArrayList<>();
        boolean full = false;
        int unsupported = 0, duplicates = 0;
        for (Path p : candidates) {
            if (p == null) { unsupported++; continue; }
            Path normalized = p.toAbsolutePath().normalize();
            Path name = normalized.getFileName();
            if (name == null || !name.toString().toLowerCase(Locale.ROOT).endsWith(".udf")) {
                unsupported++;
                continue;
            }
            if (known.contains(normalized)) { duplicates++; continue; }
            if (known.size() >= MAX_CARDS) { full = true; continue; }
            known.add(normalized);
            accepted.add(normalized);
        }
        return new Selection(accepted, full, unsupported, duplicates);
    }
}
