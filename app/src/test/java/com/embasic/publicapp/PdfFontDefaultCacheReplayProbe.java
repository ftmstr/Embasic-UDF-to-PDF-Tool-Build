package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Run ONLY with the opt-in multi-process script. Its class name intentionally
 * does not match Surefire's default *Test pattern: the regular suite must not
 * change user.home or write a real user's PDFBox default cache.
 *
 * Each explicit Surefire invocation is a fresh JVM. All source fonts are only
 * read from the OS; copied font bytes, cache metadata, and PDF remain in the
 * disposable process fixture. No native UYAP document is used.
 */
final class PdfFontDefaultCacheReplayProbe {
    private static final String TEXT = "SYNTHETIC-DEFAULT-FONTCACHE-REPLAY";

    private static String embeddedFontSha256(Path output) throws Exception {
        try (var pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(pdf).contains(TEXT));
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                var font = pdf.getPage(0).getResources().getFont(name);
                if (font.getFontDescriptor() != null
                        && font.getFontDescriptor().getFontFile2() != null) {
                    try (var in = font.getFontDescriptor()
                            .getFontFile2().createInputStream()) {
                        byte[] embedded = in.readAllBytes();
                        assertTrue(embedded.length > 200,
                                "An embedded TrueType subset is required");
                        return HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256").digest(embedded));
                    }
                }
            }
        }
        throw new AssertionError("The saved PDF must contain an embedded TrueType font");
    }

    @Test void defaultFallbackColdWarmCorruptionAndFontChangeAcrossProcessBoundaries()
            throws Exception {
        String fixtureText = System.getProperty("embasic.defaultcache.fixture", "");
        String phase = System.getProperty("embasic.defaultcache.phase", "");
        assertTrue(List.of("cold", "updated", "metadata-rebuild", "bad-font")
                .contains(phase), "Explicit script phase is required");
        Path home = Path.of(fixtureText).toRealPath();
        assertTrue(Files.isDirectory(home));
        assertTrue(home.getFileName().toString().startsWith("embasic-default-fontcache-"),
                "Never select a real home directory for this isolated test");
        assertNull(System.getProperty("pdfbox.fontcache"),
                "The test must actually exercise the PDFBox DEFAULT path");
        assertEquals("false", System.getProperty("pdfbox.fontcache.skipchecksums"),
                "PDFBox checksum verification must remain enabled");

        String originalHome = System.getProperty("user.home");
        String originalFont = System.getProperty("embasic.font.regular");
        Path fontCopy = home.resolve("original-font-copy.ttf");
        Path output = home.resolve("validated.pdf");
        Path oldSubset = home.resolve("cold-embedded.sha256");
        String beforeCache = System.getProperty("pdfbox.fontcache");
        try {
            // Override only within this disposable Surefire JVM. Do not
            // globally override a Maven process or write into the real home.
            System.setProperty("user.home", home.toString());
            Path regular = PdfExporter.systemFont(false);
            Path bold = PdfExporter.systemFont(true);
            assertFalse(Arrays.equals(Files.readAllBytes(regular),
                    Files.readAllBytes(bold)), "Fixtures must be distinct valid fonts");

            if (phase.equals("cold")) {
                assertFalse(Files.exists(output));
                Files.copy(regular, fontCopy);
            } else if (phase.equals("bad-font")) {
                assertTrue(Files.isRegularFile(output));
                Files.writeString(fontCopy, "SYNTHETIC BROKEN FONT SOURCE",
                        StandardCharsets.US_ASCII);
            } else {
                assertTrue(Files.isRegularFile(output));
                Files.copy(bold, fontCopy, StandardCopyOption.REPLACE_EXISTING);
            }

            System.setProperty("embasic.font.regular", fontCopy.toString());
            // Must leave PDFBox's cache path UNSET rather than smuggling an
            // explicit managed cache into an apparent fallback test.
            PdfBoxFontCacheDirectory.configureForDesktop(home,
                    home.resolve("UNTRUSTED-DIFFERENT-LOCATION"));
            assertNull(System.getProperty("pdfbox.fontcache"));
            var style = new UdfReader.Style(13, false, new Color(33, 81, 159));
            var paragraph = new UdfReader.Paragraph(List.of(
                    new UdfReader.Run(TEXT, style)));
            var doc = new UdfReader.DocumentSpec("public-synthetic.udf", "1.0",
                    List.of(paragraph));

            if (phase.equals("bad-font")) {
                byte[] prior = Files.readAllBytes(output);
                assertThrows(Exception.class,
                        () -> PdfExporter.save(List.of(doc), output, true));
                assertArrayEquals(prior, Files.readAllBytes(output),
                        "Corrupt replacement font must not overwrite validated PDF");
                try (var entries = Files.list(home)) {
                    assertEquals(0, entries.filter(path -> path.getFileName()
                            .toString().startsWith(".embasic-pending-")).count());
                }
                System.out.println("EMBASIC_DEFAULT_FONTCACHE phase=bad-font"
                        + " fallback=UNSET existingPdf=UNCHANGED"
                        + " source=REJECTED staging=0 checksums=ON");
                return;
            }

            // Force the first PDFBox renderer initialization in this JVM,
            // then produce the actual embedded-font PDF to be read by the
            // NEXT JVM. This intentionally crosses process/cache boundaries.
            var preview = PdfExporter.firstPage(doc);
            assertTrue(preview.getWidth() > 100 && preview.getHeight() > 100);
            assertEquals(1, PdfExporter.save(List.of(doc), output,
                    !phase.equals("cold")));
            String currentSubset = embeddedFontSha256(output);
            if (phase.equals("cold")) {
                Files.writeString(oldSubset, currentSubset, StandardCharsets.US_ASCII);
            } else {
                String originalSubset = Files.readString(oldSubset,
                        StandardCharsets.US_ASCII).trim();
                assertNotEquals(originalSubset, currentSubset,
                        "A valid replacement source at the same path must change"
                        + " the embedded PDF subset even across JVM processes");
            }
            assertNull(System.getProperty("pdfbox.fontcache"));
            assertEquals("false", System.getProperty("pdfbox.fontcache.skipchecksums"));
            System.out.println("EMBASIC_DEFAULT_FONTCACHE phase=" + phase
                    + " fallback=UNSET pdf=VALID embeddedFont=PASS"
                    + " changedSinceCold=" + (!phase.equals("cold"))
                    + " checksums=ON");
        } finally {
            if (originalHome == null) System.clearProperty("user.home");
            else System.setProperty("user.home", originalHome);
            if (originalFont == null) System.clearProperty("embasic.font.regular");
            else System.setProperty("embasic.font.regular", originalFont);
            assertEquals(beforeCache, System.getProperty("pdfbox.fontcache"));
        }
    }
}
