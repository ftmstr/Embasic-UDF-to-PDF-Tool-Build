package com.embasic.publicapp;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
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
        // An empty environment variable becomes the current working directory
        // through Path.of(""). Never place font metadata beside an executable.
        if (home == null || localAppData == null || home.toString().isBlank()
                || localAppData.toString().isBlank()
                || !home.isAbsolute() || !localAppData.isAbsolute())
            return Optional.empty();
        try {
            Path privateHome = home.toAbsolutePath().normalize();
            Path local = localAppData.toAbsolutePath().normalize();
            if (local.equals(privateHome) || !local.startsWith(privateHome)
                    || Files.isSymbolicLink(privateHome)
                    || Files.isSymbolicLink(local)
                    || !Files.isDirectory(privateHome, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(local, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isWritable(local)
                    || !canonicalWithoutAlias(privateHome)
                    || !canonicalWithoutAlias(local))
                return Optional.empty();

            // Reject aliases in every intermediate profile component: a lexical
            // startsWith check does not detect AppData junctions/symlinks.
            Path cursor = privateHome;
            for (Path component : privateHome.relativize(local)) {
                cursor = cursor.resolve(component);
                if (Files.isSymbolicLink(cursor)
                        || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)
                        || !canonicalWithoutAlias(cursor))
                    return Optional.empty();
            }

            // Cache metadata must remain in a folder owned by the same user
            // as the Windows home profile, never in a shared redirected path.
            var owner = Files.getOwner(privateHome, LinkOption.NOFOLLOW_LINKS);
            if (!owner.equals(Files.getOwner(local, LinkOption.NOFOLLOW_LINKS))
                    || !trustedAcl(privateHome, owner)
                    || !trustedAcl(local, owner))
                return Optional.empty();

            Path app = local.resolve(CHILD);
            if (Files.isSymbolicLink(app)) return Optional.empty();
            if (!Files.exists(app, LinkOption.NOFOLLOW_LINKS))
                Files.createDirectory(app);
            if (!Files.isDirectory(app, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isWritable(app)
                    || !canonicalWithoutAlias(app)
                    || !owner.equals(Files.getOwner(app, LinkOption.NOFOLLOW_LINKS))
                    || !trustedAcl(app, owner))
                return Optional.empty();

            // Never select a malicious alias or directory in the cache-file
            // position. A regular but corrupt cache is left for PDFBox's
            // built-in checksum and format validation to reject/rebuild.
            Path cache = app.resolve(".pdfbox.cache");
            if (Files.exists(cache, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isRegularFile(cache, LinkOption.NOFOLLOW_LINKS)
                        || !canonicalWithoutAlias(cache)
                        || !owner.equals(Files.getOwner(cache, LinkOption.NOFOLLOW_LINKS))
                        || !trustedAcl(cache, owner)))
                return Optional.empty();
            return Optional.of(app);
        } catch (IOException | SecurityException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Catch junction/reparse redirection even when Files.isSymbolicLink is false. */
    private static boolean canonicalWithoutAlias(Path path) throws IOException {
        return path.equals(path.toRealPath());
    }

    /**
     * Files.getOwner() alone is insufficient on NTFS: a different account may
     * have an inherited Allow/Write ACE. Reject that directory even if the
     * current user can write to it. A Linux provider has no Windows ACL here.
     */
    static boolean trustedAcl(Path path, UserPrincipal owner) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows")) return true;
        AclFileAttributeView view = Files.getFileAttributeView(
                path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) return false;
        for (AclEntry ace : view.getAcl()) {
            if (ace.type() == AclEntryType.ALLOW
                    && grantsMutation(ace.permissions())
                    && !trustedWriter(ace.principal().getName(), owner.getName()))
                return false;
        }
        return true;
    }

    /** Explicit allowlist; unknown write-capable principals fail closed. */
    static boolean trustedWriter(String name, String owner) {
        if (name == null || owner == null) return false;
        if (name.equalsIgnoreCase(owner)) return true;
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.equals("NT AUTHORITY\\SYSTEM")
                || upper.equals("BUILTIN\\ADMINISTRATORS")
                || upper.equals("CREATOR OWNER")
                || upper.equals("NT AUTHORITY\\CREATOR OWNER");
    }

    static boolean grantsMutation(Set<AclEntryPermission> permissions) {
        for (AclEntryPermission permission : permissions) {
            switch (permission) {
                case WRITE_DATA, APPEND_DATA, WRITE_NAMED_ATTRS,
                     WRITE_ATTRIBUTES, DELETE, DELETE_CHILD, WRITE_ACL,
                     WRITE_OWNER -> { return true; }
                default -> { }
            }
        }
        return false;
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
