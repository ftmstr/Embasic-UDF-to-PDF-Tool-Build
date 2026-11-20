package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Privacy-safe, CI-only font/preview timing probe. Records observed durations,
 * never enforces a machine-dependent performance limit or claims Robot coverage.
 */
final class PdfFontFirstUseProfileTest {
    private static UdfReader.DocumentSpec mixed() throws Exception {
        BufferedImage pixels = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < pixels.getHeight(); y++)
            for (int x = 0; x < pixels.getWidth(); x++)
                pixels.setRGB(x, y, new Color(55, 170, 65).getRGB());
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, "PNG", encoded));
        var regular = new UdfReader.Style(14, false, new Color(200, 30, 35));
        var bold = new UdfReader.Style(18, true, new Color(25, 50, 205));
        var paragraph = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("REGULAR ", regular),
                new UdfReader.Run("BOLD-UNIQUE", bold)));
        return new UdfReader.DocumentSpec("rights-clean-cold-font.udf", "1.0",
                List.of(paragraph), List.of(
                    new DocumentElements.ImageBlock(encoded.toByteArray(), 80, 80),
                    new DocumentElements.ParagraphBlock(paragraph)),
                DocumentElements.PageSpec.a4());
    }

    @Test void coldAndRepeatedPreviewEmitLayoutRasterAndFontTimingWithoutChangingPixels()
            throws Exception {
        var fixture = mixed();
        // The existing diagnostics flag is enabled only during this one
        // deterministic JUnit test, then restored for the rest of the suite.
        String originalFlag = System.getProperty("embasic.diagnostics.fontTiming");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        BufferedImage first, repeated;
        long coldStarted = System.nanoTime();
        long coldFinished;
        long warmFinished;
        try (PrintStream capture = new PrintStream(evidence, true,
                StandardCharsets.UTF_8)) {
            System.setProperty("embasic.diagnostics.fontTiming", "true");
            System.setErr(capture);
            try {
                first = PdfExporter.firstPage(fixture);
                coldFinished = System.nanoTime();
                repeated = PdfExporter.firstPage(fixture);
                warmFinished = System.nanoTime();
            } finally {
                System.setErr(originalErr);
                if (originalFlag == null) System.clearProperty("embasic.diagnostics.fontTiming");
                else System.setProperty("embasic.diagnostics.fontTiming", originalFlag);
            }
        }
        assertEquals(first.getWidth(), repeated.getWidth());
        assertEquals(first.getHeight(), repeated.getHeight());
        // Independent 40 DPI sample: 80pt PNG from 48pt page margin.
        assertEquals(first.getRGB(35, 35), repeated.getRGB(35, 35));
        Color sample = new Color(first.getRGB(35, 35));
        assertAll("Synthetic PNG must remain intact when font timings are enabled",
                () -> assertEquals(55, sample.getRed(), 6),
                () -> assertEquals(170, sample.getGreen(), 6),
                () -> assertEquals(65, sample.getBlue(), 6));

        List<String> messages = new ArrayList<>();
        for (String line : evidence.toString(StandardCharsets.UTF_8).split("\\R"))
            if (line.startsWith("EMBASIC_TIMING ")) messages.add(line);
        assertEquals(2, messages.stream().filter(s ->
                s.startsWith("EMBASIC_TIMING font=regular ")).count(),
                "Each independent thumbnail PDF embeds its own regular font");
        assertEquals(2, messages.stream().filter(s ->
                s.startsWith("EMBASIC_TIMING font=bold ")).count(),
                "Each independent thumbnail PDF embeds its own bold font");
        for (String key : List.of("preview_layout_ms=", "preview_raster_ms=",
                "preview_total_ms=")) {
            assertEquals(2, messages.stream().filter(s ->
                    s.startsWith("EMBASIC_TIMING " + key)).count(), key);
        }
        // Output is limited to timing keys and numbers; no source paths or
        // contents. Survives GitHub-hosted Windows/Linux CI log collection.
        for (String message : messages) {
            assertTrue(message.matches("EMBASIC_TIMING (?:font=(?:regular|bold) "
                    + "lookup_ms=\\d+ load_ms=\\d+|preview_(?:layout|raster|total)_ms=\\d+)"),
                    "Diagnostic output must contain only sanitized metrics: " + message);
            System.out.println(message);
        }
        System.out.printf("EMBASIC_FONT_PROFILE cold_preview_ms=%d "
                        + "repeat_preview_ms=%d pixel_parity=PASS%n",
                (coldFinished - coldStarted) / 1_000_000,
                (warmFinished - coldFinished) / 1_000_000);
        try (PDDocument pdf = PdfExporter.render(List.of(fixture))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("REGULAR"));
            assertTrue(text.contains("BOLD-UNIQUE"));
            assertEquals(1, pdf.getNumberOfPages());
        }
    }
}
