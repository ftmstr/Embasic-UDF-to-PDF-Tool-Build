package com.embasic.publicapp;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
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

    /**
     * Validate the entire CLI merge set before opening the first ZIP. A missing
     * or unreadable last file must not waste parsing 159 earlier documents.
     * The interactive grid retains its existing per-card error handling.
     */
    static void preflightReadableMergeFiles(List<Path> validated) {
        // A second spelling of the same file (symbolic link or hard link)
        // must not silently duplicate its pages in the output PDF. File keys
        // avoid quadratic filesystem probes for ordinary 160-file batches.
        Set<Object> identities = new HashSet<>();
        List<Path> checked = new ArrayList<>();
        List<Path> withoutIdentity = new ArrayList<>();
        for (Path path : validated) {
            if (!Files.isRegularFile(path) || !Files.isReadable(path))
                throw new IllegalArgumentException("Geçerli, okunabilir bir .udf dosyası seçin: "
                        + path.getFileName());
            try {
                Object identity = Files.readAttributes(path, BasicFileAttributes.class).fileKey();
                if (identity != null) {
                    if (!identities.add(identity))
                        throw new IllegalArgumentException("Birleştirme aynı UDF dosyasına ait yinelenen bağlantı içeriyor");
                    // Some providers cannot supply file keys; compare those
                    // earlier paths with isSameFile instead of trusting names.
                    for (Path previous : withoutIdentity)
                        if (Files.isSameFile(path, previous))
                            throw new IllegalArgumentException("Birleştirme aynı UDF dosyasına ait yinelenen bağlantı içeriyor");
                } else {
                    for (Path previous : checked)
                        if (Files.isSameFile(path, previous))
                            throw new IllegalArgumentException("Birleştirme aynı UDF dosyasına ait yinelenen bağlantı içeriyor");
                    withoutIdentity.add(path);
                }
                checked.add(path);
            } catch (IOException ex) {
                throw new IllegalArgumentException("UDF dosyası doğrulanamadı: " + path.getFileName(), ex);
            }
        }
    }

    /**
     * Reject a physical alias before parsing a newly imported GUI card. This
     * filesystem work runs on the parser worker, never the Swing EDT. Earlier
     * cards may have been removed while the worker was queued; missing paths
     * are ignored here, while the export preflight still validates every
     * remaining source.
     */
    static void preflightDistinctImport(Path candidate, List<Path> earlier) {
        if (candidate == null || earlier == null)
            throw new IllegalArgumentException("UDF kaynak listesi eksik");
        if (!Files.isRegularFile(candidate) || !Files.isReadable(candidate))
            throw new IllegalArgumentException("Geçerli, okunabilir bir .udf dosyası seçin: "
                    + candidate.getFileName());
        for (Path previous : earlier) {
            if (previous == null || !Files.exists(previous)) continue;
            try {
                if (Files.isSameFile(candidate, previous))
                    throw new IllegalArgumentException(
                            "Bu UDF dosyası zaten çalışma alanında (fiziksel yinelenen bağlantı): "
                                    + candidate.getFileName());
            } catch (IOException ex) {
                throw new IllegalArgumentException(
                        "UDF bağlantısı doğrulanamadı: " + candidate.getFileName(), ex);
            }
        }
    }

    /**
     * Stream a SHA-256 fingerprint without retaining the archive in memory.
     * The GUI compares this at import and before export so a changed source
     * cannot silently produce a PDF from an obsolete parsed preview.
     */
    static String sourceSha256(Path source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(source)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1)
                    digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new IllegalArgumentException("UDF kaynağı okunamadı: " + source.getFileName(), ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", ex);
        }
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
