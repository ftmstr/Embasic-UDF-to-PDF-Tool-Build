package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rights-clean, independently generated Java ZIP per-entry comments.
 * Tests the explicitly restricted synthetic ZIP subset, not native UYAP.
 */
final class UdfZipEntryCommentFailClosedTest {
    @TempDir Path folder;
    private static final byte[] XML = ("<template format_id='1.0'>"
            + "<content>OK</content><elements><paragraph>"
            + "<content startOffset='0' length='2'/>"
            + "</paragraph></elements></template>").getBytes(StandardCharsets.UTF_8);

    private Path archive(String name, boolean xmlComment, boolean directoryComment)
            throws Exception {
        Path file = folder.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            ZipEntry xml = new ZipEntry("content.xml");
            if (xmlComment) xml.setComment("HIDDEN MEMBER-SPECIFIC SOURCE NOTE");
            zip.putNextEntry(xml);
            zip.write(XML);
            zip.closeEntry();

            ZipEntry directory = new ZipEntry("media/");
            directory.setMethod(ZipEntry.STORED);
            directory.setSize(0);
            directory.setCompressedSize(0);
            directory.setCrc(0);
            if (directoryComment) directory.setComment("HIDDEN MEDIA DIRECTORY NOTE");
            zip.putNextEntry(directory);
            zip.closeEntry();
        }
        return file;
    }

    @Test void centralXmlMemberCommentCannotDisappearDuringPdfConversion()
            throws Exception {
        Path accepted = archive("canonical.udf", false, false);
        assertEquals(1, UdfReader.read(accepted).blocks().size());
        Path commented = archive("commented-xml.udf", true, false);
        try (ZipFile central = new ZipFile(commented.toFile());
             ZipInputStream local = new ZipInputStream(Files.newInputStream(commented))) {
            ZipEntry center = central.getEntry("content.xml");
            assertEquals("HIDDEN MEMBER-SPECIFIC SOURCE NOTE", center.getComment());
            ZipEntry streamed = local.getNextEntry();
            assertEquals("content.xml", streamed.getName());
            assertNull(streamed.getComment(),
                    "ZipInputStream exposes no CENTRAL per-entry comment");
            assertArrayEquals(XML, central.getInputStream(center).readAllBytes());
            assertArrayEquals(XML, local.readAllBytes());
            assertEquals("media/", local.getNextEntry().getName());
        }
        var error = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(commented));
        assertTrue(error.getMessage().contains("ZIP giriş açıklaması"),
                error.getMessage());
    }

    @Test void directoryCommentsAlsoFailClosedWithoutRejectingNormalMarker()
            throws Exception {
        assertEquals(1, UdfReader.read(archive("normal-directory.udf",
                false, false)).blocks().size());
        Path commented = archive("commented-directory.udf", false, true);
        try (ZipFile zip = new ZipFile(commented.toFile())) {
            assertEquals("HIDDEN MEDIA DIRECTORY NOTE",
                    zip.getEntry("media/").getComment());
        }
        var error = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(commented));
        assertTrue(error.getMessage().contains("ZIP giriş açıklaması"),
                error.getMessage());
    }
}
