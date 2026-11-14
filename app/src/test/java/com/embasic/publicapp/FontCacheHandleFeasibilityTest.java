package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Capability experiment: Java's directory-relative handle semantics cannot
 * silently be assumed on Windows or substituted into PDFBox's path-based API.
 * No native DLL, JNA, private executable or system directory is involved.
 */
final class FontCacheHandleFeasibilityTest {
    @TempDir Path isolated;

    @Test void openDirectoryRelativeHandleCapabilityIsReportedWithoutGuessing()
            throws Exception {
        Path original = Files.createDirectory(isolated.resolve("original-directory"));
        Files.writeString(original.resolve("marker.txt"), "TRUSTED-SYNTHETIC");
        boolean windows = System.getProperty("os.name", "").toLowerCase(
                java.util.Locale.ROOT).startsWith("windows");
        try (DirectoryStream<Path> opened = Files.newDirectoryStream(original)) {
            if (!(opened instanceof SecureDirectoryStream<Path> secure)) {
                System.out.println("EMBASIC_FONT_CACHE_HANDLE_FEASIBILITY"
                        + " javaSecureDirectoryStream=UNAVAILABLE"
                        + " openHandleAtomicUse=NOT_PROVEN"
                        + " windows=" + windows
                        + " nativeBridge=NOT_IMPLEMENTED");
                return;
            }
            Path renamed = isolated.resolve("moved-directory");
            try {
                Files.move(original, renamed);
            } catch (IOException notSupportedByFileSystem) {
                System.out.println("EMBASIC_FONT_CACHE_HANDLE_FEASIBILITY"
                        + " javaSecureDirectoryStream=AVAILABLE"
                        + " renameWhileOpen=NOT_RUN"
                        + " openHandleAtomicUse=NOT_PROVEN"
                        + " nativeBridge=NOT_IMPLEMENTED");
                return;
            }
            Files.createDirectory(original);
            Files.writeString(original.resolve("marker.txt"), "REPLACED-SYNTHETIC");
            try (var channel = secure.newByteChannel(Path.of("marker.txt"),
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                ByteBuffer buffer = ByteBuffer.allocate(64);
                int read = channel.read(buffer);
                assertTrue(read > 0);
                buffer.flip();
                assertEquals("TRUSTED-SYNTHETIC",
                        StandardCharsets.UTF_8.decode(buffer).toString(),
                        "An open relative handle must not follow the replaced path");
            }
            assertEquals("REPLACED-SYNTHETIC",
                    Files.readString(original.resolve("marker.txt")));
            System.out.println("EMBASIC_FONT_CACHE_HANDLE_FEASIBILITY"
                    + " javaSecureDirectoryStream=AVAILABLE"
                    + " renamedDirectoryRelativeRead=PASS"
                    + " pdfboxPathApiAtomicity=NOT_PROVEN"
                    + " nativeBridge=NOT_IMPLEMENTED");
        }
    }
}
