package com.embasic.publicapp;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.CRC32;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.ext.DefaultHandler2;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Explicitly limited, independent UDF ZIP/XML reader. No proprietary UYAP code.
 * Unsupported nodes fail closed; a successful parse does not mean UYAP-equivalent rendering.
 */
public final class UdfReader {
    private static final int MAX_ENTRIES = 2048;
    private static final int MAX_CONTENT_XML = 4 * 1024 * 1024;
    private static final int MAX_XML_DEPTH = 128;
    private static final int MAX_XML_NODES = 65_536;
    private static final int MAX_XML_ATTRIBUTES = 256;
    private static final long MAX_TOTAL_UNCOMPRESSED = 32L * 1024 * 1024;
    private static final int MAX_MEDIA_FILES = 64;
    private static final int MAX_MEDIA_TOTAL = 8 * 1024 * 1024;
    private static final int MAX_MEDIA_PNG = 1_048_576;
    // TUR428: ZIP storage budgets only count distinct files. A single 1 MiB
    // PNG referenced thousands of times was decoded and cloned once per XML
    // image node, potentially exhausting heap before PDF export's pixel gate.
    private static final int MAX_IMAGE_BLOCKS = 64;
    private static final long MAX_RENDERED_IMAGE_BYTES = MAX_MEDIA_TOTAL;
    // TUR429: the compressed reference-byte budget does not bound raster
    // decoding during UDF import. Align with PDF export's global 16MP gate,
    // but reject BEFORE decoding even the first image of an oversized UDF.
    private static final long MAX_IMPORT_IMAGE_PIXELS = 16_000_000L;
    public record Style(float size, boolean bold, Color color) {}
    public record Run(String text, Style style) {}
    public record Paragraph(List<Run> runs) {
        public Paragraph { runs = List.copyOf(runs); }
    }
    public record DocumentSpec(String fileName, String formatId, List<Paragraph> paragraphs,
                               List<DocumentElements.Block> blocks, DocumentElements.PageSpec page) {
        public DocumentSpec {
            paragraphs = List.copyOf(paragraphs);
            blocks = List.copyOf(blocks);
            if (page == null || blocks.isEmpty()) throw new IllegalArgumentException("Boş belge veya sayfa");
        }
        public DocumentSpec(String name, String format, List<Paragraph> oldParagraphs) {
            this(name, format, oldParagraphs,
                oldParagraphs.stream().<DocumentElements.Block>map(DocumentElements.ParagraphBlock::new).toList(),
                DocumentElements.PageSpec.a4());
        }
    }
    private static final Style DEFAULT = new Style(11, false, Color.BLACK);
    private UdfReader() {}

    public static DocumentSpec read(Path file) throws Exception {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)
                || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".udf"))
            throw new IllegalArgumentException("Geçerli, okunabilir bir .udf dosyası seçin");
        // Direct reader callers must share the GUI/CLI compressed-size limit.
        // Reject oversized archives before ZIP central-directory processing.
        FileImportPolicy.requireBoundedArchive(file);
        // ZipFile and ZipInputStream both ignore data beyond a valid EOCD.
        // The synthetic UDF contract has no semantics for archive trailers,
        // self-extracting prefixes or ZIP comments; silently dropping them
        // while producing a successful PDF would misrepresent input bytes.
        long centralOffset = requireCanonicalZipEnvelope(file);
        byte[] data = null;
        long total = 0;
        int mediaTotal = 0;
        Map<String,byte[]> media = new HashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            // ZipFile trusts CENTRAL names, whereas other ZIP readers trust LOCAL
            // headers. A mismatch can reclassify a source XML or image without
            // changing its payload or CRC; reject such ambiguous archives.
            requireConsistentLocalZipRecords(file, zip, centralOffset);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            Set<String> seen = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("UDF dosyasında çok fazla giriş var");
                // ZIP names use forward slashes. Rewriting backslashes or removing
                // leading "./" aliases can silently reinterpret unknown members.
                // The independently documented synthetic subset requires canonical paths.
                String name = entry.getName();
                if (name.indexOf('\\') >= 0)
                    throw new IllegalArgumentException("UDF ZIP ters eğik çizgi içeriyor");
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
                    throw new IllegalArgumentException("UDF ZIP mutlak yol içeriyor");
                String path = entry.isDirectory() && name.endsWith("/")
                        ? name.substring(0, name.length() - 1) : name;
                for (String part : path.split("/", -1))
                    if (part.isEmpty() || part.equals(".") || part.equals(".."))
                        throw new IllegalArgumentException("UDF ZIP güvenli olmayan yol içeriyor");
                // Directory trailing slash is a ZIP record marker, not a distinct logical path.
                // A file and directory at the same path must not be interpreted differently.
                String normalized = path.toLowerCase(Locale.ROOT);
                if (!seen.add(normalized)) throw new IllegalArgumentException("UDF ZIP tekrarlanan giriş içeriyor");
                if (entry.isDirectory()) {
                    // A ZIP directory is metadata, not an ignored payload. Accept
                    // only canonical paths in the synthetic PNG namespace, and
                    // reject directory records carrying bytes that would vanish.
                    // Canonical ZIP roots normalize from "media/" or "images/" to no slash.
                    // Still reject file/directory aliases through the same logical seen set.
                    if (!(normalized.equals("media") || normalized.startsWith("media/")
                            || normalized.equals("images") || normalized.startsWith("images/")))
                        throw new IllegalArgumentException("Desteklenmeyen UDF ZIP dizini: " + normalized);
                    DocumentElements.mediaPath(normalized + "/placeholder.png");
                    // Directory markers must be truly empty, including their
                    // declared ZIP sizes and CRC. File/directory aliases are
                    // rejected separately by the normalized seen set.
                    if (entry.getSize() != 0 || entry.getCrc() != 0)
                        throw new IllegalArgumentException("UDF ZIP dizininde beklenmeyen veri var");
                    try (InputStream in = zip.getInputStream(entry)) {
                        if (in.read() != -1)
                            throw new IllegalArgumentException("UDF ZIP dizininde beklenmeyen veri var");
                    }
                    continue;
                }
                if (entry.getSize() > MAX_TOTAL_UNCOMPRESSED)
                    throw new IllegalArgumentException("UDF ZIP giriş boyutu sınırı aşıldı");
                if (entry.getSize() > 0) total += entry.getSize();
                if (total > MAX_TOTAL_UNCOMPRESSED) throw new IllegalArgumentException("UDF ZIP çok büyük");
                if (normalized.equals("content.xml")) {
                    if (data != null) throw new IllegalArgumentException("Birden fazla content.xml");
                    if (entry.getSize() > MAX_CONTENT_XML)
                        throw new IllegalArgumentException("UDF XML boyut sınırı aşıldı");
                    data = readVerifiedZipMember(zip, entry, MAX_CONTENT_XML);
                } else if (normalized.endsWith(".png")
                        && (normalized.startsWith("media/") || normalized.startsWith("images/"))) {
                    // Exactly scoped resource namespace; no extraction to disk and no network fallback.
                    DocumentElements.mediaPath(normalized);
                    if (media.size() >= MAX_MEDIA_FILES || entry.getSize() > MAX_MEDIA_PNG)
                        throw new IllegalArgumentException("UDF PNG kaynak sınırı aşıldı");
                    byte[] bytes = readVerifiedZipMember(zip, entry, MAX_MEDIA_PNG);
                    if ((long) mediaTotal + bytes.length > MAX_MEDIA_TOTAL)
                        throw new IllegalArgumentException("UDF PNG veri sınırı aşıldı");
                    mediaTotal += bytes.length;
                    media.put(normalized, bytes);
                } else {
                    // Unknown archive payloads may contain document content or formatting.
                    // Never report a successful PDF after silently discarding them.
                    throw new IllegalArgumentException("Desteklenmeyen UDF ZIP bileşeni: " + normalized);
                }
            }
        }
        if (data == null) throw new IllegalArgumentException("UDF içinde content.xml bulunamadı");
        return parse(data, file.getFileName().toString(), media);
    }

    /**
     * A complete supported ZIP ends at its fixed 22-byte End of Central
     * Directory record. Java ZipFile and ZipInputStream BOTH accept appended
     * non-ZIP bytes: their member-by-member SHA comparison cannot see that
     * information. Fail closed before interpreting any XML or media.
     *
     * Only single-disk non-ZIP64 archives without comments or self-extracting
     * prefixes are in this independently specified synthetic subset. Normal
     * Java ZipOutputStream archives, including DEFLATE data descriptors and
     * directory markers, satisfy this envelope exactly.
     */
    private static long requireCanonicalZipEnvelope(Path source) throws IOException {
        long fileSize = Files.size(source);
        if (fileSize < 22)
            throw new IllegalArgumentException("UDF ZIP son kaydı eksik veya açıklanmamış ek veri var");
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            channel.position(fileSize - 22);
            while (eocd.hasRemaining()) {
                if (channel.read(eocd) < 0)
                    throw new IllegalArgumentException("UDF ZIP son kaydı eksik");
            }
            eocd.flip();
            int signature = eocd.getInt();
            int disk = Short.toUnsignedInt(eocd.getShort());
            int directoryDisk = Short.toUnsignedInt(eocd.getShort());
            int entriesOnDisk = Short.toUnsignedInt(eocd.getShort());
            int entryCount = Short.toUnsignedInt(eocd.getShort());
            long directorySize = Integer.toUnsignedLong(eocd.getInt());
            long directoryOffset = Integer.toUnsignedLong(eocd.getInt());
            int commentLength = Short.toUnsignedInt(eocd.getShort());
            if (signature != 0x06054b50 || disk != 0 || directoryDisk != 0
                    || entriesOnDisk != entryCount || entryCount < 1
                    || entryCount > MAX_ENTRIES || commentLength != 0
                    || directorySize < 46 || directoryOffset < 30
                    || directoryOffset + directorySize != fileSize - 22)
                throw new IllegalArgumentException(
                        "UDF ZIP son kaydı, merkez dizini veya açıklanmamış ek veri geçersiz");
            ByteBuffer signatureBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            channel.position(0);
            while (signatureBytes.hasRemaining())
                if (channel.read(signatureBytes) < 0)
                    throw new IllegalArgumentException("UDF ZIP yerel kayıt başı eksik");
            signatureBytes.flip();
            if (signatureBytes.getInt() != 0x04034b50)
                throw new IllegalArgumentException("UDF ZIP ön eki desteklenmiyor");
            signatureBytes.clear();
            channel.position(directoryOffset);
            while (signatureBytes.hasRemaining())
                if (channel.read(signatureBytes) < 0)
                    throw new IllegalArgumentException("UDF ZIP merkez dizini eksik");
            signatureBytes.flip();
            if (signatureBytes.getInt() != 0x02014b50)
                throw new IllegalArgumentException("UDF ZIP merkez dizini tutarsız");
            return directoryOffset;
        }
    }

    /**
     * ZIP can contain unaccounted bytes BETWEEN the last local member and
     * the central directory. Both Java ZIP readers ignore that interstitial
     * data, and TUR430's EOCD-at-EOF check still passes if the central offset
     * is updated. Use each already-verified central entry's compressed size
     * and the actual local header/data-descriptor layout to account for every
     * byte in the local record region without decompressing or allocating
     * any extra source body.
     */
    private static long nextPackedLocalZipOffset(
            FileChannel channel, long offset, ZipEntry central, long directoryOffset)
            throws IOException {
        if (offset < 0 || offset > directoryOffset - 30
                || central.getCompressedSize() < 0)
            throw new IllegalArgumentException("UDF ZIP yerel kayıt aralığı geçersiz");
        ByteBuffer header = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        readAt(channel, header, offset);
        header.flip();
        if (header.getInt() != 0x04034b50)
            throw new IllegalArgumentException(
                    "UDF ZIP yerel kayıtlar arasında açıklanmamış veri var");
        header.position(6);
        int flags = Short.toUnsignedInt(header.getShort());
        int method = Short.toUnsignedInt(header.getShort());
        if (method != central.getMethod() || (flags & 0x1) != 0)
            throw new IllegalArgumentException("UDF ZIP yerel sıkıştırma kaydı uyuşmuyor");
        header.position(26);
        int nameLength = Short.toUnsignedInt(header.getShort());
        int extraLength = Short.toUnsignedInt(header.getShort());
        long afterData = offset + 30L + nameLength + extraLength
                + central.getCompressedSize();
        if (nameLength == 0 || afterData < offset || afterData > directoryOffset)
            throw new IllegalArgumentException("UDF ZIP yerel veri sınırı geçersiz");
        if ((flags & 0x8) == 0) return afterData;
        if (afterData > directoryOffset - 12)
            throw new IllegalArgumentException("UDF ZIP veri tanımlayıcısı eksik");
        ByteBuffer descriptorSignature = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readAt(channel, descriptorSignature, afterData);
        descriptorSignature.flip();
        long length = descriptorSignature.getInt() == 0x08074b50 ? 16L : 12L;
        if (afterData + length > directoryOffset)
            throw new IllegalArgumentException("UDF ZIP veri tanımlayıcısı sınırı geçersiz");
        // ZipInputStream below still validates the full descriptor CRC and
        // both decoded lengths. This bounds ONLY the physical ZIP layout.
        return afterData + length;
    }

    private static void readAt(FileChannel channel, ByteBuffer bytes, long offset)
            throws IOException {
        while (bytes.hasRemaining()) {
            int read = channel.read(bytes, offset);
            if (read <= 0) throw new IllegalArgumentException("UDF ZIP yerel kayıt okunamadı");
            offset += read;
        }
    }

    /**
     * Compare all LOCAL ZIP records against the CENTRAL directory before any
     * content.xml or media bytes are treated as document content. ZipFile
     * resolves entry names from the central directory, while streaming ZIP
     * readers resolve them from local headers. Checking only decompressed
     * CRC-32 cannot detect a conflicting local filename.
     *
     * Consume each local stream at most once, with the same total 32 MiB
     * decoded budget as the reader. A Map avoids depending on ZIP entry
     * ordering, which need not match local header order.
     */
    private static void requireConsistentLocalZipRecords(
            Path file, ZipFile zip, long directoryOffset) {
        Map<String, ZipEntry> declared = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        int centralCount = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (++centralCount > MAX_ENTRIES || declared.putIfAbsent(entry.getName(), entry) != null)
                throw new IllegalArgumentException("UDF ZIP merkez dizini girişleri tutarsız");
            // Unlike the archive-level EOCD comment rejected by TUR430,
            // per-member comments live inside CENTRAL records. ZipInputStream
            // has no per-member comment and drops this input on the floor;
            // converting a commented source to PDF would silently erase it.
            // No comment-to-PDF mapping exists in the audited synthetic subset.
            if (entry.getComment() != null && !entry.getComment().isEmpty())
                throw new IllegalArgumentException(
                        "UDF ZIP giriş açıklaması PDF'ye aktarılmıyor; açıklamalı giriş desteklenmiyor");
            if (entry.getSize() < 0 || entry.getSize() > MAX_TOTAL_UNCOMPRESSED
                    || entry.getCrc() < 0)
                throw new IllegalArgumentException("UDF ZIP merkez dizini boyutu veya CRC geçersiz");
        }
        long aggregate = 0;
        long nextLocalOffset = 0;
        int localCount = 0;
        byte[] buffer = new byte[64 * 1024];
        try (FileChannel packedBytes = FileChannel.open(file, StandardOpenOption.READ);
             ZipInputStream local = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = local.getNextEntry()) != null) {
                ZipEntry central = declared.remove(entry.getName());
                if (++localCount > MAX_ENTRIES || central == null
                        || entry.getMethod() != central.getMethod())
                    throw new IllegalArgumentException("UDF ZIP yerel başlık ile merkez dizini uyuşmuyor");
                nextLocalOffset = nextPackedLocalZipOffset(
                        packedBytes, nextLocalOffset, central, directoryOffset);
                long decoded = 0;
                int length;
                MessageDigest localSha = zipSha256();
                while ((length = local.read(buffer)) != -1) {
                    decoded += length;
                    aggregate += length;
                    localSha.update(buffer, 0, length);
                    if (decoded > central.getSize() || aggregate > MAX_TOTAL_UNCOMPRESSED)
                        throw new IllegalArgumentException("UDF ZIP yerel giriş veri sınırı aşıldı");
                }
                local.closeEntry();
                // Preserve the specific fail-closed CRC/size error categories
                // already relied upon by the existing TUR393 regression tests.
                // This does not expose member names or source document data.
                if (decoded != central.getSize() || entry.getSize() != central.getSize())
                    throw new IllegalArgumentException("UDF ZIP yerel giriş veri boyutu tutarsız");
                if (entry.getCrc() != central.getCrc())
                    throw new IllegalArgumentException("UDF ZIP yerel giriş CRC-32 bütünlüğü uyuşmuyor");
                // Metadata equality is not byte identity: two different ZIP
                // payloads can share length and CRC-32. A manipulated CENTRAL
                // local-header offset may point to a different payload than
                // ZipInputStream reads. Compare both bounded decoded streams.
                MessageDigest centralSha = zipSha256();
                long centralDecoded = 0;
                try (InputStream centralInput = zip.getInputStream(central)) {
                    while ((length = centralInput.read(buffer)) != -1) {
                        centralDecoded += length;
                        if (centralDecoded > central.getSize())
                            throw new IllegalArgumentException("UDF ZIP merkez giriş veri sınırı aşıldı");
                        centralSha.update(buffer, 0, length);
                    }
                }
                if (centralDecoded != central.getSize())
                    throw new IllegalArgumentException("UDF ZIP merkez giriş veri boyutu tutarsız");
                if (!MessageDigest.isEqual(localSha.digest(), centralSha.digest()))
                    throw new IllegalArgumentException("UDF ZIP yerel ve merkez giriş SHA-256 içeriği uyuşmuyor");
            }
        } catch (IOException ex) {
            // ZIP streams can report document/member names in exceptions.
            // Keep user-facing text generic; diagnostics stay chained for logs.
            throw new IllegalArgumentException("UDF ZIP yerel başlık okunamadı", ex);
        }
        if (localCount != centralCount || !declared.isEmpty())
            throw new IllegalArgumentException("UDF ZIP yerel başlık sayısı uyuşmuyor");
        if (nextLocalOffset != directoryOffset)
            throw new IllegalArgumentException(
                    "UDF ZIP yerel kayıtlar ile merkez dizini arasında açıklanmamış veri var");
    }

    private static MessageDigest zipSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", ex);
        }
    }

    /**
     * java.util.zip.ZipFile.getInputStream() does NOT validate an entry's
     * declared CRC-32. A corrupted ZIP may otherwise be silently rendered as
     * a PDF. Check both uncompressed byte length and CRC on bounded bytes,
     * before the XML or PNG becomes a trusted document element.
     *
     * CRC-32 detects archive-integrity mismatch; it is NOT authenticity,
     * a signature, a source-ownership proof or a native UYAP fidelity claim.
     */
    private static byte[] readVerifiedZipMember(ZipFile zip, ZipEntry entry, int maxBytes)
            throws IOException {
        long expectedSize = entry.getSize();
        long expectedCrc = entry.getCrc();
        if (expectedSize < 0 || expectedSize > maxBytes || expectedCrc < 0)
            throw new IllegalArgumentException("UDF ZIP giriş boyutu veya CRC alanı geçersiz");
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes || bytes.length != expectedSize)
                throw new IllegalArgumentException("UDF ZIP giriş veri boyutu tutarsız");
            CRC32 crc = new CRC32();
            crc.update(bytes);
            if (crc.getValue() != expectedCrc)
                throw new IllegalArgumentException("UDF ZIP CRC-32 bütünlük denetimi başarısız");
            return bytes;
        }
    }

    /** Public synthetic fixture contract: UTF-16 startOffset/length; other variants are not guessed. */
    public static DocumentSpec parse(byte[] xml, String name) throws Exception {
        return parse(xml, name, Map.of());
    }
    private static DocumentSpec parse(byte[] xml, String name, Map<String,byte[]> media) throws Exception {
        if (xml == null || xml.length == 0 || xml.length > MAX_CONTENT_XML)
            throw new IllegalArgumentException("UDF XML boyutu geçersiz");
        // A 4 MiB XML file can contain hundreds of thousands of tiny elements,
        // comments or deeply nested tags. Check SAX event budgets BEFORE
        // allocating a full DOM or recursively examining its elements.
        preflightXmlComplexity(xml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        // Reject metadata nodes that the synthetic schema cannot represent in PDF.
        // In particular, DOM comments and processing instructions must not disappear.
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        rejectUnsupportedXmlNodes(document);
        Element root = document.getDocumentElement();
        if (!root.getTagName().equals("template")) throw new IllegalArgumentException("Desteklenmeyen UDF XML kökü");
        onlyAttributes(root, "format_id");
        rejectDirectText(root);
        String version = root.getAttribute("format_id");
        // Only the independently documented synthetic 1.0 fixture contract is implemented.
        // Do not guess a future or authentic UYAP schema from an arbitrary format_id.
        if (!"1.0".equals(version))
            throw new IllegalArgumentException("Desteklenmeyen sentetik UDF format_id: " + version);
        Element content = null, body = null, styles = null, properties = null;
        for (Element e : children(root)) {
            switch (e.getTagName()) {
                case "content" -> { if (content != null) throw new IllegalArgumentException("Çift content"); content = e; }
                case "elements" -> { if (body != null) throw new IllegalArgumentException("Çift elements"); body = e; }
                case "styles" -> { if (styles != null) throw new IllegalArgumentException("Çift styles"); styles = e; }
                case "properties" -> {
                    if (properties != null) throw new IllegalArgumentException("Çift sayfa ayarı");
                    properties = e;
                }
                default -> throw new IllegalArgumentException("Desteklenmeyen XML bileşeni: " + e.getTagName());
            }
        }
        if (content == null || body == null) throw new IllegalArgumentException("Eksik content/elements");
        onlyAttributes(content);
        onlyAttributes(body);
        rejectDirectText(body);
        preflightImportImagePixels(body, media);
        if (!children(content).isEmpty())
            throw new IllegalArgumentException("İç içe metin elemanı desteklenmiyor");
        String raw = content.getTextContent();
        Map<String, Style> named = new HashMap<>();
        if (styles != null) {
            onlyAttributes(styles);
            rejectDirectText(styles);
            for (Element e : children(styles)) {
                if (!e.getTagName().equals("style")) throw new IllegalArgumentException("Desteklenmeyen stil");
                onlyAttributes(e, "name", "size", "bold", "foreground");
                if (!children(e).isEmpty() || !DocumentElements.isStructuralWhitespace(e.getTextContent()))
                    throw new IllegalArgumentException("Desteklenmeyen stil içeriği");
                String key = e.getAttribute("name");
                if (key.isBlank() || named.containsKey(key)) throw new IllegalArgumentException("Tekrarlanan stil");
                named.put(key, resolve(e, DEFAULT));
            }
        }
        List<Paragraph> paragraphs = new ArrayList<>();
        List<DocumentElements.Block> blocks = new ArrayList<>();
        // A ZIP-local PNG without an XML reference would disappear from the PDF.
        // Track only references that passed the strict synthetic image validation.
        Set<String> referencedMedia = new HashSet<>();
        int renderedImageBlocks = 0;
        long renderedImageBytes = 0L;
        // Synthetic UTF-16 ranges must be ordered, non-overlapping and cover
        // every visible character; otherwise conversion could silently lose text.
        int coveredUntil = 0;
        for (Element p : children(body)) {
            if ("table".equals(p.getTagName())) {
                blocks.add(DocumentElements.table(p));
                continue;
            }
            if ("image".equals(p.getTagName())) {
                // Count XML IMAGE USES, not just distinct ZIP media files.
                // Reject before expensive ImageIO decode and ImageBlock clone.
                if (renderedImageBlocks >= MAX_IMAGE_BLOCKS)
                    throw new IllegalArgumentException(
                            "UDF toplam görsel kullanım sayısı sınırı aşıldı");
                String reference = p.hasAttribute("ref")
                        ? DocumentElements.mediaPath(p.getAttribute("ref")) : null;
                byte[] referencedBytes = reference == null ? null : media.get(reference);
                // A repeated reference can multiply one legal compressed PNG
                // into hundreds of retained copies even when ZIP mediaTotal
                // is far below its 8 MiB budget. Pre-check before decoding.
                if (referencedBytes != null
                        && referencedBytes.length > MAX_RENDERED_IMAGE_BYTES - renderedImageBytes)
                    throw new IllegalArgumentException(
                            "UDF toplam kullanılan PNG veri sınırı aşıldı");
                DocumentElements.ImageBlock image = DocumentElements.image(p, media);
                // Inline base64 PNGs have no ZIP media entry. Count those too
                // after their own already-bounded decode; the next one cannot
                // be decoded if the use count is exhausted.
                long imageBytes = referencedBytes != null
                        ? referencedBytes.length : image.png().length;
                if (imageBytes > MAX_RENDERED_IMAGE_BYTES - renderedImageBytes)
                    throw new IllegalArgumentException(
                            "UDF toplam kullanılan PNG veri sınırı aşıldı");
                renderedImageBytes += imageBytes;
                renderedImageBlocks++;
                if (reference != null) referencedMedia.add(reference);
                blocks.add(image);
                continue;
            }
            if (!p.getTagName().equals("paragraph"))
                throw new IllegalArgumentException("Bu MVP henüz " + p.getTagName() + " bileşenini desteklemiyor");
            onlyAttributes(p);
            rejectDirectText(p);
            List<Run> runs = new ArrayList<>();
            for (Element e : children(p)) {
                if (!e.getTagName().equals("content")) throw new IllegalArgumentException("Desteklenmeyen paragraf elemanı");
                onlyAttributes(e, "startOffset", "length", "resolver", "size", "bold", "foreground");
                if (!children(e).isEmpty() || !DocumentElements.isStructuralWhitespace(e.getTextContent()))
                    throw new IllegalArgumentException("Desteklenmeyen metin aralığı içeriği");
                int start = requiredInt(e, "startOffset");
                int length = requiredInt(e, "length");
                if (start < 0 || length < 0 || (long) start + length > raw.length())
                    throw new IllegalArgumentException("Geçersiz UTF-16 metin aralığı");
                int end = start + length;
                if (start < coveredUntil)
                    throw new IllegalArgumentException("Sırasız veya çakışan UTF-16 metin aralığı");
                String gap = raw.substring(coveredUntil, start);
                if (!DocumentElements.isStructuralWhitespace(gap))
                    throw new IllegalArgumentException("Atlanan UTF-16 metin aralığı");
                if (splitsSurrogate(raw, start) || splitsSurrogate(raw, end))
                    throw new IllegalArgumentException("Unicode çiftinin ortasından bölme");
                String key = e.getAttribute("resolver");
                Style base = key.isBlank() ? DEFAULT : named.get(key);
                if (base == null) throw new IllegalArgumentException("Bulunamayan stil: " + key);
                // Disjoint ranges inside the SAME paragraph may skip whitespace.
                // Preserve that source whitespace in the following styled run:
                // silently omitting it merges words and changes PDF geometry.
                // Gaps before the first run retain the documented structural
                // whitespace behavior of the synthetic UTF-16 subset.
                String text = raw.substring(start, end);
                // Interior gaps belong to the next styled run. At the FIRST
                // run of a paragraph, prior CR/LF/TAB are structural, but
                // ASCII spaces AFTER the last structural separator are visible
                // leading indentation. TUR396 covered only all-space gaps;
                // "\\n    " used to drop four visible spaces and shift PDF
                // glyphs left without reporting a conversion error.
                if (!runs.isEmpty()) text = gap + text;
                else text = paragraphLeadingSpaces(gap) + text;
                runs.add(new Run(text, resolve(e, base)));
                coveredUntil = end;
            }
            if (runs.isEmpty()) throw new IllegalArgumentException("Boş/desteklenmeyen paragraf");
            Paragraph paragraph = new Paragraph(runs);
            paragraphs.add(paragraph);
            blocks.add(new DocumentElements.ParagraphBlock(paragraph));
        }
        for (String path : media.keySet())
            if (!referencedMedia.contains(path))
                throw new IllegalArgumentException("PDF\u0027ye aktarılmayan UDF PNG kaynağı: " + path);
        if (!DocumentElements.isStructuralWhitespace(raw.substring(coveredUntil)))
            throw new IllegalArgumentException("UDF içeriğinde PDF\u0027ye aktarılmayan metin var");
        if (blocks.isEmpty()) throw new IllegalArgumentException("Dönüştürülebilir belge öğesi bulunamadı");
        return new DocumentSpec(name, version, paragraphs, blocks, DocumentElements.page(properties));
    }

    /**
     * Do not create extra PDF lines from structural CR/LF/TAB between XML
     * paragraphs, but preserve the literal ASCII indentation after the LAST
     * such separator. An all-space gap (TUR396) is preserved unchanged.
     */
    private static String paragraphLeadingSpaces(String gap) {
        if (gap.isEmpty()) return "";
        int structural = Math.max(gap.lastIndexOf('\n'),
                Math.max(gap.lastIndexOf('\r'), gap.lastIndexOf('\t')));
        String indent = gap.substring(structural + 1);
        for (int i = 0; i < indent.length(); i++)
            if (indent.charAt(i) != ' ') return "";
        return indent;
    }

    /**
     * ImageIO decodes each PNG while importing UDF XML, before the exporter
     * enforces TUR425's 16MP aggregate budget. A UDF with five DISTINCT,
     * highly compressible 4MP images fits both the 8MiB ZIP media-byte budget
     * and TUR428's 64-use budget, yet unnecessarily allocates large rasters.
     *
     * Scan only the XML's direct image children and IHDRs before decoding ANY
     * PNG. This method cannot approve an image: ordinary strict CRC, IDAT,
     * palette, color and XML schema validation still run afterward.
     */
    private static void preflightImportImagePixels(
            Element body, Map<String, byte[]> media) {
        long total = 0L;
        for (Element element : children(body)) {
            if (!"image".equals(element.getTagName())) continue;
            byte[] png;
            if (element.hasAttribute("ref")) {
                // The regular image parser still rejects malformed/missing refs;
                // this step only prevents excess decoded pixel allocation.
                png = media.get(DocumentElements.mediaPath(element.getAttribute("ref")));
                if (png == null) continue;
            } else {
                // XML already has a strict 4MiB source cap. Read the same
                // bounded base64 syntax as DocumentElements.image(), but do
                // not call ImageIO or allocate a 4MP BufferedImage here.
                String encoded = element.getTextContent().replaceAll("\\s+", "");
                if (encoded.isEmpty() || encoded.length() > 1_400_000)
                    throw new IllegalArgumentException("PNG veri sınırı");
                try {
                    png = java.util.Base64.getDecoder().decode(encoded);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Geçersiz PNG base64", ex);
                }
            }
            long pixels = DocumentElements.pngPixelCount(png);
            if (pixels > MAX_IMPORT_IMAGE_PIXELS - total)
                throw new IllegalArgumentException(
                        "UDF toplam çözümlenmiş PNG piksel sınırı aşıldı: "
                                + MAX_IMPORT_IMAGE_PIXELS);
            total += pixels;
        }
    }

    /**
     * Reject complexity bombs before the DOM allocates one object per XML node.
     * SAX sees elements, text events, comments and processing instructions;
     * the depth limit also bounds rejectUnsupportedXmlNodes recursion.
     * This is an input safety check, not support for any native UYAP schema.
     */
    private static void preflightXmlComplexity(byte[] xml) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            var sax = factory.newSAXParser();
            sax.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sax.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            class Budget extends DefaultHandler2 {
                int depth, nodes;
                void count() throws SAXException {
                    if (++nodes > MAX_XML_NODES)
                        throw new SAXException("UDF_XML_NODE_LIMIT");
                }
                @Override public void startElement(String uri, String local, String qName,
                                                    Attributes attributes) throws SAXException {
                    count();
                    if (++depth > MAX_XML_DEPTH)
                        throw new SAXException("UDF_XML_DEPTH_LIMIT");
                    if (attributes.getLength() > MAX_XML_ATTRIBUTES)
                        throw new SAXException("UDF_XML_ATTRIBUTE_LIMIT");
                }
                @Override public void endElement(String uri, String local, String qName) {
                    depth--;
                }
                @Override public void characters(char[] ch, int start, int length) throws SAXException {
                    if (length > 0) count();
                }
                @Override public void ignorableWhitespace(char[] ch, int start, int length)
                        throws SAXException {
                    if (length > 0) count();
                }
                @Override public void comment(char[] ch, int start, int length) throws SAXException {
                    count();
                }
                @Override public void processingInstruction(String target, String data)
                        throws SAXException {
                    count();
                }
            }
            Budget handler = new Budget();
            var saxReader = sax.getXMLReader();
            saxReader.setContentHandler(handler);
            saxReader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            saxReader.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            // Never report untrusted XML text, parser internals or a path.
            throw new IllegalArgumentException("UDF XML karmaşıklık veya güvenli ayrıştırma sınırı", e);
        }
    }

    /** Fail closed on XML nodes outside the explicitly supported synthetic subset. */
    private static void rejectUnsupportedXmlNodes(Node parent) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE -> rejectUnsupportedXmlNodes(node);
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> { }
                default -> throw new IllegalArgumentException(
                        "Desteklenmeyen UDF XML düğümü: " + node.getNodeName());
            }
        }
    }

    private static void onlyAttributes(Element element, String... allowed) {
        Set<String> names = Set.of(allowed);
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.item(i).getNodeName();
            if (!names.contains(name))
                throw new IllegalArgumentException("Desteklenmeyen " + element.getTagName()
                        + " özelliği: " + name);
        }
    }

    /** Never silently discard non-whitespace text outside supported content ranges. */
    private static void rejectDirectText(Element element) {
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if ((node.getNodeType() == Node.TEXT_NODE
                    || node.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !DocumentElements.isStructuralWhitespace(node.getNodeValue()))
                throw new IllegalArgumentException("Desteklenmeyen " + element.getTagName()
                        + " doğrudan metni");
        }
    }

    private static int requiredInt(Element e, String key) {
        try { return Integer.parseInt(e.getAttribute(key)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Geçersiz " + key, ex); }
    }
    private static Style resolve(Element e, Style base) {
        float size = e.hasAttribute("size") ? Float.parseFloat(e.getAttribute("size")) : base.size();
        if (!Float.isFinite(size) || size < 6 || size > 48) throw new IllegalArgumentException("Geçersiz font boyutu");
        boolean bold = base.bold();
        if (e.hasAttribute("bold")) {
            String value = e.getAttribute("bold");
            if (!"true".equals(value) && !"false".equals(value))
                throw new IllegalArgumentException("Geçersiz kalın yazı ayarı: " + value);
            bold = Boolean.parseBoolean(value);
        }
        Color color = base.color();
        if (e.hasAttribute("foreground")) {
            String value = e.getAttribute("foreground");
            if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Geçersiz renk");
            color = Color.decode(value);
        }
        return new Style(size, bold, color);
    }
    private static boolean splitsSurrogate(String raw, int position) {
        return position > 0 && position < raw.length() &&
            Character.isHighSurrogate(raw.charAt(position - 1)) &&
            Character.isLowSurrogate(raw.charAt(position));
    }
    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++)
            if (nodes.item(i) instanceof Element e) result.add(e);
        return result;
    }
}
