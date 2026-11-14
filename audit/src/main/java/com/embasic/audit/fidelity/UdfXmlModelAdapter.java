package com.embasic.audit.fidelity;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
/** Independent fail-closed XML subset reader. Not a full UDF decoder. */
public final class UdfXmlModelAdapter {
  private UdfXmlModelAdapter() {}
  public record Style(String family,float size,boolean bold,Color color) {}
  public record Run(String text,Style style) {}
  public record Paragraph(List<Run> runs) { public Paragraph { runs=List.copyOf(runs); } }
  public record Result(String version,List<Paragraph> paragraphs) {
    public Result { paragraphs=List.copyOf(paragraphs); }
  }
  private static final Set<String> SUPPORTED=Set.of("content","elements","styles","properties");
  public static Result parse(byte[] xml) throws Exception {
    if(xml==null||xml.length==0||xml.length>4*1024*1024)throw new IllegalArgumentException("XML size");
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    f.setFeature("http://xml.org/sax/features/external-general-entities",false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    f.setXIncludeAware(false);f.setExpandEntityReferences(false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
    Element root=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    if(!"template".equals(name(root)))throw new IllegalArgumentException("Unknown document root");
    String version=root.getAttribute("format_id");
    if(version.isBlank())throw new IllegalArgumentException("Missing format_id");
    Element content=null,elements=null,styles=null;
    for(Element e:children(root)){
      if(!SUPPORTED.contains(name(e)))throw new IllegalArgumentException("Unsupported root element: "+name(e));
      switch(name(e)){
        case "content" -> content=unique(content,e);
        case "elements" -> elements=unique(elements,e);
        case "styles" -> styles=unique(styles,e);
        default -> {}
      }
    }
    if(content==null||elements==null)throw new IllegalArgumentException("Missing content/elements");
    String raw=content.getTextContent();
    Map<String,Style> named=parseStyles(styles);
    List<Paragraph> paragraphs=new ArrayList<>();
    for(Element e:children(elements)){
      if(!"paragraph".equals(name(e)))throw new IllegalArgumentException("Unsupported body node: "+name(e));
      List<Run> runs=new ArrayList<>();
      for(Element run:children(e)){
        if(!"content".equals(name(run)))throw new IllegalArgumentException("Unsupported inline node: "+name(run));
        int start=integer(run,"startOffset"),length=integer(run,"length");
        if(start<0||length<0||(long)start+length>raw.length())throw new IllegalArgumentException("Invalid UTF-16 offset");
        int end=start+length;
        if(splitsSurrogate(raw,start)||splitsSurrogate(raw,end))throw new IllegalArgumentException("Split surrogate pair");
        String key=run.getAttribute("resolver");
        Style base=key.isEmpty()?new Style("DejaVu Sans",11,false,Color.BLACK):named.get(key);
        if(base==null)throw new IllegalArgumentException("Unresolved style: "+key);
        String family=run.hasAttribute("family")?run.getAttribute("family"):base.family();
        float size=run.hasAttribute("size")?Float.parseFloat(run.getAttribute("size")):base.size();
        if(!Float.isFinite(size)||size<=0||size>256)throw new IllegalArgumentException("Invalid font size");
        boolean bold=run.hasAttribute("bold")?Boolean.parseBoolean(run.getAttribute("bold")):base.bold();
        Color color=run.hasAttribute("foreground")?parseColor(run.getAttribute("foreground")):base.color();
        runs.add(new Run(raw.substring(start,end),new Style(family,size,bold,color)));
      }
      paragraphs.add(new Paragraph(runs));
    }
    return new Result(version,paragraphs);
  }
  private static Map<String,Style> parseStyles(Element e){
    Map<String,Style> map=new HashMap<>();
    if(e==null)return map;
    for(Element item:children(e)){
      if(!"style".equals(name(item)))throw new IllegalArgumentException("Unsupported style node");
      String key=item.getAttribute("name");
      if(key.isBlank()||map.containsKey(key))throw new IllegalArgumentException("Duplicate/blank style");
      float size=item.hasAttribute("size")?Float.parseFloat(item.getAttribute("size")):11f;
      if(!Float.isFinite(size)||size<=0||size>256)throw new IllegalArgumentException("Font size");
      map.put(key,new Style(item.hasAttribute("family")?item.getAttribute("family"):"DejaVu Sans",size,
          Boolean.parseBoolean(item.getAttribute("bold")),
          item.hasAttribute("foreground")?parseColor(item.getAttribute("foreground")):Color.BLACK));
    }
    return map;
  }
  private static Color parseColor(String s){
    try {
      long v=s.startsWith("#")?Long.parseLong(s.substring(1),16):Long.parseLong(s);
      return new Color((int)(v&0xffffff));
    }catch(NumberFormatException e){throw new IllegalArgumentException("Invalid foreground",e);}
  }
  private static boolean splitsSurrogate(String s,int i){
    return i>0&&i<s.length()&&Character.isHighSurrogate(s.charAt(i-1))&&Character.isLowSurrogate(s.charAt(i));
  }
  private static int integer(Element e,String key){
    try{return Integer.parseInt(e.getAttribute(key));}catch(NumberFormatException x){
      throw new IllegalArgumentException("Invalid "+key,x);
    }
  }
  private static Element unique(Element prior,Element next){
    if(prior!=null)throw new IllegalArgumentException("Duplicate element: "+name(next));return next;
  }
  private static String name(Element e){return e.getLocalName()==null?e.getTagName():e.getLocalName();}
  private static List<Element> children(Element e){
    List<Element> list=new ArrayList<>();NodeList nodes=e.getChildNodes();
    for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element child)list.add(child);
    return list;
  }
}
