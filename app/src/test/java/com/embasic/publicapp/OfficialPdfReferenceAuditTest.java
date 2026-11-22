package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Original rights-clean synthetic PDF fixtures; no UYAP source/assets needed. */
final class OfficialPdfReferenceAuditTest {
    @TempDir Path folder;

    private Path pdf(String name, PDRectangle paper, String text,
                     Color square, int pages) throws IOException {
        Path output = folder.resolve(name);
        try (PDDocument pdf = new PDDocument()) {
            for (int index = 0; index < pages; index++) {
                PDPage page = new PDPage(paper);
                pdf.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    stream.setNonStrokingColor(square);
                    stream.addRect(50, 60 + index * 20, 90, 55);
                    stream.fill();
                    stream.setNonStrokingColor(Color.BLACK);
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(60, 190);
                    stream.showText(text + index);
                    stream.endText();
                }
            }
            pdf.save(output.toFile());
        }
        return output;
    }

    @Test void separatelySerializedIdenticalSyntheticPagesMatch() throws Exception {
        Path expected = pdf("reference.pdf", PDRectangle.A4, "SYNTHETIC", Color.BLUE, 2);
        Path candidate = pdf("candidate.pdf", PDRectangle.A4, "SYNTHETIC", Color.BLUE, 2);
        OfficialPdfReferenceAudit.Result result = OfficialPdfReferenceAudit.audit(expected, candidate);
        assertTrue(result.matches());
        assertEquals(2, result.referencePages());
        assertEquals(2, result.candidatePages());
        assertEquals(2, result.pages().size());
        assertEquals(0, result.differentPixels());
        assertTrue(result.pages().stream().allMatch(x -> x.renderedPixels() > 0));
        System.out.println("EMBASIC_TUR211_OFFLINE_REFERENCE identicalSynthetic=PASS"
                + " pages=2 text=PARITY geometry=PARITY pixels=PARITY noNativeUyap=true");
    }

    @Test void changedRasterCannotHideBehindEqualTextAndGeometry() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 1);
        Path candidate = pdf("out.pdf", PDRectangle.A4, "SYNTHETIC", Color.BLUE, 1);
        var result = OfficialPdfReferenceAudit.audit(expected, candidate);
        assertFalse(result.matches());
        assertEquals(1, result.pages().size());
        var page = result.pages().get(0);
        assertTrue(page.geometryMatches());
        assertTrue(page.extractedTextMatches());
        assertTrue(page.differentPixels() > 100);
        assertTrue(page.maximumChannelDifference() > 8);
    }

    @Test void changedTextFailsEvenWithSimilarPageLayout() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "ALPHA", Color.BLUE, 1);
        Path candidate = pdf("out.pdf", PDRectangle.A4, "BETA", Color.BLUE, 1);
        var result = OfficialPdfReferenceAudit.audit(expected, candidate);
        assertFalse(result.matches());
        assertTrue(result.pages().get(0).geometryMatches());
        assertFalse(result.pages().get(0).extractedTextMatches());
    }

    @Test void pageCountAndMediaBoxDifferenceNeverPass() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 2);
        Path fewer = pdf("few.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 1);
        Path legal = pdf("legal.pdf", new PDRectangle(1008, 612),
                "SYNTHETIC", Color.RED, 2);
        var count = OfficialPdfReferenceAudit.audit(expected, fewer);
        assertFalse(count.matches());
        assertEquals(2, count.referencePages());
        assertEquals(1, count.candidatePages());
        var geometry = OfficialPdfReferenceAudit.audit(expected, legal);
        assertFalse(geometry.matches());
        assertTrue(geometry.pages().stream().noneMatch(x -> x.geometryMatches()));
        assertTrue(geometry.pages().stream().allMatch(x -> x.renderedPixels() == 0));
    }

    @Test void samePhysicalFileAndNonPdfAreRejectedWithoutWritingFiles() throws Exception {
        Path expected = pdf("ref.pdf", PDRectangle.A4, "SYNTHETIC", Color.RED, 1);
        byte[] before = Files.readAllBytes(expected);
        assertThrows(IOException.class, () -> OfficialPdfReferenceAudit.audit(expected, expected));
        Path bad = folder.resolve("malformed.pdf");
        Files.write(bad, new byte[]{0, 1, 2, 3});
        assertThrows(IOException.class, () -> OfficialPdfReferenceAudit.audit(expected, bad));
        assertArrayEquals(before, Files.readAllBytes(expected));
        try (var listed = Files.list(folder)) {
            assertEquals(2L, listed.count(), "Audit must not create staging or user-content logs");
        }
    }

    @Test void invisibleLinkAndCatalogActionCannotProduceFalseParity() throws Exception {
        Path reference = pdf("reference.pdf", PDRectangle.A4, "STATIC", Color.BLUE, 1);
        Path plain = pdf("plain.pdf", PDRectangle.A4, "STATIC", Color.BLUE, 1);
        byte[] referenceBytes = Files.readAllBytes(reference);
        byte[] plainBytes = Files.readAllBytes(plain);
        assertTrue(OfficialPdfReferenceAudit.audit(reference, plain).matches());

        Path linkPdf = folder.resolve("hidden-link.pdf");
        try (PDDocument document = Loader.loadPDF(plain.toFile())) {
            COSDictionary invisibleLink = new COSDictionary();
            invisibleLink.setItem(COSName.getPDFName("Type"), COSName.getPDFName("Annot"));
            invisibleLink.setItem(COSName.getPDFName("Subtype"), COSName.getPDFName("Link"));
            COSArray rect = new COSArray();
            for (int coordinate : new int[]{0, 0, 1, 1})
                rect.add(COSInteger.get(coordinate));
            invisibleLink.setItem(COSName.getPDFName("Rect"), rect);
            COSArray annotations = new COSArray();
            annotations.add(invisibleLink);
            document.getPage(0).getCOSObject().setItem(
                    COSName.getPDFName("Annots"), annotations);
            document.save(linkPdf.toFile());
        }
        byte[] linkBytes = Files.readAllBytes(linkPdf);
        assertThrows(IOException.class,
                () -> OfficialPdfReferenceAudit.audit(reference, linkPdf));
        assertThrows(IOException.class,
                () -> OfficialPdfReferenceAudit.audit(linkPdf, reference));
        assertArrayEquals(linkBytes, Files.readAllBytes(linkPdf));

        Path actionPdf = folder.resolve("catalog-action.pdf");
        try (PDDocument document = Loader.loadPDF(plain.toFile())) {
            COSDictionary action = new COSDictionary();
            action.setName(COSName.getPDFName("S"), "Named");
            action.setName(COSName.getPDFName("N"), "NextPage");
            document.getDocumentCatalog().getCOSObject().setItem(
                    COSName.getPDFName("OpenAction"), action);
            document.save(actionPdf.toFile());
        }
        assertThrows(IOException.class,
                () -> OfficialPdfReferenceAudit.audit(reference, actionPdf));
        assertThrows(IOException.class,
                () -> OfficialPdfReferenceAudit.audit(actionPdf, reference));
        assertArrayEquals(referenceBytes, Files.readAllBytes(reference));
        assertArrayEquals(plainBytes, Files.readAllBytes(plain));
        System.out.println("EMBASIC_TUR212_STATIC_PDF_GATE hiddenLink=REJECT"
                + " catalogAction=REJECT noNativeUyap=true");
    }

    @Test void physicalPageScaleAndPrintBoxesCannotHideBehindEqualRaster() throws Exception {
        Path reference = pdf("reference.pdf", PDRectangle.A4, "PRINT", Color.BLUE, 1);
        Path baseline = pdf("baseline.pdf", PDRectangle.A4, "PRINT", Color.BLUE, 1);
        byte[] original = Files.readAllBytes(reference);
        assertTrue(OfficialPdfReferenceAudit.audit(reference, baseline).matches());

        // The same page content and MediaBox have different physical size
        // when /UserUnit differs. PDF raster and extracted text may still match.
        Path scaled = folder.resolve("scaled.pdf");
        try (PDDocument document = Loader.loadPDF(baseline.toFile())) {
            document.getPage(0).getCOSObject().setFloat(
                    COSName.getPDFName("UserUnit"), 2.0f);
            document.save(scaled.toFile());
        }
        var unit = OfficialPdfReferenceAudit.audit(reference, scaled);
        assertFalse(unit.matches());
        assertFalse(unit.pages().get(0).geometryMatches());
        assertEquals(0, unit.pages().get(0).renderedPixels());

        // Print trim is a production-page boundary, not necessarily visible
        // in an ordinary on-screen render.
        Path trimmed = folder.resolve("trimmed.pdf");
        try (PDDocument document = Loader.loadPDF(baseline.toFile())) {
            document.getPage(0).setTrimBox(new PDRectangle(30, 40, 400, 600));
            document.save(trimmed.toFile());
        }
        var trim = OfficialPdfReferenceAudit.audit(reference, trimmed);
        assertFalse(trim.matches());
        assertFalse(trim.pages().get(0).geometryMatches());
        assertEquals(0, trim.pages().get(0).renderedPixels());
        assertArrayEquals(original, Files.readAllBytes(reference));
        System.out.println("EMBASIC_TUR213_PRINT_GEOMETRY userUnit=DIFFERENCE"
                + " trimBox=DIFFERENCE sourceUnchanged=true noNativeUyap=true");
    }

    @Test void hugePageGeometryFailsBeforeAllocatingRasterMemory() throws Exception {
        PDRectangle huge = new PDRectangle(12000, 12000);
        Path expected = pdf("ref.pdf", huge, "SYNTHETIC", Color.RED, 1);
        Path candidate = pdf("candidate.pdf", huge, "SYNTHETIC", Color.RED, 1);
        assertThrows(IOException.class, () -> OfficialPdfReferenceAudit.audit(expected, candidate));
    }
}
