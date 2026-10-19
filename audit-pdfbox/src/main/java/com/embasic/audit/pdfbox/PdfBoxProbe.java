package com.embasic.audit.pdfbox;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

/** Synthetic-only PDFBox 3.x smoke. No UDF logic, proprietary code or logo. */
public final class PdfBoxProbe {
    private static final String A = "EMBASIC-SYNTHETIC-PDFBOX-A";
    private static final String B = "EMBASIC-SYNTHETIC-PDFBOX-B";
    private static final String TURKISH = "İıĞğŞşÜüÖöÇç";

    private PdfBoxProbe() {}

    private static void writePage(Path output, String marker, float width,
                                  float height, boolean withImage) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(width, height));
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 13);
                content.newLineAtOffset(48, height - 64);
                content.showText(marker);
                content.endText();
                if (withImage) {
                    // The runner's installed TTF is used only in an ephemeral PDF.
                    // No font files or generated PDFs are committed or uploaded.
                    Path systemFont = Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
                    if (!Files.isRegularFile(systemFont)) {
                        systemFont = Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf");
                    }
                    if (!Files.isRegularFile(systemFont)) {
                        throw new IOException("SYNTHETIC_UNICODE_FONT_UNAVAILABLE");
                    }
                    PDType0Font unicodeFont = PDType0Font.load(doc, systemFont.toFile());
                    content.beginText();
                    content.setFont(unicodeFont, 12);
                    content.newLineAtOffset(48, height - 93);
                    content.showText(TURKISH);
                    content.endText();
                    BufferedImage fake = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < 10; y++) {
                        for (int x = 0; x < 10; x++) {
                            fake.setRGB(x, y, ((x + y) % 2 == 0 ? Color.BLUE : Color.GREEN).getRGB());
                        }
                    }
                    PDImageXObject picture = LosslessFactory.createFromImage(doc, fake);
                    content.drawImage(picture, 48, height - 145, 50, 50);
                }
            }
            doc.save(output.toFile());
        }
    }

    private static void require(boolean test, String reason) {
        if (!test) {
            throw new IllegalStateException("SYNTHETIC_PDFBOX_FAILED:" + reason);
        }
    }

    private static void verify(Path merged) throws IOException {
        try (PDDocument doc = Loader.loadPDF(merged.toFile())) {
            require(doc.getNumberOfPages() == 2, "pages");
            require(Math.abs(doc.getPage(0).getMediaBox().getWidth() - 595.28f) < .1f,
                    "page-1-width");
            require(Math.abs(doc.getPage(1).getMediaBox().getHeight() - 792f) < .1f,
                    "page-2-height");
            String text = new PDFTextStripper().getText(doc);
            require(text.contains(A) && text.contains(B), "text-missing");
            require(text.contains(TURKISH), "turkish-unicode-text-lost");
            require(text.indexOf(A) < text.indexOf(B), "wrong-order");
            require(doc.getPage(0).getResources().getXObjectNames().iterator().hasNext(),
                    "synthetic-image-missing");
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("embasic-public-pdfbox-probe-");
        try {
            Path a = dir.resolve("synthetic-a.pdf");
            Path b = dir.resolve("synthetic-b.pdf");
            Path result = dir.resolve("merged.pdf");
            writePage(a, A, 595.28f, 841.89f, true);
            writePage(b, B, 612f, 792f, false);
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.addSource(a.toFile());
            merger.addSource(b.toFile());
            merger.setDestinationFileName(result.toString());
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
            verify(result);
            require("2".equals(PdfQualityOracle.observed(result).get("pages")), "quality-oracle-read");
            System.out.println("PDFBOX_3_0_8_SYNTHETIC: PASS pages=2 order=true image=true turkish-unicode=true");
        } finally {
            try (Stream<Path> all = Files.walk(dir)) {
                for (Path path : all.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
