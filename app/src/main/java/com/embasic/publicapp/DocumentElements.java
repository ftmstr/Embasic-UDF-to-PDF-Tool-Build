package com.embasic.publicapp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Strictly synthetic, documented additional XML subset.
 * This is NOT a declaration that authentic UYAP uses these XML structures.
 */
public final class DocumentElements {
    private DocumentElements() {}
    public sealed interface Block permits ParagraphBlock, TableBlock, ImageBlock {}
    public record ParagraphBlock(UdfReader.Paragraph paragraph) implements Block {}
    public record Cell(String text, int colspan, int rowspan) {}
    public record Row(List<Cell> cells, float height) {
        public Row { cells = List.copyOf(cells); }
    }
    public record TableBlock(List<Float> widths, List<Row> rows) implements Block {
        public TableBlock {
            widths = List.copyOf(widths);
            rows = List.copyOf(rows);
        }
    }
    public record ImageBlock(byte[] png, float width, float height) implements Block {
        public ImageBlock { png = png.clone(); }
        @Override public byte[] png() { return png.clone(); }
    }
    public record PageSpec(float width, float height, float margin) {
        public static PageSpec a4() { return new PageSpec(595.28f, 841.89f, 48f); }
    }
    private static void onlyAttributes(Element element, String... allowed) {
        var names = java.util.Set.of(allowed);
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++)
            if (!names.contains(attributes.item(i).getNodeName()))
                throw new IllegalArgumentException("Desteklenmeyen " + element.getTagName()
                        + " özelliği: " + attributes.item(i).getNodeName());
    }
    private static List<Element> children(Element parent) {
        List<Element> list = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++)
            if (nodes.item(i) instanceof Element e) list.add(e);
        return list;
    }
    private static float positive(String value, String name, float max) {
        try {
            float x = Float.parseFloat(value.trim());
            if (!Float.isFinite(x) || x <= 0 || x > max)
                throw new IllegalArgumentException(name + " aralık dışında");
            return x;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Geçersiz " + name, e);
        }
    }
    private static int span(Element cell, String name, int max) {
        if (!cell.hasAttribute(name)) return 1;
        try {
            int value = Integer.parseInt(cell.getAttribute(name));
            if (value < 1 || value > max) throw new IllegalArgumentException(name + " aralık dışında");
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Geçersiz " + name, e);
        }
    }
    public static PageSpec page(Element properties) {
        if (properties == null) return PageSpec.a4();
        onlyAttributes(properties, "pageSize", "orientation", "margin");
        if (!children(properties).isEmpty() || !properties.getTextContent().isBlank())
            throw new IllegalArgumentException("Bilinmeyen UDF sayfa ayarı");
        String size = properties.hasAttribute("pageSize") ? properties.getAttribute("pageSize") : "A4";
        float w, h;
        switch (size.toUpperCase(Locale.ROOT)) {
            case "A4" -> { w = 595.28f; h = 841.89f; }
            case "LETTER" -> { w = 612f; h = 792f; }
            default -> throw new IllegalArgumentException("Desteklenmeyen sayfa boyutu: " + size);
        }
        String orientation = properties.hasAttribute("orientation")
                ? properties.getAttribute("orientation") : "portrait";
        if (orientation.equals("landscape")) { float swap = w; w = h; h = swap; }
        else if (!orientation.equals("portrait"))
            throw new IllegalArgumentException("Desteklenmeyen sayfa yönü: " + orientation);
        float margin = properties.hasAttribute("margin")
                ? positive(properties.getAttribute("margin"), "sayfa marjı", 100) : 48f;
        return new PageSpec(w, h, margin);
    }
    public static TableBlock table(Element table) {
        onlyAttributes(table, "widths");
        String widthsValue = table.getAttribute("widths");
        if (widthsValue.isBlank()) throw new IllegalArgumentException("Tablo sütun genişlikleri eksik");
        String[] parts = widthsValue.split(",", -1);
        if (parts.length < 1 || parts.length > 32) throw new IllegalArgumentException("Tablo sütun sayısı");
        List<Float> widths = new ArrayList<>();
        for (String part : parts) widths.add(positive(part, "sütun genişliği", 1000));
        List<Row> rows = new ArrayList<>();
        for (Element row : children(table)) {
            if (!row.getTagName().equals("row")) throw new IllegalArgumentException("Desteklenmeyen tablo satırı");
            onlyAttributes(row, "height");
            if (rows.size() >= 500) throw new IllegalArgumentException("Tablo çok fazla satır içeriyor");
            float height = positive(row.getAttribute("height"), "satır yüksekliği", 180);
            if (height < 18f) throw new IllegalArgumentException("Satır yüksekliği yetersiz");
            List<Cell> cells = new ArrayList<>();
            for (Element cell : children(row)) {
                if (!cell.getTagName().equals("cell")) throw new IllegalArgumentException("Desteklenmeyen tablo hücresi");
                onlyAttributes(cell, "colspan", "rowspan");
                if (!children(cell).isEmpty()) throw new IllegalArgumentException("İç içe tablo hücresi desteklenmiyor");
                String text = cell.getTextContent();
                if (text.length() > 400 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("Çok satırlı tablo hücresi desteklenmiyor");
                cells.add(new Cell(text, span(cell, "colspan", parts.length), span(cell, "rowspan", 500)));
            }
            if (cells.isEmpty()) throw new IllegalArgumentException("Boş tablo satırı");
            rows.add(new Row(cells, height));
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Boş tablo");
        return new TableBlock(widths, rows);
    }
    public static ImageBlock image(Element image) throws Exception {
        onlyAttributes(image, "mime", "encoding", "width", "height");
        if (!children(image).isEmpty()) throw new IllegalArgumentException("İç içe görsel elemanı desteklenmiyor");
        if (!"image/png".equals(image.getAttribute("mime"))
                || !"base64".equals(image.getAttribute("encoding")))
            throw new IllegalArgumentException("Yalnız sentetik base64 PNG görseli destekleniyor");
        float w = positive(image.getAttribute("width"), "görsel genişliği", 1000);
        float h = positive(image.getAttribute("height"), "görsel yüksekliği", 1000);
        String encoded = image.getTextContent().replaceAll("\\s+", "");
        if (encoded.isEmpty() || encoded.length() > 1_400_000)
            throw new IllegalArgumentException("PNG veri sınırı");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Geçersiz PNG base64", e); }
        if (bytes.length < 24 || bytes.length > 1_048_576
                || (bytes[0] & 0xff) != 0x89 || bytes[1] != 'P'
                || bytes[2] != 'N' || bytes[3] != 'G')
            throw new IllegalArgumentException("PNG biçimi veya boyutu geçersiz");
        // Inspect dimensions before a full bitmap allocation: compressed images can expand.
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("PNG çözücü bulunamadı");
            var reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName()))
                    throw new IllegalArgumentException("PNG olmayan görüntü");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 4_000_000L)
                    throw new IllegalArgumentException("PNG piksel sınırı aşıldı");
                if (reader.read(0) == null)
                    throw new IllegalArgumentException("PNG çözümlenemedi");
            } finally { reader.dispose(); }
        }
        return new ImageBlock(bytes, w, h);
    }
}
