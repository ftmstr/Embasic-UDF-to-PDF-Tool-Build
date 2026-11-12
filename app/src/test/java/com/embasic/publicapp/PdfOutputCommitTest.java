package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression for a PDF target appearing after the initial overwrite check. */
final class PdfOutputCommitTest {
    @TempDir Path folder;

    @Test void stagedPageGeometryRejectsChangedCropRotationAndPageCount() throws Exception {
        // A successful PDF reload is insufficient: a silently changed page
        // crop, orientation or page count must prevent publication.
        try (PDDocument original = new PDDocument();
             PDDocument staged = new PDDocument()) {
            original.addPage(new PDPage(new PDRectangle(595.28f, 841.89f)));
            original.addPage(new PDPage(new PDRectangle(1008f, 612f)));
            staged.addPage(new PDPage(new PDRectangle(595.28f, 841.89f)));
            staged.addPage(new PDPage(new PDRectangle(1008f, 612f)));
            List<float[]> expected = PdfExporter.snapshotPageGeometry(original);
            assertDoesNotThrow(() -> PdfExporter.verifyStagedPageGeometry(expected, staged));
            staged.getPage(1).setCropBox(new PDRectangle(1007f, 612f));
            IOException crop = assertThrows(IOException.class,
                    () -> PdfExporter.verifyStagedPageGeometry(expected, staged));
            assertTrue(crop.getMessage().contains("2"));
            staged.getPage(1).setCropBox(new PDRectangle(1008f, 612f));
            staged.getPage(1).setRotation(90);
            assertThrows(IOException.class,
                    () -> PdfExporter.verifyStagedPageGeometry(expected, staged));
            staged.getPage(1).setRotation(0);
            staged.removePage(1);
            assertThrows(IOException.class,
                    () -> PdfExporter.verifyStagedPageGeometry(expected, staged));
        }
    }

    @Test void sameGeometryPageReorderingCannotPassStagedContentValidation() throws Exception {
        // Two same-size pages have indistinguishable MediaBox/CropBox/rotation.
        // Distinct synthetic drawing commands must still retain their order
        // after serialization and reload, before the PDF can be published.
        Path staged = folder.resolve("synthetic-same-geometry.pdf");
        try (PDDocument original = new PDDocument()) {
            for (int index = 0; index < 2; index++) {
                PDPage page = new PDPage(PDRectangle.A4);
                original.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(original, page)) {
                    float y = 90 + index * 40;
                    stream.moveTo(60, y);
                    stream.lineTo(160, y);
                    stream.stroke();
                }
            }
            List<float[]> expectedGeometry = PdfExporter.snapshotPageGeometry(original);
            List<String> expectedContents = PdfExporter.snapshotPageContentHashes(original);
            assertNotEquals(expectedContents.get(0), expectedContents.get(1));
            original.save(staged.toFile());
            try (PDDocument reloaded = Loader.loadPDF(staged.toFile())) {
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageGeometry(expectedGeometry, reloaded));
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageContentHashes(expectedContents, reloaded));
                PDPage first = reloaded.getPage(0);
                reloaded.removePage(0);
                reloaded.addPage(first);
                // The previous geometry-only guard cannot distinguish this swap.
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageGeometry(expectedGeometry, reloaded));
                IOException swapped = assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageContentHashes(expectedContents, reloaded));
                assertTrue(swapped.getMessage().contains("1"));
            }
        }
    }

    @Test void samePageCommandsCannotHideSwappedImageXObject() throws Exception {
        // The content stream still says /Im1 Do after the resource dictionary
        // redirects /Im1 to a different same-size image. A content SHA alone
        // cannot distinguish these two visually different PDFs.
        Path staged = folder.resolve("synthetic-image-resource.pdf");
        try (PDDocument original = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            original.addPage(page);
            BufferedImage red = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 2; x++) red.setRGB(x, y, Color.RED.getRGB());
            PDImageXObject firstImage = LosslessFactory.createFromImage(original, red);
            try (PDPageContentStream stream = new PDPageContentStream(original, page)) {
                stream.drawImage(firstImage, 48, 48, 20, 20);
            }
            List<String> expectedCommands = PdfExporter.snapshotPageContentHashes(original);
            List<List<String>> expectedImages = PdfExporter.snapshotPageImageResources(original);
            assertEquals(1, expectedImages.get(0).size());
            original.save(staged.toFile());
            try (PDDocument reloaded = Loader.loadPDF(staged.toFile())) {
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageContentHashes(
                        expectedCommands, reloaded));
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageImageResources(
                        expectedImages, reloaded));
                BufferedImage blue = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 2; y++)
                    for (int x = 0; x < 2; x++) blue.setRGB(x, y, Color.BLUE.getRGB());
                var resources = reloaded.getPage(0).getResources();
                var imageName = resources.getXObjectNames().iterator().next();
                resources.put(imageName, LosslessFactory.createFromImage(reloaded, blue));
                // The drawing commands and page geometry have not changed.
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageContentHashes(
                        expectedCommands, reloaded));
                IOException swapped = assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageImageResources(expectedImages, reloaded));
                assertTrue(swapped.getMessage().contains("1"));
            }
        }
    }

    @Test void sameAlphaSamplesCannotHideChangedSoftMaskGeometryOrDecode() throws Exception {
        // Synthetic 2x2 RGBA: unchanged /Im Do commands and alpha bytes do not
        // prove unchanged opacity if SMask width or Decode is redirected.
        Path staged = folder.resolve("synthetic-alpha-resource.pdf");
        try (PDDocument original = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            original.addPage(page);
            BufferedImage rgba = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 2; x++)
                    rgba.setRGB(x, y, new Color(30, 90, 180, 128).getRGB());
            PDImageXObject image = LosslessFactory.createFromImage(original, rgba);
            try (PDPageContentStream stream = new PDPageContentStream(original, page)) {
                stream.drawImage(image, 48, 48, 20, 20);
            }
            List<String> commands = PdfExporter.snapshotPageContentHashes(original);
            List<List<String>> expected = PdfExporter.snapshotPageImageResources(original);
            original.save(staged.toFile());
            try (PDDocument reloaded = Loader.loadPDF(staged.toFile())) {
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageImageResources(expected, reloaded));
                var resources = reloaded.getPage(0).getResources();
                var imageName = resources.getXObjectNames().iterator().next();
                var imageStream = resources.getXObject(imageName).getCOSObject();
                var mask = (org.apache.pdfbox.cos.COSStream) imageStream.getDictionaryObject(
                        org.apache.pdfbox.cos.COSName.SMASK);
                assertNotNull(mask, "Synthetic RGBA must embed a soft mask");
                int originalWidth = mask.getInt(org.apache.pdfbox.cos.COSName.WIDTH);
                mask.setInt(org.apache.pdfbox.cos.COSName.WIDTH, originalWidth + 1);
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageContentHashes(commands, reloaded));
                assertThrows(IOException.class,
                        () -> PdfExporter.verifyStagedPageImageResources(expected, reloaded));
                mask.setInt(org.apache.pdfbox.cos.COSName.WIDTH, originalWidth);
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageImageResources(expected, reloaded));
                var decodeKey = org.apache.pdfbox.cos.COSName.getPDFName("Decode");
                var originalDecode = mask.getDictionaryObject(decodeKey);
                var inverted = new org.apache.pdfbox.cos.COSArray();
                inverted.add(org.apache.pdfbox.cos.COSInteger.ONE);
                inverted.add(org.apache.pdfbox.cos.COSInteger.ZERO);
                mask.setItem(decodeKey, inverted);
                assertThrows(IOException.class,
                        () -> PdfExporter.verifyStagedPageImageResources(expected, reloaded));
                if (originalDecode == null) mask.removeItem(decodeKey);
                else mask.setItem(decodeKey, originalDecode);
                assertDoesNotThrow(() -> PdfExporter.verifyStagedPageImageResources(expected, reloaded));
            }
        }
    }

    @Test void finalizedFontProgramAndToUnicodeCannotBeSilentlyRedirected() throws Exception {
        // Synthetic text embeds a Type0 font. A /F1 Tf content command can
        // remain identical even if the font resource's CMap changes.
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("Public synthetic font 123", style)));
        var doc = new UdfReader.DocumentSpec("font-synthetic.udf", "1.0",
                List.of(paragraph));
        Path staged = folder.resolve("synthetic-font.pdf");
        try (PDDocument original = PdfExporter.render(List.of(doc))) {
            original.save(staged.toFile());
            // PDFBox materializes the subset and ToUnicode during save().
            List<List<String>> expected = PdfExporter.snapshotPageFontResources(original);
            assertEquals(1, expected.size());
            assertFalse(expected.get(0).isEmpty());
            try (PDDocument reloaded = Loader.loadPDF(staged.toFile())) {
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded));
                var resources = reloaded.getPage(0).getResources();
                var fontName = resources.getFontNames().iterator().next();
                var dictionary = resources.getFont(fontName).getCOSObject();
                var unicodeName = org.apache.pdfbox.cos.COSName.TO_UNICODE;
                var descendants = (org.apache.pdfbox.cos.COSArray) dictionary.getDictionaryObject(
                        org.apache.pdfbox.cos.COSName.DESCENDANT_FONTS);
                var descendant = (org.apache.pdfbox.cos.COSDictionary) descendants.getObject(0);
                var widthKey = org.apache.pdfbox.cos.COSName.getPDFName("W");
                var originalWidths = descendant.getDictionaryObject(widthKey);
                var replacementWidths = new org.apache.pdfbox.cos.COSArray();
                replacementWidths.add(org.apache.pdfbox.cos.COSInteger.get(1));
                var newAdvances = new org.apache.pdfbox.cos.COSArray();
                newAdvances.add(org.apache.pdfbox.cos.COSInteger.get(999));
                replacementWidths.add(newAdvances);
                descendant.setItem(widthKey, replacementWidths);
                assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded),
                        "Changed glyph advances must not survive staging");
                if (originalWidths == null) descendant.removeItem(widthKey);
                else descendant.setItem(widthKey, originalWidths);
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded));

                var cidKey = org.apache.pdfbox.cos.COSName.getPDFName("CIDToGIDMap");
                var originalMap = descendant.getDictionaryObject(cidKey);
                var alteredMap = reloaded.getDocument().createCOSStream();
                try (var output = alteredMap.createOutputStream()) {
                    output.write(new byte[]{0, 0, 0, 7});
                }
                descendant.setItem(cidKey, alteredMap);
                assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded),
                        "Swapped CID-to-glyph mapping must not survive staging");
                if (originalMap == null) descendant.removeItem(cidKey);
                else descendant.setItem(cidKey, originalMap);
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded));

                // The embedded font program and CID widths can stay unchanged
                // while descriptor ascent alters glyph geometry or clipping.
                var descriptor = (org.apache.pdfbox.cos.COSDictionary)
                        descendant.getDictionaryObject(org.apache.pdfbox.cos.COSName.FONT_DESC);
                var ascentKey = org.apache.pdfbox.cos.COSName.getPDFName("Ascent");
                var originalAscent = descriptor.getDictionaryObject(ascentKey);
                descriptor.setItem(ascentKey, org.apache.pdfbox.cos.COSInteger.get(-999999));
                assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded),
                        "Changed font descriptor ascent must not survive staging");
                if (originalAscent == null) descriptor.removeItem(ascentKey);
                else descriptor.setItem(ascentKey, originalAscent);
                assertDoesNotThrow(() ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded));

                // A different CMap must not pass merely because the PDF's
                // page geometry and /F1 drawing command are unchanged.
                var replacement = reloaded.getDocument().createCOSStream();
                try (var output = replacement.createOutputStream()) {
                    output.write("different synthetic CMap".getBytes(StandardCharsets.US_ASCII));
                }
                dictionary.setItem(unicodeName, replacement);
                IOException changed = assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded));
                assertTrue(changed.getMessage().contains("1"));
                dictionary.removeItem(unicodeName);
                assertThrows(IOException.class, () ->
                        PdfExporter.verifyStagedPageFontResources(expected, reloaded));
            }
        }
    }

    @Test void lateCreatedTargetCannotBeClobberedWithoutConsent() throws Exception {
        Path staging = folder.resolve(".embasic-pending.pdf");
        Path target = folder.resolve("case.pdf");
        Files.writeString(staging, "VALIDATED-PDF", StandardCharsets.UTF_8);
        // Simulates another process creating the target while PDF rendering runs.
        Files.writeString(target, "KEEP-ORIGINAL", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.publishValidated(staging, target, false));
        assertEquals("KEEP-ORIGINAL", Files.readString(target));
        assertEquals("VALIDATED-PDF", Files.readString(staging));
    }

    @Test void approvedOverwriteCanReplaceExistingTarget() throws Exception {
        Path staging = folder.resolve(".embasic-pending.pdf");
        Path target = folder.resolve("case.pdf");
        Files.writeString(staging, "VALIDATED-PDF", StandardCharsets.UTF_8);
        Files.writeString(target, "OLD-PDF", StandardCharsets.UTF_8);
        PdfExporter.publishValidated(staging, target, true);
        assertEquals("VALIDATED-PDF", Files.readString(target));
        assertFalse(Files.exists(staging));
    }

    @Test void newTargetIsCreatedWithoutOverwriteConsent() throws Exception {
        Path staging = folder.resolve(".embasic-pending.pdf");
        Path target = folder.resolve("new.pdf");
        Files.writeString(staging, "VALIDATED-PDF", StandardCharsets.UTF_8);
        PdfExporter.publishValidated(staging, target, false);
        assertEquals("VALIDATED-PDF", Files.readString(target));
        assertFalse(Files.exists(staging));
    }

    @Test void oversizedBatchFailsBeforeCreatingAnyPdfStagingFile() throws Exception {
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Synthetic", style)));
        var doc = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(paragraph));
        Path target = folder.resolve("too-many.pdf");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(java.util.Collections.nCopies(FileImportPolicy.MAX_CARDS + 1, doc),
                        target, false));
        assertTrue(error.getMessage().contains("160"));
        assertFalse(Files.exists(target));
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.count(), "Rejected batch must leave no staging file");
        }
    }

    @Test void invalidDestinationFailsBeforeSourceHashingOrPdfStaging() throws Exception {
        // A failed output choice must not read 160 sources or wait for font
        // discovery. The source deliberately does not exist: output validation
        // must be the first filesystem operation in the GUI save worker.
        Path missing = folder.resolve("missing.udf");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Synthetic", style)));
        var doc = new UdfReader.DocumentSpec("missing.udf", "1.0", List.of(paragraph));
        IllegalArgumentException extension = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(missing), List.of(doc),
                        List.of("not-a-fingerprint"), folder.resolve("invalid.txt"), false));
        assertTrue(extension.getMessage().contains(".pdf"));

        // The save dialog may select an existing directory whose name ends in
        // .pdf. Even with overwrite consent, never stage a PDF against it.
        Path directory = Files.createDirectory(folder.resolve("directory.pdf"));
        IllegalArgumentException nonFile = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(missing), List.of(doc),
                        List.of("not-a-fingerprint"), directory, true));
        assertTrue(nonFile.getMessage().contains("normal bir PDF"));
        assertTrue(Files.isDirectory(directory));
        // A PDF-looking symlink is not a regular output destination. Windows
        // runners without symlink privileges skip only this filesystem branch;
        // Linux still exercises it without touching any real user documents.
        Path actual = Files.writeString(folder.resolve("actual.pdf"), "KEEP-LINK-TARGET");
        Path linked = folder.resolve("linked.pdf");
        boolean symlinkAvailable;
        try {
            Files.createSymbolicLink(linked, actual.getFileName());
            symlinkAvailable = true;
        } catch (java.io.IOException | UnsupportedOperationException | SecurityException unavailable) {
            symlinkAvailable = false;
        }
        if (symlinkAvailable) {
            for (boolean overwrite : new boolean[]{false, true}) {
                IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                        () -> DesktopApp.saveWorkspace(List.of(missing), List.of(doc),
                                List.of("not-a-fingerprint"), linked, overwrite));
                assertTrue(rejected.getMessage().contains("sembolik bağlantı"));
            }
            Path staging = Files.writeString(folder.resolve("validated-stage.pdf"), "VALIDATED");
            assertThrows(IllegalArgumentException.class,
                    () -> PdfExporter.publishValidated(staging, linked, true));
            assertEquals("VALIDATED", Files.readString(staging));
            assertEquals("KEEP-LINK-TARGET", Files.readString(actual));
            assertTrue(Files.isSymbolicLink(linked));
        }
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count());
        }
    }

    @Test void latePhysicalAliasCannotOverwriteExistingOutput() throws Exception {
        // Two independent, byte-identical sources pass the initial fingerprint
        // gate. Replacing the second path with a hard link during rendering
        // must fail the final physical-identity check even though SHA stays equal.
        Path first = Files.writeString(folder.resolve("first.udf"), "synthetic shared bytes");
        Path second = Files.writeString(folder.resolve("second.udf"), "synthetic shared bytes");
        List<Path> sources = List.of(first, second);
        String fingerprint = FileImportPolicy.sourceSha256(first);
        List<String> fingerprints = List.of(fingerprint, fingerprint);
        assertDoesNotThrow(() -> DesktopApp.verifySourceFingerprints(sources, fingerprints));
        Path target = Files.writeString(folder.resolve("existing.pdf"), "KEEP-ORIGINAL");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Public synthetic", style)));
        var doc = new UdfReader.DocumentSpec("first.udf", "1.0", List.of(paragraph));

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(doc, doc), target, true, () -> {
                    Files.delete(second);
                    Files.createLink(second, first);
                    assertEquals(fingerprint, FileImportPolicy.sourceSha256(second));
                    DesktopApp.verifySourceFingerprints(sources, fingerprints);
                }));
        assertTrue(duplicate.getMessage().contains("yinelenen"));
        assertEquals("KEEP-ORIGINAL", Files.readString(target));
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count());
        }
    }

    @Test void externalPdfEditDuringRenderCannotBeSilentlyOverwritten() throws Exception {
        Path target = Files.writeString(folder.resolve("existing.pdf"), "USER-APPROVED-OLD");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("Synthetic public document", style)));
        var doc = new UdfReader.DocumentSpec("synthetic.udf", "1.0", List.of(paragraph));

        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(doc), target, true, () -> {
                    // Deterministic external edit after PDF staging and validation.
                    Files.writeString(target, "EXTERNAL-NEWER-PDF");
                }));
        assertTrue(changed.getMessage().contains("yeniden onaylayın"));
        assertEquals("EXTERNAL-NEWER-PDF", Files.readString(target));
        // A new, explicit overwrite attempt against the now-current target
        // must still succeed: the guard is optimistic, not a permanent lock.
        assertEquals(1, PdfExporter.save(List.of(doc), target, true));
        byte[] pdfBytes = Files.readAllBytes(target);
        assertEquals("%PDF", new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII));
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count(),
                    "Rejected overwrite must remove its staged PDF");
        }
    }

    @Test void sourceMutationDuringPdfRenderCannotOverwriteExistingOutput() throws Exception {
        // Deterministic late mutation: this callback runs only after PDFBox
        // staged and re-opened a valid PDF, immediately before publication.
        Path source = Files.writeString(folder.resolve("source.udf"), "synthetic initial bytes");
        String originalFingerprint = FileImportPolicy.sourceSha256(source);
        Path target = Files.writeString(folder.resolve("existing.pdf"), "KEEP-ORIGINAL");
        var style = new UdfReader.Style(11, false, Color.BLACK);
        var paragraph = new UdfReader.Paragraph(List.of(new UdfReader.Run("Public synthetic", style)));
        var doc = new UdfReader.DocumentSpec("source.udf", "1.0", List.of(paragraph));
        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> PdfExporter.save(List.of(doc), target, true, () -> {
                    Files.writeString(source, "synthetic changed bytes");
                    DesktopApp.verifySourceFingerprints(List.of(source), List.of(originalFingerprint));
                }));
        assertTrue(changed.getMessage().contains("değiştirildi"));
        assertEquals("KEEP-ORIGINAL", Files.readString(target),
                "A changed source must not replace a previously saved PDF");
        try (var entries = Files.list(folder)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".embasic-pending-")).count(),
                    "Rejected staged PDF must be cleaned up");
        }
    }
}
