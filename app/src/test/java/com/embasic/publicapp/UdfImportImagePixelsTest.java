package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Synthetic, license-clean 4000x1000 grayscale PNGs with tiny compressed IDAT.
 * No authentic/native UYAP data and no deliberate OOM workload.
 */
final class UdfImportImagePixelsTest {
    @TempDir Path folder;

    private static byte[] chunk(String type, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(payload.length).array());
        out.writeBytes(name);
        out.writeBytes(payload);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return out.toByteArray();
    }

    private static byte[] grayscaleFourMegapixels() {
        int width = 4000, height = 1000;
        // Filter None, gray zero: 4.001 MiB raw, highly compressible,
        // and well below existing per-PNG 1MiB compressed byte budget.
        byte[] rows = new byte[(width + 1) * height];
        Deflater deflater = new Deflater(9);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try {
            deflater.setInput(rows);
            deflater.finish();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                assertTrue(n > 0, "Deflater must make progress");
                compressed.write(buffer, 0, n);
            }
        } finally {
            deflater.end();
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        png.writeBytes(chunk("IHDR", ByteBuffer.allocate(13)
                .putInt(width).putInt(height).put((byte) 8).put((byte) 0)
                .put((byte) 0).put((byte) 0).put((byte) 0).array()));
        png.writeBytes(chunk("IDAT", compressed.toByteArray()));
        png.writeBytes(chunk("IEND", new byte[0]));
        assertTrue(png.size() < 64 * 1024);
        return png.toByteArray();
    }

    private Path archive(String filename, byte[] png, int images,
                         boolean firstImageHasBrokenIdat) throws Exception {
        StringBuilder xml = new StringBuilder(
                "<template format_id='1.0'><content></content><elements>");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < images; i++) {
            String name = "media/image" + i + ".png";
            xml.append("<image ref='").append(name)
                    .append("' mime='image/png' width='40' height='40'/>");
            // Distinct ZIP entries, not the repeated-reference defect already
            // fixed in TUR428. A 33-byte IHDR-only first PNG is deliberately
            // not decodable, proving the aggregate budget runs BEFORE any
            // raster decoding when five valid IHDRs exceed the total.
            entries.put(name, i == 0 && firstImageHasBrokenIdat
                    ? Arrays.copyOf(png, 33) : png);
        }
        xml.append("</elements></template>");
        entries.put("content.xml", xml.toString().getBytes(StandardCharsets.UTF_8));
        Path file = folder.resolve(filename);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test void distinctCompressedImagesMustPassImportPixelBudgetBeforeAnyRasterDecode()
            throws Exception {
        byte[] png = grayscaleFourMegapixels();
        assertEquals(4_000_000L, DocumentElements.pngPixelCount(png));
        var validFour = UdfReader.read(archive("four-distinct.udf", png, 4, false));
        assertEquals(4, validFour.blocks().size(), "Exactly 16MP remains legal");
        assertTrue(Files.size(archive("five-distinct.udf", png, 5, false)) < 64 * 1024);
        var rejected = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(folder.resolve("five-distinct.udf")));
        assertTrue(rejected.getMessage().contains("toplam çözümlenmiş PNG piksel"),
                rejected.getMessage());
        var rejectsBeforeRaster = assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(archive("five-broken-first.udf", png, 5, true)));
        assertTrue(rejectsBeforeRaster.getMessage().contains("toplam çözümlenmiş PNG piksel"),
                "All IHDR budgets must be checked before decoding first invalid IDAT");
    }
}
