package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rights-clean Java 21 ZIP source-fidelity regression, no native UYAP fixture. */
final class UdfZipTrailerFailClosedTest {
    @TempDir Path folder;

    private Path archive(String name, String comment) throws Exception {
        Path file = folder.resolve(name);
        String xml = "<template format_id='1.0'><content>VISIBLE</content>"
                + "<elements><paragraph><content startOffset='0' length='7'/>"
                + "</paragraph></elements></template>";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            if (comment != null) zip.setComment(comment);
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    @Test void appendedBytesIgnoredByBothJavaZipReadersMustFailClosed() throws Exception {
        Path source = archive("appended.udf", null);
        assertEquals(1, UdfReader.read(source).blocks().size(),
                "Canonical ZipOutputStream UDF remains accepted");
        byte[] extra = "DOCUMENT DATA AFTER ZIP EOCD".getBytes(StandardCharsets.UTF_8);
        Files.write(source, extra, StandardOpenOption.APPEND);
        // This tests a real Java ZIP behavior, not a guessed vulnerability:
        // both readers return identical valid XML and silently ignore suffix.
        try (ZipFile central = new ZipFile(source.toFile());
             ZipInputStream local = new ZipInputStream(Files.newInputStream(source))) {
            assertEquals(1, central.size());
            assertNotNull(central.getEntry("content.xml"));
            assertEquals("content.xml", local.getNextEntry().getName());
            assertTrue(new String(local.readAllBytes(), StandardCharsets.UTF_8)
                    .contains("VISIBLE"));
            assertNull(local.getNextEntry());
        }
        var error = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(source));
        assertTrue(error.getMessage().contains("ZIP son kaydı"), error.getMessage());
    }

    @Test void unsupportedZipArchiveCommentCannotDisappearFromPdf() throws Exception {
        Path commented = archive("comment.udf", "UNREPRESENTED DOCUMENT COMMENT");
        try (ZipFile central = new ZipFile(commented.toFile())) {
            assertEquals("UNREPRESENTED DOCUMENT COMMENT", central.getComment());
        }
        var error = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(commented));
        assertTrue(error.getMessage().contains("ZIP son kaydı"), error.getMessage());
    }

    @Test void invalidPrefixAndCentralDirectoryOffsetRejectWithoutChangingValidZip()
            throws Exception {
        Path valid = archive("canonical.udf", null);
        assertEquals(1, UdfReader.read(valid).blocks().size());
        byte[] original = Files.readAllBytes(valid);
        byte[] prefixed = new byte[original.length + 4];
        System.arraycopy(new byte[]{14, 18, 28, 42}, 0, prefixed, 0, 4);
        System.arraycopy(original, 0, prefixed, 4, original.length);
        Path prefix = folder.resolve("prefix.udf");
        Files.write(prefix, prefixed);
        assertThrows(IllegalArgumentException.class, () -> UdfReader.read(prefix));

        // Corrupt only the last EOCD directory offset; the existing central
        // ZIP local/central payload hashes are otherwise untouched.
        byte[] invalidOffset = original.clone();
        invalidOffset[invalidOffset.length - 6] ^= 1;
        Path wrong = folder.resolve("wrong-central-offset.udf");
        Files.write(wrong, invalidOffset);
        assertThrows(IllegalArgumentException.class, () -> UdfReader.read(wrong));
    }
}
