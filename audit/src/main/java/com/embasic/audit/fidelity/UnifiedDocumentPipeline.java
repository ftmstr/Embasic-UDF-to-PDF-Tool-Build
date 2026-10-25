package com.embasic.audit.fidelity;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.print.Printable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Independent, deliberately narrow XML fixture pipeline; no UYAP source or schema claims. */
public final class UnifiedDocumentPipeline {
  private UnifiedDocumentPipeline() {}
  public sealed interface Block permits ParagraphBlock, TableBlock {}
  public record ParagraphBlock(UdfXmlModelAdapter.Paragraph paragraph) implements Block {}
  public record TableBlock(SyntheticXmlTableBridge.Parsed table) implements Block {}
  public record Model(String version,List<Block> blocks) {
    public Model { blocks=List.copyOf(blocks); }
  }
  public record Output(Model model,PageLayoutProbe.PageLayout pages) {}
  private static final float LEFT=48,TOP=48,BOTTOM=PageLayoutProbe.HEIGHT-48,RIGHT=PageLayoutProbe.WIDTH-48;
  private static final FontRenderContext FRC=new FontRenderContext(new AffineTransform(),true,true);

  private static List<Element> children(Element parent) {
    List<Element> list=new ArrayList<>(); NodeList nodes=parent.getChildNodes();
    for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element e)list.add(e);
    return list;
  }
  private static Element find(Element parent,String name) {
    Element found=null;
    for(Element e:children(parent))if(name.equals(e.getTagName())){
      if(found!=null)throw new IllegalArgumentException("Duplicate "+name);found=e;
    }
    return found;
  }
  private static byte[] serialize(Document doc)throws Exception {
    TransformerFactory tf=TransformerFactory.newInstance();
    tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
    Transformer t=tf.newTransformer();
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    t.transform(new DOMSource(doc),new StreamResult(out));
    return out.toByteArray();
  }
  private static Document fresh()throws Exception {
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    return f.newDocumentBuilder().newDocument();
  }
  public static Model parse(byte[] xml)throws Exception {
    if(xml==null||xml.length==0||xml.length>1024*1024)throw new IllegalArgumentException("XML size");
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    f.setFeature("http://xml.org/sax/features/external-general-entities",false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
    f.setXIncludeAware(false); f.setExpandEntityReferences(false);
    Element root=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    if(!"template".equals(root.getTagName()))throw new IllegalArgumentException("Unsupported root");
    String version=root.getAttribute("format_id");
    if(version.isBlank())throw new IllegalArgumentException("Missing version");
    Element content=find(root,"content"),styles=find(root,"styles"),elements=find(root,"elements");
    if(content==null||elements==null)throw new IllegalArgumentException("Missing document content");
    for(Element e:children(root))if(!Set.of("content","styles","elements").contains(e.getTagName()))
      throw new IllegalArgumentException("Unsupported root member");
    List<Block> blocks=new ArrayList<>();
    for(Element element:children(elements)){
      if("paragraph".equals(element.getTagName())){
        Document d=fresh(); Element wrapper=d.createElement("template");wrapper.setAttribute("format_id",version);d.appendChild(wrapper);
        wrapper.appendChild(d.importNode(content,true));
        if(styles!=null)wrapper.appendChild(d.importNode(styles,true));
        Element body=d.createElement("elements");wrapper.appendChild(body);body.appendChild(d.importNode(element,true));
        var parsed=UdfXmlModelAdapter.parse(serialize(d));
        if(parsed.paragraphs().size()!=1)throw new IllegalStateException("Paragraph lost");
        blocks.add(new ParagraphBlock(parsed.paragraphs().get(0)));
      }else if("table".equals(element.getTagName())){
        Document d=fresh();Element table=(Element)d.importNode(element,true);
        d.appendChild(table);d.renameNode(table,null,"syntheticTable");
        blocks.add(new TableBlock(SyntheticXmlTableBridge.parse(serialize(d))));
      }else throw new IllegalArgumentException("Unknown block: "+element.getTagName());
    }
    if(blocks.isEmpty())throw new IllegalArgumentException("Empty document");
    return new Model(version,blocks);
  }

  private record DrawText(String text,float x,float y,UdfXmlModelAdapter.Style style){}
  private record DrawCell(TableGeometry.Placement cell){}
  public static Output render(Model model) {
    if(model==null)throw new IllegalArgumentException("model");
    List<List<Object>> pages=new ArrayList<>();pages.add(new ArrayList<>());
    int page=0;float x=LEFT,y=TOP+12,line=16;
    for(Block block:model.blocks()){
      if(block instanceof ParagraphBlock p){
        for(var run:p.paragraph().runs()){
          var style=run.style();
          if(!"DejaVu Sans".equals(style.family()))throw new IllegalArgumentException("Unresolved font "+style.family());
          Font font=new Font(style.family(),style.bold()?Font.BOLD:Font.PLAIN,1).deriveFont(style.size());
          float advance=DocumentModelAdapter.lineHeight(font,FRC);
          line=Math.max(line,advance);
          for(String token:run.text().split("(?<=\\s)|(?=\\s)",-1)){
            if(token.isEmpty())continue;
            if(token.equals("\n")){x=LEFT;y+=line;line=advance;continue;}
            if(token.equals("\t")){x=LEFT+((float)Math.floor((x-LEFT)/36)+1)*36;continue;}
            float width=(float)font.getStringBounds(token,FRC).getWidth();
            if(width>RIGHT-LEFT)throw new IllegalArgumentException("Unbreakable text");
            if(x+width>RIGHT){x=LEFT;y+=line;line=advance;}
            if(y>BOTTOM){pages.add(new ArrayList<>());page++;y=TOP+12;}
            if(!token.isBlank())pages.get(page).add(new DrawText(token,x,y,style));
            x+=width;
          }
        }
        x=LEFT;y+=line+6;line=16;
        if(y>BOTTOM){pages.add(new ArrayList<>());page++;y=TOP+12;}
      }else if(block instanceof TableBlock t){
        if(x!=LEFT){x=LEFT;y+=line;}
        List<TableGeometry.Row> rows=t.table().rows();float[] widths=t.table().widths();
        float total=0;for(float w:widths)total+=w;
        if(total>RIGHT-LEFT)throw new IllegalArgumentException("Table width");
        int start=0;
        while(start<rows.size()){
          float remaining=BOTTOM-y;
          if(remaining<=0){pages.add(new ArrayList<>());page++;y=TOP;remaining=BOTTOM-y;}
          int end=start;float used=0;
          while(end<rows.size()&&used+rows.get(end).height()<=remaining){used+=rows.get(end).height();end++;}
          if(end==start){if(y<=TOP)throw new IllegalArgumentException("Row too tall");pages.add(new ArrayList<>());page++;y=TOP;continue;}
          boolean crosses=false;
          for(int r=start;r<end;r++)for(var cell:rows.get(r).cells())if(r+cell.rowspan()>end)crosses=true;
          if(crosses){if(y>TOP){pages.add(new ArrayList<>());page++;y=TOP;continue;}throw new IllegalArgumentException("Rowspan page overflow");}
          var geometry=TableGeometry.layout(rows.subList(start,end),widths,LEFT,y,remaining);
          Font font=new Font("DejaVu Sans",Font.PLAIN,10);
          for(var cell:geometry.cells()){
            if(font.getStringBounds(cell.text(),FRC).getWidth()>cell.width()-6||cell.height()<17)
              throw new IllegalArgumentException("Cell overflow");
            pages.get(page).add(new DrawCell(cell));
          }
          y+=used;start=end;
        }
        x=LEFT;y+=8;line=16;
      }
    }
    List<Printable> printables=new ArrayList<>();
    for(List<Object> commands:pages){
      var frozen=List.copyOf(commands);
      printables.add((graphics,format,index)->{
        Graphics2D g=(Graphics2D)graphics;
        for(Object command:frozen){
          if(command instanceof DrawText t){
            g.setColor(t.style().color());
            g.setFont(new Font("DejaVu Sans",t.style().bold()?Font.BOLD:Font.PLAIN,1).deriveFont(t.style().size()));
            g.drawString(t.text(),t.x(),t.y());
          }else if(command instanceof DrawCell c){
            var z=c.cell();
            g.setColor(Color.BLACK);g.setStroke(new BasicStroke(.7f));
            g.draw(new java.awt.geom.Rectangle2D.Float(z.x(),z.y(),z.width(),z.height()));
            g.setFont(new Font("DejaVu Sans",Font.PLAIN,10));
            g.drawString(z.text(),z.x()+3,z.y()+13);
          }
        }
        return Printable.PAGE_EXISTS;
      });
    }
    return new Output(model,new PageLayoutProbe.PageLayout(printables));
  }
  public static Output fromXml(byte[] xml)throws Exception{return render(parse(xml));}
}
