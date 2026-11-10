package com.embasic.publicapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Conservatively prefer a persistent, user-profile-owned PDFBox font metadata
 * cache on Windows. PDFBox itself validates font-file checksums and rebuilds a
 * stale/corrupt cache; this class never edits or accepts cache entries.
 *
 * No global PDFBox checksum flags or renderer/font substitutions are used.
 * If a safe directory cannot be prepared, retain PDFBox's existing defaults.
 */
final class PdfBoxFontCacheDirectory {
    private static final String PROPERTY = "pdfbox.fontcache";
    private static final String CHILD = "Embasic-UDF-to-PDF";
    private PdfBoxFontCacheDirectory() {}

    static Optional<Path> prepare(Path home, Path localAppData) {
        if (home == null || localAppData == null) return Optional.empty();
        try {
            Path privateHome = home.toAbsolutePath().normalize();
            Path local = localAppData.toAbsolutePath().normalize();
            if (!local.startsWith(privateHome)
                    || Files.isSymbolicLink(privateHome)
                    || Files.isSymbolicLink(local)
                    || !Files.isDirectory(privateHome, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(local, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isWritable(local))
                return Optional.empty();

            Path app = local.resolve(CHILD);
            if (Files.isSymbolicLink(app)) return Optional.empty();
            if (!Files.exists(app, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(app);
            if (!Files.isDirectory(app, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isWritable(app)) return Optional.empty();

            // Never select a malicious alias or directory in the cache-file
            // position. A regular but corrupt cache is left for PDFBox's
            // built-in checksum and format validation to reject/rebuild.
            Path cache = app.resolve(".pdfbox.cache");
            if (Files.exists(cache, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(cache, LinkOption.NOFOLLOW_LINKS))
                return Optional.empty();
            return Optional.of(app);
        } catch (IOException | SecurityException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static void configureForDesktop() {
        // Respect explicit JVM overrides; do not replace PDFBox's own integrity
        // logic, including its default checksum validation.
        if (System.getProperty(PROPERTY) != null
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                        .startsWith("windows"))
            return;
        try {
            Optional<Path> cache = prepare(
                    Path.of(System.getProperty("user.home", "")),
                    Path.of(System.getenv().getOrDefault("LOCALAPPDATA", "")));
            cache.ifPresent(path -> System.setProperty(PROPERTY, path.toString()));
        } catch (SecurityException | IllegalArgumentException ex) {
            // Unusable or untrusted profile configuration: leave the default
            // PDFBox selection intact instead of using a shared temp folder.
        }
    }
}
