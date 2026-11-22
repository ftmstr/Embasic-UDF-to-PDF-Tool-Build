import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * TUR216: inspect the bytes written by the real packaged Windows EXE.
 * Only a RUNNER_TEMP, rights-clean, two-document synthetic output is accepted.
 * This is not a native UYAP format/equivalence test or a source-code hook.
 */
public final class PackagedPdfOrderProbe {
    private PackagedPdfOrderProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected synthetic output PDF");
        Path pdf = Path.of(args[0]).toAbsolutePath().normalize();
        if (!pdf.getFileName().toString().equals("tur216-reordered.pdf")
                || !Files.isRegularFile(pdf) || Files.size(pdf) < 200) {
            throw new IllegalArgumentException("Expected nonempty TUR216 synthetic output PDF");
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            if (doc.isEncrypted() || doc.getNumberOfPages() != 2)
                throw new IllegalStateException("Physical save did not produce exactly two plain pages");
            String[] expected = {"BRAVO-TWO", "ALPHA-ONE"};
            for (int index = 0; index < 2; index++) {
                PDRectangle media = doc.getPage(index).getMediaBox();
                PDRectangle crop = doc.getPage(index).getCropBox();
                if (media == null || crop == null
                        || Math.abs(media.getWidth() - 595.28f) > .15f
                        || Math.abs(media.getHeight() - 841.89f) > .15f
                        || Math.abs(crop.getWidth() - media.getWidth()) > .15f
                        || Math.abs(crop.getHeight() - media.getHeight()) > .15f
                        || doc.getPage(index).getRotation() != 0) {
                    throw new IllegalStateException("Unexpected A4 geometry on synthetic page " + (index + 1));
                }
                PDFTextStripper text = new PDFTextStripper();
                text.setStartPage(index + 1);
                text.setEndPage(index + 1);
                String extracted = text.getText(doc);
                if (!extracted.contains(expected[index])
                        || extracted.contains(expected[1 - index])) {
                    throw new IllegalStateException("Physical keyboard reorder differs from PDF page " + (index + 1));
                }
            }
        }
        System.out.println("PACKAGED-PDF-ORDER-PASS: physical Ctrl+S, 2 ordered synthetic A4 pages; no native UYAP claim.");
    }
}
