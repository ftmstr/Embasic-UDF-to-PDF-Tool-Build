package com.embasic.audit.fidelity;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
/** Limited independent synthetic XML adapter; deliberately fails closed. */
public final class SyntheticUdfLayout {
  private SyntheticUdfLayout() {}
  public record Cell(String text, int colspan) {
    public Cell { if(text == null || colspan < 1) throw new IllegalArgumentException("cell"); }
  }
  private static float width(String text, Font font, FontRenderContext frc) {
    return (float)font.getStringBounds(text, frc).getWidth();
  }
  public static List<String> wrap(String text, Font font, FontRenderContext frc, float maxWidth) {
    if(text == null || font == null || frc == null || !Float.isFinite(maxWidth) || maxWidth <= 0)
      throw new IllegalArgumentException("wrap arguments");
    List<String> out = new ArrayList<>();
    for(String paragraph : text.split("\\n", -1)) {
      StringBuilder line = new StringBuilder();
      for(String word : paragraph.split(" +")) {
        String candidate = line.length() == 0 ? word : line + " " + word;
        if(line.length() > 0 && width(candidate,font,frc) > maxWidth) {
          out.add(line.toString()); line.setLength(0); line.append(word);
        } else { line.setLength(0); line.append(candidate); }
        if(width(line.toString(),font,frc) > maxWidth)
          throw new IllegalArgumentException("Unbreakable word exceeds width");
      }
      out.add(line.toString());
    }
    return List.copyOf(out);
  }
  public static float nextTabStop(float x,float origin,float interval) {
    if(!Float.isFinite(x)||!Float.isFinite(origin)||!Float.isFinite(interval)||interval<=0)
      throw new IllegalArgumentException("tab");
    return origin + ((float)Math.floor((x-origin)/interval)+1)*interval;
  }
  public static List<Float> cellWidths(float[] columns,List<Cell> cells) {
    if(columns==null||cells==null) throw new IllegalArgumentException("columns");
    for(float c:columns) if(!Float.isFinite(c)||c<=0) throw new IllegalArgumentException("column width");
    List<Float> result = new ArrayList<>(); int offset=0;
    for(Cell c:cells) {
      if(c.colspan()>columns.length-offset) throw new IllegalArgumentException("span");
      float sum=0;
      for(int i=0;i<c.colspan();i++) sum+=columns[offset++];
      result.add(sum);
    }
    return List.copyOf(result);
  }
  public static int pageCount(float[] heights,float printableHeight) {
    if(heights==null||!Float.isFinite(printableHeight)||printableHeight<=0)
      throw new IllegalArgumentException("page");
    int pages=1;float used=0;
    for(float h:heights) {
      if(!Float.isFinite(h)||h<=0||h>printableHeight)
        throw new IllegalArgumentException("Block cannot fit and split unsupported");
      if(used+h>printableHeight){pages++;used=0;}
      used+=h;
    }
    return pages;
  }
  public static List<String> syntheticXmlParagraphs(byte[] xml) throws Exception {
    if(xml==null||xml.length>1024*1024) throw new IllegalArgumentException("xml");
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    f.setFeature("http://xml.org/sax/features/external-general-entities",false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    f.setXIncludeAware(false);f.setExpandEntityReferences(false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
    Element root=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    if(!"template".equals(root.getTagName())) throw new IllegalArgumentException("root");
    List<String> result=new ArrayList<>();NodeList nodes=root.getChildNodes();
    for(int i=0;i<nodes.getLength();i++){
      if(!(nodes.item(i) instanceof Element e))continue;
      if(!"paragraph".equals(e.getTagName()))throw new IllegalArgumentException("unsupported node "+e.getTagName());
      result.add(e.getTextContent());
    }
    return List.copyOf(result);
  }
}
