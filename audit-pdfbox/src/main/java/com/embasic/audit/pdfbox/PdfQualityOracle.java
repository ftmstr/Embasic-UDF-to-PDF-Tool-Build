package com.embasic.audit.pdfbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Read-only PDF quality oracle for locally approved goldens.
 *
 * Only plain, independently reviewed manifest values are accepted. The
 * verifier never prints PDF text, input filenames, font files, or hashes.
 * Does not assert legal rights, UDF format support or visual fidelity.
 */
public final class PdfQualityOracle {
    private static final long MAX_PDF_BYTES = 250L * 1024L * 1024L;
    private static final long MAX_MANIFEST_BYTES = 128 * 1024;
    private static final int MAX_PAGES = 500;
    private static final float SIZE_TOLERANCE_PT = 0.2f;
    private PdfQualityOracle() {}

    private static void ensure(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException("PDF_QUALITY: BLOCKED (" + code + ")");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >>> 4) & 15, 16));
                hex.append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private static String normalized(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static Map<String, String> pageRecord(PDDocument document, int pageNumber)
            throws IOException {
        PDPage page = document.getPage(pageNumber - 1);
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String text = normalized(stripper.getText(document));
        int images = 0;
        int embedded = 0;
        PDResources resources = page.getResources();
        if (resources != null) {
            for (COSName name : resources.getXObjectNames()) {
                PDXObject object = resources.getXObject(name);
                if (object instanceof PDImageXObject) {
                    images++;
                }
            }
            for (COSName name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                if (font != null && font.isEmbedded()) {
                    embedded++;
                }
            }
        }
        String prefix = "page." + pageNumber + ".";
        Map<String, String> record = new HashMap<>();
        record.put(prefix + "width", Float.toString(page.getMediaBox().getWidth()));
        record.put(prefix + "height", Float.toString(page.getMediaBox().getHeight()));
        record.put(prefix + "text.sha256", sha256(text));
        record.put(prefix + "images.min", Integer.toString(images));
        record.put(prefix + "fonts.embedded.min", Integer.toString(embedded));
        return record;
    }

    static Map<String, String> observed(Path pdf) throws IOException {
        ensure(Files.isRegularFile(pdf, LinkOption.NOFOLLOW_LINKS), "pdf_missing_or_symlink");
        ensure(Files.size(pdf) > 0 && Files.size(pdf) <= MAX_PDF_BYTES, "pdf_size_limit");
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            int count = document.getNumberOfPages();
            ensure(count > 0 && count <= MAX_PAGES, "pdf_page_count_limit");
            Map<String, String> all = new HashMap<>();
            all.put("pages", Integer.toString(count));
            for (int n = 1; n <= count; n++) {
                all.putAll(pageRecord(document, n));
            }
            return all;
        }
    }

    static Map<String, String> parseManifest(Path manifest) throws IOException {
        ensure(Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS),
                "manifest_missing_or_symlink");
        ensure(Files.size(manifest) > 0 && Files.size(manifest) <= MAX_MANIFEST_BYTES,
                "manifest_size_limit");
        Map<String, String> result = new HashMap<>();
        for (String raw : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf('=');
            ensure(split > 0 && split == line.lastIndexOf('='), "manifest_syntax");
            String key = line.substring(0, split).trim();
            String val = line.substring(split + 1).trim();
            ensure(key.matches("(?:pages|page\\.[1-9][0-9]*\\.(?:width|height|text\\.sha256|images\\.min|fonts\\.embedded\\.min))"),
                    "manifest_key");
            ensure(!val.isEmpty() && !result.containsKey(key), "manifest_duplicate_or_empty");
            result.put(key, val);
        }
        return result;
    }

    static int verify(Path pdf, Map<String, String> manifest) throws IOException {
        ensure(manifest != null, "manifest_null");
        Map<String, String> expected = observed(pdf);
        ensure(manifest.keySet().equals(expected.keySet()), "manifest_file_set");
        ensure(expected.get("pages").equals(manifest.get("pages")), "page_count_mismatch");
        int count = Integer.parseInt(expected.get("pages"));
        for (int n = 1; n <= count; n++) {
            String prefix = "page." + n + ".";
            for (String dim : new String[] {"width", "height"}) {
                float expectedValue;
                try {
                    expectedValue = Float.parseFloat(manifest.get(prefix + dim));
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("PDF_QUALITY: BLOCKED (page_dimension_invalid)");
                }
                ensure(Float.isFinite(expectedValue) && expectedValue > 0, "page_dimension_invalid");
                float observedValue = Float.parseFloat(expected.get(prefix + dim));
                ensure(Math.abs(expectedValue - observedValue) <= SIZE_TOLERANCE_PT,
                        "page_dimension_mismatch");
            }
            String expectedHash = manifest.get(prefix + "text.sha256");
            ensure(expectedHash.matches("[0-9a-f]{64}"), "text_hash_invalid");
            ensure(expectedHash.equals(expected.get(prefix + "text.sha256")), "text_hash_mismatch");
            for (String metric : new String[] {"images.min", "fonts.embedded.min"}) {
                int min;
                try {
                    min = Integer.parseInt(manifest.get(prefix + metric));
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("PDF_QUALITY: BLOCKED (resource_count_invalid)");
                }
                ensure(min >= 0 && min <= 10000, "resource_count_invalid");
                int actual = Integer.parseInt(expected.get(prefix + metric));
                ensure(actual >= min, "resource_count_shortfall");
            }
        }
        return count;
    }

    public static void main(String[] args) {
        try {
            ensure(args.length == 2, "usage_requires_private_pdf_and_manifest");
            Path pdf = Path.of(args[0]);
            Path manifest = Path.of(args[1]);
            int pages = verify(pdf, parseManifest(manifest));
            System.out.println("PDF_QUALITY_ORACLE: PASS pages=" + pages);
        } catch (Exception e) {
            // No private file path, content, extracted text or hash in logs.
            String status = e instanceof IllegalStateException
                    && e.getMessage() != null && e.getMessage().startsWith("PDF_QUALITY:")
                    ? e.getMessage() : "PDF_QUALITY: BLOCKED (unreadable_or_invalid_pdf)";
            System.err.println(status);
            System.exit(2);
        }
    }
}
