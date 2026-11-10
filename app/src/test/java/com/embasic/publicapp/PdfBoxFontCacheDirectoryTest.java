package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Safeguards selecting the native Windows PDFBox font metadata cache path.
 * These tests never delete, trust, or modify PDFBox checksums themselves.
 * They are not a claim of cold-start acceleration on a physical Windows PC.
 */
final class PdfBoxFontCacheDirectoryTest {
    @TempDir Path sandbox;

    private Path home() throws Exception {
        return Files.createDirectories(sandbox.resolve("profile"));
    }

    private Path local(Path home) throws Exception {
        return Files.createDirectories(home.resolve("AppData").resolve("Local"));
    }

    @Test void cachesInStableWritableUserProfileAndNeverModifiesExistingMetadata()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Path preferred = PdfBoxFontCacheDirectory.prepare(home, local).orElseThrow();
        assertEquals(local.resolve("Embasic-UDF-to-PDF"), preferred);
        assertTrue(Files.isDirectory(preferred));
        // Corrupt metadata must be revalidated by PDFBox at render time.
        // Merely selecting a directory must neither silently bless its
        // contents nor erase the file to conceal an invalid cache.
        Path metadata = Files.writeString(preferred.resolve(".pdfbox.cache"),
                "SYNTHETIC INVALID CACHE DATA", StandardCharsets.UTF_8);
        byte[] invalid = Files.readAllBytes(metadata);
        assertEquals(Optional.of(preferred),
                PdfBoxFontCacheDirectory.prepare(home, local));
        assertArrayEquals(invalid, Files.readAllBytes(metadata),
                "Application must not rewrite or 'repair' PDFBox cache bytes");
    }

    @Test void rejectsDirectoriesOutsideProfileOrNonDirectoryOrMissingHome()
            throws Exception {
        Path home = home();
        Path local = local(home);
        assertTrue(PdfBoxFontCacheDirectory.prepare(home, local).isPresent());
        Path outside = Files.createDirectory(sandbox.resolve("shared"));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, outside),
                "Never store font metadata in an arbitrary shared directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(null, local));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, null));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                sandbox.resolve("not-present"), local));
        Path file = Files.writeString(home.resolve("wrong-kind"), "not-directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, file),
                "A file is not a writable directory, regardless of its extension");
    }

    @Test void rejectsExistingCacheAliasAndDirectoryInsteadOfOverwriting()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Path preferred = PdfBoxFontCacheDirectory.prepare(home, local).orElseThrow();
        Path metadata = Files.createDirectory(preferred.resolve(".pdfbox.cache"));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
        assertTrue(Files.isDirectory(metadata), "Failed selection cannot mutate user data");
        Files.delete(metadata);

        Path real = Files.writeString(preferred.resolve("backing.txt"), "KEEP ORIGINAL");
        boolean linked = false;
        try {
            Files.createSymbolicLink(metadata, real.getFileName());
            linked = true;
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
            assertEquals("KEEP ORIGINAL", Files.readString(real));
            assertTrue(Files.isSymbolicLink(metadata));
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException
                | SecurityException notPermittedOnWindows) {
            // Standard hosted Windows accounts may not have symlink privilege.
        } finally {
            if (linked) Files.delete(metadata);
        }
        assertEquals(Optional.of(preferred),
                PdfBoxFontCacheDirectory.prepare(home, local));
    }

    @Test void rejectsSymlinkedApplicationDirectoryAndFallsBackWithoutTouchingTarget()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Path external = Files.createDirectory(local.resolve("external-store"));
        Path app = local.resolve("Embasic-UDF-to-PDF");
        boolean linked = false;
        try {
            Files.createSymbolicLink(app, external.getFileName());
            linked = true;
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
            assertTrue(Files.isDirectory(external));
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException
                | SecurityException notPermittedOnWindows) {
            // Symlink permission varies on Windows runners.
        } finally {
            if (linked) Files.delete(app);
        }
        assertFalse(Files.exists(app));
    }

    @Test void rejectsIntermediateProfileSymlinkWithoutWritingOutsideProfile()
            throws Exception {
        Path home = home();
        Path external = Files.createDirectories(sandbox.resolve("external").resolve("Local"));
        Path alias = home.resolve("AppData");
        boolean linked = false;
        try {
            Files.createSymbolicLink(alias, external.getParent());
            linked = true;
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, alias.resolve("Local")),
                    "Intermediate symlink must not redirect the PDFBox cache");
            assertFalse(Files.exists(external.resolve("Embasic-UDF-to-PDF")));
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException
                | SecurityException notPermittedOnWindows) {
            // Windows runner may not permit creating symbolic links.
        } finally {
            if (linked) Files.delete(alias);
        }
    }

    @Test void emptyRelativeOrHomeRootMustNeverBeUsedAsWindowsCache()
            throws Exception {
        Path home = home();
        Path local = local(home);
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                Path.of(""), local),
                "A missing user.home must not resolve to the process directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                home, Path.of("")),
                "A missing LOCALAPPDATA must never become the working directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                home, home),
                "The cache must be a dedicated subdirectory, not the profile root");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                Path.of("relative-home"), local));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                home, Path.of("relative-local")));
    }

    @Test void syntheticUntrustedWritePermissionsAreRejectedRegardlessOfOwner() {
        String owner = "LAPTOP\\ExampleUser";
        for (String principal : java.util.List.of(
                "Everyone", "NT AUTHORITY\\Authenticated Users",
                "BUILTIN\\Users", "OTHER\\Guest", "LOCAL\\SomeoneElse")) {
            assertFalse(PdfBoxFontCacheDirectory.trustedWriter(principal, owner),
                    "Reject untrusted Windows writer: " + principal);
        }
        for (String trusted : java.util.List.of(
                owner, "nt authority\\system", "BUILTIN\\Administrators",
                "CREATOR OWNER")) {
            assertTrue(PdfBoxFontCacheDirectory.trustedWriter(trusted, owner));
        }
        assertFalse(PdfBoxFontCacheDirectory.trustedWriter(null, owner));
        assertFalse(PdfBoxFontCacheDirectory.trustedWriter(owner, null));
        assertFalse(PdfBoxFontCacheDirectory.grantsMutation(
                java.util.Set.of(java.nio.file.attribute.AclEntryPermission.READ_DATA,
                        java.nio.file.attribute.AclEntryPermission.READ_CONTROL)));
        for (var mutation : java.util.List.of(
                java.nio.file.attribute.AclEntryPermission.WRITE_DATA,
                java.nio.file.attribute.AclEntryPermission.APPEND_DATA,
                java.nio.file.attribute.AclEntryPermission.WRITE_ACL,
                java.nio.file.attribute.AclEntryPermission.WRITE_OWNER,
                java.nio.file.attribute.AclEntryPermission.DELETE_CHILD)) {
            assertTrue(PdfBoxFontCacheDirectory.grantsMutation(
                    java.util.Set.of(mutation)),
                    "Must reject write-capable ACE " + mutation);
        }
    }

    @Test void windowsNativeAclViewIsReadWithoutChangingProfilePermissions()
            throws Exception {
        if (!System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
            return; // This is Windows-only evidence, not physical GUI acceptance.
        Path home = home();
        Path local = local(home);
        var view = Files.getFileAttributeView(local,
                java.nio.file.attribute.AclFileAttributeView.class);
        assertNotNull(view, "Windows NTFS ACL view must exist to select a trusted cache");
        var owner = Files.getOwner(home);
        assertNotNull(owner);
        var entries = view.getAcl();
        assertFalse(entries.isEmpty());
        boolean trusted = PdfBoxFontCacheDirectory.trustedAcl(local, owner);
        // If inherited runner permissions allow another account to write,
        // refusing to use the cache is the correct fail-closed behavior.
        if (!trusted) {
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
        } else {
            assertTrue(PdfBoxFontCacheDirectory.prepare(home, local).isPresent(),
                    "A trusted local test profile should be eligible");
        }
        System.out.println("EMBASIC_FONT_CACHE_ACL nativeWindows=PASS"
                + " trusted=" + trusted + " metadataAltered=false");
    }

    @Test void explicitPdfBoxPropertyIsNotOverwrittenByAutomaticSelection()
            throws Exception {
        String original = System.getProperty("pdfbox.fontcache");
        String requested = sandbox.resolve("operator-managed").toString();
        try {
            System.setProperty("pdfbox.fontcache", requested);
            PdfBoxFontCacheDirectory.configureForDesktop();
            assertEquals(requested, System.getProperty("pdfbox.fontcache"),
                    "Existing operator choice is not replaced unexpectedly");
        } finally {
            if (original == null) System.clearProperty("pdfbox.fontcache");
            else System.setProperty("pdfbox.fontcache", original);
        }
    }
}
