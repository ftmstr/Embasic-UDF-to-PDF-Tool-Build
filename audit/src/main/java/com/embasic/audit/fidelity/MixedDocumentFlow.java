package com.embasic.audit.fidelity;
import java.awt.*;
import java.awt.print.Printable;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
/** Synthetic ordered paragraph/table flow; no claim of genuine UDF schema support. */
public final class MixedDocumentFlow {
  private MixedDocumentFlow(){}
  public record Flow(PageLayoutProbe.PageLayout pageable,int blocks,int pages){}
  private sealed interface Block permits TextBlock, TableBlock {}
  private record TextBlock(String text) implements Block {}
  private record TableBlock(java.util.List<TableGeometry.Row> rows,float[] widths) implements Block {}
  private static final float LEFT=48,TOP=48,BOTTOM=PageLayoutProbe.HEIGHT-48,COLUMN=PageLayoutProbe.WIDTH-96;
  public static Flow parse(byte[] xml) throws Exception {
    if(xml==null||xml.length==0||xml.length>1024*1024)throw new IllegalArgumentException("XML size");
    DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
    factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
    Element root=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    if(!"syntheticDocument".equals(root.getTagName()))throw new IllegalArgumentException("Unsupported root");
    java.util.List<Block> blocks=new ArrayList<>();
    for(Element e:elements(root)){
      if("paragraph".equals(e.getTagName())){
        if(!elements(e).isEmpty())throw new IllegalArgumentException("Nested paragraph");
        blocks.add(new TextBlock(e.getTextContent()));
      }else if("table".equals(e.getTagName())){
        String[] nums=e.getAttribute("widths").split(",");
        if(nums.length==0||nums.length>64)throw new IllegalArgumentException("Columns");
        float[] widths=new float[nums.length];float sum=0;
        for(int i=0;i<nums.length;i++){widths[i]=Float.parseFloat(nums[i]);if(!Float.isFinite(widths[i])||widths[i]<=0)throw new IllegalArgumentException("Width");sum+=widths[i];}
        if(sum>COLUMN)throw new IllegalArgumentException("Table wider than page");
        java.util.List<TableGeometry.Row> rows=new ArrayList<>();
        for(Element row:elements(e)){
          if(!"row".equals(row.getTagName()))throw new IllegalArgumentException("Unknown row");
          float height=Float.parseFloat(row.getAttribute("height"));
          java.util.List<TableGeometry.Cell> cells=new ArrayList<>();
          for(Element cell:elements(row)){
            if(!"cell".equals(cell.getTagName())||!elements(cell).isEmpty())throw new IllegalArgumentException("Unknown cell");
            int cs=cell.hasAttribute("colspan")?Integer.parseInt(cell.getAttribute("colspan")):1;
            int rs=cell.hasAttribute("rowspan")?Integer.parseInt(cell.getAttribute("rowspan")):1;
            cells.add(new TableGeometry.Cell(cs,rs,cell.getTextContent()));
          }
          rows.add(new TableGeometry.Row(cells,height));
        }
        blocks.add(new TableBlock(java.util.List.copyOf(rows),widths));
      }else throw new IllegalArgumentException("Unsupported element: "+e.getTagName());
    }
    java.util.List<java.util.List<java.util.function.Consumer<Graphics2D>>> pages=new ArrayList<>();
    pages.add(new ArrayList<>());int page=0;float y=TOP;
    Font font=new Font("DejaVu Sans",Font.PLAIN,11);
    BufferedImageMetrics metrics=new BufferedImageMetrics(font);
    for(Block block:blocks){
      if(block instanceof TextBlock paragraph){
        for(String line:paragraph.text().split("\\n",-1)){
          StringBuilder current=new StringBuilder();
          for(String word:line.split(" +",-1)){
            String candidate=current.length()==0?word:current+" "+word;
            if(metrics.width(candidate)>COLUMN&&current.length()>0){
              if(y+16>BOTTOM){pages.add(new ArrayList<>());page++;y=TOP;}
              float baseline=y+12;String output=current.toString();
              pages.get(page).add(g->{g.setFont(font);g.setColor(Color.BLACK);g.drawString(output,LEFT,baseline);});
              y+=16;current.setLength(0);current.append(word);
            }else{current.setLength(0);current.append(candidate);}
            if(metrics.width(current.toString())>COLUMN)throw new IllegalArgumentException("Unbreakable token");
          }
          if(y+16>BOTTOM){pages.add(new ArrayList<>());page++;y=TOP;}
          float baseline=y+12;String output=current.toString();
          pages.get(page).add(g->{g.setFont(font);g.setColor(Color.BLACK);g.drawString(output,LEFT,baseline);});
          y+=16;
        }
        y+=6;
      }else if(block instanceof TableBlock table){
        // Reserve row groups so a rowspan never crosses a page.
        java.util.List<TableGeometry.Row> rows=table.rows();
        int start=0;
        while(start<rows.size()){
          float remaining=BOTTOM-y;
          if(remaining<18){pages.add(new ArrayList<>());page++;y=TOP;remaining=BOTTOM-y;}
          int end=start;float total=0;
          while(end<rows.size()&&total+rows.get(end).height()<=remaining){total+=rows.get(end).height();end++;}
          if(end==start){if(y==TOP)throw new IllegalArgumentException("Table row exceeds page");pages.add(new ArrayList<>());page++;y=TOP;continue;}
          // Do not split a spanning cell: push the spanning group to next page.
          boolean spans=false;
          for(int r=start;r<end;r++)for(var cell:rows.get(r).cells())if(r+cell.rowspan()>end)spans=true;
          if(spans){if(y>TOP){pages.add(new ArrayList<>());page++;y=TOP;continue;}throw new IllegalArgumentException("Rowspan exceeds page");}
          java.util.List<TableGeometry.Row> group=rows.subList(start,end);
          TableGeometry.Result geo=TableGeometry.layout(group,table.widths(),LEFT,y,remaining);
          if(geo.pages()!=1)throw new IllegalStateException("Unexpected split");
          for(var cell:geo.cells()){
            String label=cell.text();
            if(metrics.width(label)>cell.width()-6||cell.height()<16)throw new IllegalArgumentException("Cell text overflow");
            var shape=new java.awt.geom.Rectangle2D.Float(cell.x(),cell.y(),cell.width(),cell.height());
            float baseline=cell.y()+13;
            pages.get(page).add(g->{g.setColor(Color.BLACK);g.setStroke(new BasicStroke(0.7f));g.draw(shape);g.setFont(font);g.drawString(label,cell.x()+3,baseline);});
          }
          y+=total;start=end;
        }
        y+=8;
      }
    }
    java.util.List<Printable> outputs=new ArrayList<>();
    for(var commands:pages){
      var stable=java.util.List.copyOf(commands);
      outputs.add((graphics,format,index)->{Graphics2D g=(Graphics2D)graphics;for(var command:stable)command.accept(g);return Printable.PAGE_EXISTS;});
    }
    return new Flow(new PageLayoutProbe.PageLayout(outputs),blocks.size(),outputs.size());
  }
  private static java.util.List<Element> elements(Element p){
    java.util.List<Element> out=new ArrayList<>();NodeList ns=p.getChildNodes();
    for(int i=0;i<ns.getLength();i++)if(ns.item(i) instanceof Element e)out.add(e);
    return out;
  }
  private static final class BufferedImageMetrics {
    private final java.awt.FontMetrics metrics;
    BufferedImageMetrics(Font font){
      var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);
      var graphics=image.createGraphics();
      try{graphics.setFont(font);metrics=graphics.getFontMetrics();}finally{graphics.dispose();}
    }
    int width(String text){return metrics.stringWidth(text);}
  }
}
