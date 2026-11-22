package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests default PDFBox behavior when the optional persistent profile is unsafe. */
final class PdfBoxFontCacheFallbackTest {
    @TempDir Path folder;

    @Test void rejectedProfileLeavesDefaultSelectionAndFullPdfIntact()
            throws Exception {
        Path home = Files.createDirectories(folder.resolve("isolated-home"));
        Path outside = Files.createDirectories(folder.resolve("untrusted-outside"));
        String existingCache = System.getProperty("pdfbox.fontcache");
        String checksums = System.getProperty("pdfbox.fontcache.skipchecksums");
        try {
            System.clearProperty("pdfbox.fontcache");
            assertTrue(PdfBoxFontCacheDirectory.prepare(home, outside).isEmpty());
            PdfBoxFontCacheDirectory.configureForDesktop(home, outside);
            assertNull(System.getProperty("pdfbox.fontcache"),
                    "Untrusted profile must leave PDFBox's default choice untouched");
            assertEquals(checksums, System.getProperty("pdfbox.fontcache.skipchecksums"));

            // Original rights-clean fixture: a four-color PDF PNG quadrant,
            // one vector table, and mixed regular/bold colored font runs.
            BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                source.setRGB(x, y, new Color(40, 165, 70).getRGB());
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(source, "PNG", png));
            var imagePage = new UdfReader.DocumentSpec("image-only.udf", "1.0",
                    List.of(), List.of(new DocumentElements.ImageBlock(
                            png.toByteArray(), 80f, 80f)), DocumentElements.PageSpec.a4());
            var paragraph = new UdfReader.Paragraph(List.of(
                    new UdfReader.Run("KIRMIZI", new UdfReader.Style(14, false, Color.RED)),
                    new UdfReader.Run(" MAVI", new UdfReader.Style(18, true, Color.BLUE))));
            var table = new DocumentElements.TableBlock(List.of(120f, 120f),
                    List.of(new DocumentElements.Row(List.of(
                            new DocumentElements.Cell("SOL", 1, 1),
                            new DocumentElements.Cell("SAG", 1, 1)), 32f)));
            var contentPage = new UdfReader.DocumentSpec("style-table.udf", "1.0",
                    List.of(paragraph), List.of(
                            new DocumentElements.ParagraphBlock(paragraph), table),
                    DocumentElements.PageSpec.a4());
            Path output = folder.resolve("safe-default.pdf");
            assertEquals(2, PdfExporter.save(List.of(imagePage, contentPage),
                    output, false));
            try (var pdf = Loader.loadPDF(output.toFile())) {
                assertEquals(2, pdf.getNumberOfPages());
                var raster = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
                Color sample = new Color(raster.getRGB(68, 68));
                assertAll(() -> assertEquals(40, sample.getRed(), 8),
                        () -> assertEquals(165, sample.getGreen(), 8),
                        () -> assertEquals(70, sample.getBlue(), 8));
                int images = 0;
                for (var name : pdf.getPage(0).getResources().getXObjectNames())
                    if (pdf.getPage(0).getResources().getXObject(name)
                            instanceof PDImageXObject) images++;
                assertEquals(1, images);
                String text = new PDFTextStripper().getText(pdf);
                for (String expected : List.of("KIRMIZI", "MAVI", "SOL", "SAG"))
                    assertTrue(text.contains(expected), "Missing synthetic text " + expected);
                boolean embeddedFont = false;
                for (var name : pdf.getPage(1).getResources().getFontNames()) {
                    var font = pdf.getPage(1).getResources().getFont(name);
                    if (font.getFontDescriptor() != null
                            && font.getFontDescriptor().getFontFile2() != null)
                        embeddedFont = true;
                }
                assertTrue(embeddedFont, "Default cache must not lose embedded fonts");
            }
            assertNull(System.getProperty("pdfbox.fontcache"));
            assertEquals(checksums, System.getProperty("pdfbox.fontcache.skipchecksums"));
            try (var entries = Files.list(folder)) {
                assertEquals(0, entries.filter(p -> p.getFileName().toString()
                        .startsWith(".embasic-pending-")).count());
            }
            System.out.println("EMBASIC_FONT_CACHE_FALLBACK injectedUnsafe=REJECTED"
                    + " defaultProperty=UNCHANGED pages=2 png=PASS"
                    + " font=EMBEDDED colorTextTable=PASS checksumSetting=UNCHANGED");
        } finally {
            if (existingCache == null) System.clearProperty("pdfbox.fontcache");
            else System.setProperty("pdfbox.fontcache", existingCache);
        }
    }

    @Test void operatorOverrideAndChecksumFlagNeverChangedByInvalidProfile()
            throws Exception {
        String oldCache = System.getProperty("pdfbox.fontcache");
        String oldChecksum = System.getProperty("pdfbox.fontcache.skipchecksums");
        String explicit = folder.resolve("operator-managed-location").toString();
        try {
            System.setProperty("pdfbox.fontcache", explicit);
            PdfBoxFontCacheDirectory.configureForDesktop(
                    folder.resolve("nonexistent-home"), folder.resolve("outside"));
            assertEquals(explicit, System.getProperty("pdfbox.fontcache"));
            assertEquals(oldChecksum, System.getProperty("pdfbox.fontcache.skipchecksums"));
            assertFalse(Files.exists(folder.resolve("operator-managed-location")));
        } finally {
            if (oldCache == null) System.clearProperty("pdfbox.fontcache");
            else System.setProperty("pdfbox.fontcache", oldCache);
        }
    }

    @Test void unsafeRealWindowsRunnerProfileNeverGetsCustomCacheWritten()
            throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows")) return;
        String homeText = System.getProperty("user.home", "");
        String localText = System.getenv("LOCALAPPDATA");
        if (homeText.isBlank() || localText == null || localText.isBlank()) {
            System.out.println("EMBASIC_FONT_CACHE_REAL_FALLBACK hostWindows=NOT_RUN"
                    + " reason=environment_missing");
            return;
        }
        Path home = Path.of(homeText).toAbsolutePath().normalize();
        Path local = Path.of(localText).toAbsolutePath().normalize();
        if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(local, LinkOption.NOFOLLOW_LINKS)) {
            System.out.println("EMBASIC_FONT_CACHE_REAL_FALLBACK hostWindows=NOT_RUN"
                    + " reason=profile_missing");
            return;
        }
        var owner = Files.getOwner(home, LinkOption.NOFOLLOW_LINKS);
        boolean unsafe = !owner.equals(Files.getOwner(local, LinkOption.NOFOLLOW_LINKS))
                || !PdfBoxFontCacheDirectory.trustedAcl(home, owner)
                || !PdfBoxFontCacheDirectory.trustedAcl(local, owner);
        if (!unsafe) {
            // Never let a JUnit fixture provision a real user's writable cache.
            System.out.println("EMBASIC_FONT_CACHE_REAL_FALLBACK hostWindows=NOT_RUN"
                    + " reason=unsafe_precondition_unproven");
            return;
        }
        Path realApp = local.resolve("Embasic-UDF-to-PDF");
        boolean existed = Files.exists(realApp, LinkOption.NOFOLLOW_LINKS);
        String before = System.getProperty("pdfbox.fontcache");
        try {
            System.clearProperty("pdfbox.fontcache");
            PdfBoxFontCacheDirectory.configureForDesktop(home, local);
            assertNull(System.getProperty("pdfbox.fontcache"),
                    "Unsafe actual runner profile must not set a persistent cache");
            assertEquals(existed, Files.exists(realApp, LinkOption.NOFOLLOW_LINKS),
                    "No real user cache directory may be created by this test");
            System.out.println("EMBASIC_FONT_CACHE_REAL_FALLBACK hostWindows=PASS"
                    + " unsafeProfile=REJECTED property=UNSET"
                    + " realUserWrites=NONE physicalGui=NOT_RUN");
        } finally {
            if (before == null) System.clearProperty("pdfbox.fontcache");
            else System.setProperty("pdfbox.fontcache", before);
        }
    }
}
