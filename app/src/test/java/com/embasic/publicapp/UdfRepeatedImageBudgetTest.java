package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Original, rights-clean compressed PNG references. A small ZIP can contain
 * thousands of image USES, each previously decoded and cloned independently.
 * No native UYAP fixtures, no memory-exhaustion stress run.
 */
final class UdfRepeatedImageBudgetTest {
    @TempDir Path folder;

    private static byte[] png(int width, int height, boolean noisy) throws Exception {
        BufferedImage raster = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(428L);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                raster.setRGB(x, y, noisy ? random.nextInt(0x1000000) : 0x1177AA);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(raster, "PNG", out));
        assertTrue(out.size() <= 1_048_576, "PNG must meet existing per-image budget");
        return out.toByteArray();
    }

    private Path udf(String name, byte[] png, int imageUses) throws Exception {
        String image = "<image ref='media/one.png' mime='image/png' width='12' height='12'/>";
        String xml = "<template format_id='1.0'><content></content><elements>"
                + image.repeat(imageUses) + "</elements></template>";
        Path path = folder.resolve(name);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("content.xml", xml.getBytes(StandardCharsets.UTF_8));
        entries.put("media/one.png", png);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return path;
    }

    @Test void repeatedZipPngReferencesAreBoundedBeforeThousandsOfImageDecodes()
            throws Exception {
        byte[] image = png(8, 8, false);
        var valid = UdfReader.read(udf("sixty-four.udf", image, 64));
        assertEquals(64, valid.blocks().size(), "64 legitimate repeated uses remain supported");
        var rejected = udf("sixty-five.udf", image, 65);
        var error = assertThrows(IllegalArgumentException.class, () -> UdfReader.read(rejected));
        assertTrue(error.getMessage().contains("görsel kullanım"), error.getMessage());

        // 2,048 XML image references fit within the 4 MiB XML limit and the
        // 65,536-node SAX budget. They must not trigger thousands of PNG
        // decodes or retained clones; the 65th use is rejected.
        var flood = udf("two-thousand.udf", image, 2048);
        assertTrue(Files.size(flood) < 64 * 1024);
        assertThrows(IllegalArgumentException.class, () -> UdfReader.read(flood));
    }

    @Test void encodedMediaBudgetCountsReuseNotJustUniqueZipEntries()
            throws Exception {
        // One distinct ~0.75 MiB random RGB PNG is legal under ZIP 8 MiB;
        // each reference previously retained an independent byte[] clone.
        byte[] image = png(512, 512, true);
        assertTrue(image.length > 500_000 && image.length < 1_048_576,
                "Fixture must expose real repeated-media amplification");
        int fits = (8 * 1024 * 1024) / image.length;
        assertTrue(fits > 4 && fits < 64);
        var valid = UdfReader.read(udf("encoded-budget-fit.udf", image, fits));
        assertEquals(fits, valid.blocks().size());
        var tooMany = udf("encoded-budget-exceeded.udf", image, fits + 1);
        var error = assertThrows(IllegalArgumentException.class, () -> UdfReader.read(tooMany));
        assertTrue(error.getMessage().contains("toplam kullanılan PNG"), error.getMessage());
    }
}
