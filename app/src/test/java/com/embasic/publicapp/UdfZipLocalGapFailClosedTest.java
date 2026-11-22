package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rights-clean archive byte-coverage proof, not real UYAP UDF compatibility. */
final class UdfZipLocalGapFailClosedTest {
    @TempDir Path folder;
    private static final byte[] XML = ("<template format_id='1.0'>"
            + "<content>OK</content><elements><paragraph>"
            + "<content startOffset='0' length='2'/>"
            + "</paragraph></elements></template>").getBytes(StandardCharsets.UTF_8);

    private static int le32(byte[] bytes, int pos) {
        return ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
    private static void put32(byte[] bytes, int pos, int value) {
        ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
    }

    private Path archive(String name, boolean storedDirectory) throws Exception {
        Path file = folder.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            if (storedDirectory) {
                ZipEntry directory = new ZipEntry("media/");
                directory.setMethod(ZipEntry.STORED);
                directory.setSize(0);
                directory.setCompressedSize(0);
                directory.setCrc(0);
                zip.putNextEntry(directory);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(XML);
            zip.closeEntry();
        }
        return file;
    }

    private Path withGapBeforeCentralDirectory(String name, Path source) throws Exception {
        byte[] original = Files.readAllBytes(source);
        int oldEocd = original.length - 22;
        assertEquals(0x06054b50, le32(original, oldEocd));
        int oldCentral = le32(original, oldEocd + 16);
        byte[] omitted = "UNDECLARED CONTENT BETWEEN ZIP MEMBERS".getBytes(StandardCharsets.UTF_8);
        byte[] changed = new byte[original.length + omitted.length];
        System.arraycopy(original, 0, changed, 0, oldCentral);
        System.arraycopy(omitted, 0, changed, oldCentral, omitted.length);
        System.arraycopy(original, oldCentral, changed,
                oldCentral + omitted.length, original.length - oldCentral);
        put32(changed, changed.length - 22 + 16, oldCentral + omitted.length);
        assertEquals(changed.length - 22,
                le32(changed, changed.length - 22 + 16)
                        + le32(changed, changed.length - 22 + 12),
                "TUR430 EOCD-at-EOF check still passes");
        Path dest = folder.resolve(name);
        Files.write(dest, changed);
        return dest;
    }

    @Test void interstitialBytesInvisibleToBothJavaZipReadersAreRejected() throws Exception {
        Path source = archive("canonical.udf", false);
        assertEquals(1, UdfReader.read(source).blocks().size());
        Path withGap = withGapBeforeCentralDirectory("hidden-gap.udf", source);
        try (ZipFile central = new ZipFile(withGap.toFile());
             ZipInputStream local = new ZipInputStream(Files.newInputStream(withGap))) {
            assertEquals(1, central.size());
            assertNotNull(central.getEntry("content.xml"));
            assertEquals("content.xml", local.getNextEntry().getName());
            assertArrayEquals(XML, local.readAllBytes());
            assertNull(local.getNextEntry());
        }
        var error = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(withGap));
        assertTrue(error.getMessage().contains("açıklanmamış veri"), error.getMessage());
    }

    @Test void packedDeflateDescriptorsAndStoredDirectoryRemainSupported() throws Exception {
        // A STORED directory has no descriptor; a DEFLATED XML member has
        // ZipOutputStream's signed 16-byte descriptor. Both must remain valid.
        Path valid = archive("stored-and-deflated.udf", true);
        assertEquals(1, UdfReader.read(valid).blocks().size());
        Path gap = withGapBeforeCentralDirectory("stored-and-deflated-gap.udf", valid);
        var error = assertThrows(IllegalArgumentException.class, () -> UdfReader.read(gap));
        assertTrue(error.getMessage().contains("açıklanmamış veri"), error.getMessage());
    }

    @Test void signaturelessDeflateDataDescriptorRemainsSupported() throws Exception {
        Path original = archive("signed-descriptor.udf", false);
        byte[] bytes = Files.readAllBytes(original);
        int eocd = bytes.length - 22;
        int cen = le32(bytes, eocd + 16);
        assertEquals(0x08074b50, le32(bytes, cen - 16));
        byte[] unsigned = new byte[bytes.length - 4];
        System.arraycopy(bytes, 0, unsigned, 0, cen - 16);
        System.arraycopy(bytes, cen - 12, unsigned, cen - 16, bytes.length - (cen - 12));
        put32(unsigned, unsigned.length - 22 + 16, cen - 4);
        Path file = folder.resolve("unsigned-descriptor.udf");
        Files.write(file, unsigned);
        assertEquals(1, UdfReader.read(file).blocks().size(),
                "Optional ZIP data-descriptor signature must not be mandatory");
    }
}
