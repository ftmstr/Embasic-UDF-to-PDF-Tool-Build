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
