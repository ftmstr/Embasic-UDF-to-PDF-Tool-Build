package com.embasic.audit.openpdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Synthetic-only iText-shaped OpenPDF 2.0.x API compatibility smoke. */
public final class OpenPdfProbe {
    private OpenPdfProbe() {}

    private static void writePage(Path output, String marker) throws Exception {
        Document document = new Document(PageSize.A4);
        try (OutputStream out = Files.newOutputStream(output)) {
            PdfWriter.getInstance(document, out);
            document.open();
            Paragraph paragraph = new Paragraph(marker);
            paragraph.setAlignment(Element.ALIGN_CENTER);
            document.add(paragraph);
            PdfPTable table = new PdfPTable(2);
            table.addCell("ONE");
            table.addCell("TWO");
            document.add(table);
            document.close(); // Complete PDF trailer BEFORE closing the stream.
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private static void merge(Path a, Path b, Path output) throws Exception {
        Document merged = new Document();
        try (OutputStream outputStream = Files.newOutputStream(output)) {
            PdfCopy copy = new PdfCopy(merged, outputStream);
            merged.open();
            for (Path source : new Path[]{a, b}) {
                PdfReader reader = new PdfReader(Files.readAllBytes(source));
                try {
                    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                        copy.addPage(copy.getImportedPage(reader, page));
                    }
                } finally {
                    reader.close();
                }
            }
            merged.close(); // Complete cross-reference/trailer before stream close.
        } finally {
            if (merged.isOpen()) {
                merged.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("embasic-public-openpdf-probe-");
        try {
            Path a = folder.resolve("sample-a.pdf");
            Path b = folder.resolve("sample-b.pdf");
            Path merged = folder.resolve("merged.pdf");
            writePage(a, "EMBASIC-OPENPDF-SYNTHETIC-A");
            writePage(b, "EMBASIC-OPENPDF-SYNTHETIC-B");
            merge(a, b, merged);
            PdfReader reader = new PdfReader(Files.readAllBytes(merged));
            try {
                if (reader.getNumberOfPages() != 2) {
                    throw new IllegalStateException("SYNTHETIC_OPENPDF_PAGE_ORDER_FAILED");
                }
                if (Math.abs(reader.getPageSize(1).getWidth() - 595) > 2f) {
                    throw new IllegalStateException("SYNTHETIC_OPENPDF_PAGE_SIZE_FAILED");
                }
            } finally {
                reader.close();
            }
            System.out.println("OPENPDF_2_0_5_SYNTHETIC: PASS pages=2 table=true");
        } finally {
            try (Stream<Path> all = Files.walk(folder)) {
                for (Path path : all.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
