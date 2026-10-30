package com.embasic.publicapp;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
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
    private static final long MAX_TOTAL_UNCOMPRESSED = 32L * 1024 * 1024;
    private static final int MAX_MEDIA_FILES = 64;
    private static final int MAX_MEDIA_TOTAL = 8 * 1024 * 1024;
    private static final int MAX_MEDIA_PNG = 1_048_576;
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
        byte[] data = null;
        long total = 0;
        int mediaTotal = 0;
        Map<String,byte[]> media = new HashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            Set<String> seen = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("UDF dosyasında çok fazla giriş var");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
                    throw new IllegalArgumentException("UDF ZIP mutlak yol içeriyor");
                for (String part : name.split("/"))
                    if (part.equals("..")) throw new IllegalArgumentException("UDF ZIP güvenli olmayan yol içeriyor");
                String normalized = name.replaceAll("^\\./+", "").toLowerCase(Locale.ROOT);
                if (!seen.add(normalized)) throw new IllegalArgumentException("UDF ZIP tekrarlanan giriş içeriyor");
                if (entry.isDirectory()) continue;
                if (entry.getSize() > MAX_TOTAL_UNCOMPRESSED)
                    throw new IllegalArgumentException("UDF ZIP giriş boyutu sınırı aşıldı");
                if (entry.getSize() > 0) total += entry.getSize();
                if (total > MAX_TOTAL_UNCOMPRESSED) throw new IllegalArgumentException("UDF ZIP çok büyük");
                if (normalized.equals("content.xml")) {
                    if (data != null) throw new IllegalArgumentException("Birden fazla content.xml");
                    try (InputStream in = zip.getInputStream(entry)) {
                        byte[] bytes = in.readNBytes(MAX_CONTENT_XML + 1);
                        if (bytes.length > MAX_CONTENT_XML) throw new IllegalArgumentException("UDF XML boyut sınırı aşıldı");
                        data = bytes;
                    }
                } else if (normalized.endsWith(".png")
                        && (normalized.startsWith("media/") || normalized.startsWith("images/"))) {
                    // Exactly scoped resource namespace; no extraction to disk and no network fallback.
                    DocumentElements.mediaPath(normalized);
                    if (media.size() >= MAX_MEDIA_FILES || entry.getSize() > MAX_MEDIA_PNG)
                        throw new IllegalArgumentException("UDF PNG kaynak sınırı aşıldı");
                    try (InputStream in = zip.getInputStream(entry)) {
                        byte[] bytes = in.readNBytes(MAX_MEDIA_PNG + 1);
                        if (bytes.length > MAX_MEDIA_PNG || (long) mediaTotal + bytes.length > MAX_MEDIA_TOTAL)
                            throw new IllegalArgumentException("UDF PNG veri sınırı aşıldı");
                        mediaTotal += bytes.length;
                        media.put(normalized, bytes);
                    }
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

    /** Public synthetic fixture contract: UTF-16 startOffset/length; other variants are not guessed. */
    public static DocumentSpec parse(byte[] xml, String name) throws Exception {
        return parse(xml, name, Map.of());
    }
    private static DocumentSpec parse(byte[] xml, String name, Map<String,byte[]> media) throws Exception {
        if (xml == null || xml.length == 0 || xml.length > MAX_CONTENT_XML)
            throw new IllegalArgumentException("UDF XML boyutu geçersiz");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
        if (!root.getTagName().equals("template")) throw new IllegalArgumentException("Desteklenmeyen UDF XML kökü");
        onlyAttributes(root, "format_id");
        rejectDirectText(root);
        String version = root.getAttribute("format_id");
        if (version.isBlank()) throw new IllegalArgumentException("UDF format_id bulunamadı; sürüm doğrulanamadı");
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
                if (!children(e).isEmpty() || !e.getTextContent().isBlank())
                    throw new IllegalArgumentException("Desteklenmeyen stil içeriği");
                String key = e.getAttribute("name");
                if (key.isBlank() || named.containsKey(key)) throw new IllegalArgumentException("Tekrarlanan stil");
                named.put(key, resolve(e, DEFAULT));
            }
        }
        List<Paragraph> paragraphs = new ArrayList<>();
        List<DocumentElements.Block> blocks = new ArrayList<>();
        for (Element p : children(body)) {
            if ("table".equals(p.getTagName())) {
                blocks.add(DocumentElements.table(p));
                continue;
            }
            if ("image".equals(p.getTagName())) {
                blocks.add(DocumentElements.image(p, media));
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
                if (!children(e).isEmpty() || !e.getTextContent().isBlank())
                    throw new IllegalArgumentException("Desteklenmeyen metin aralığı içeriği");
                int start = requiredInt(e, "startOffset");
                int length = requiredInt(e, "length");
                if (start < 0 || length < 0 || (long) start + length > raw.length())
                    throw new IllegalArgumentException("Geçersiz UTF-16 metin aralığı");
                int end = start + length;
                if (splitsSurrogate(raw, start) || splitsSurrogate(raw, end))
                    throw new IllegalArgumentException("Unicode çiftinin ortasından bölme");
                String key = e.getAttribute("resolver");
                Style base = key.isBlank() ? DEFAULT : named.get(key);
                if (base == null) throw new IllegalArgumentException("Bulunamayan stil: " + key);
                runs.add(new Run(raw.substring(start, end), resolve(e, base)));
            }
            if (runs.isEmpty()) throw new IllegalArgumentException("Boş/desteklenmeyen paragraf");
            Paragraph paragraph = new Paragraph(runs);
            paragraphs.add(paragraph);
            blocks.add(new DocumentElements.ParagraphBlock(paragraph));
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("Dönüştürülebilir belge öğesi bulunamadı");
        return new DocumentSpec(name, version, paragraphs, blocks, DocumentElements.page(properties));
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
                    && !node.getNodeValue().isBlank())
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
