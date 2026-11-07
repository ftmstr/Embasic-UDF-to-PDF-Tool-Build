package com.embasic.audit.fidelity;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.*;
/** Independent XML-subset to Java2D document adapter. No UYAP code. */
public final class XmlToPageLayout {
 private XmlToPageLayout(){}
 public record Layout(DocumentModelAdapter.DocumentModel model,int pageCount,int lineCount){}
 private static final FontRenderContext FRC=new FontRenderContext(new AffineTransform(),true,true);
 private static Font font(UdfXmlModelAdapter.Style s) {
   if(!"DejaVu Sans".equals(s.family()))throw new IllegalArgumentException("Font unsupported: "+s.family());
   return new Font(s.family(),s.bold()?Font.BOLD:Font.PLAIN,1).deriveFont(s.size());
 }
 private static float width(String t,Font f){return (float)f.getStringBounds(t,FRC).getWidth();}
 public static Layout convert(UdfXmlModelAdapter.Result xml) {
   if(xml==null)throw new IllegalArgumentException("XML absent");
   final float left=48,right=PageLayoutProbe.WIDTH-48,bottom=PageLayoutProbe.HEIGHT-48;
   List<DocumentModelAdapter.Page> pages=new ArrayList<>();
   List<DocumentModelAdapter.TextRun> lines=new ArrayList<>();
   float x=left,y=54,lineHeight=15;int count=0;
   for(var paragraph:xml.paragraphs()){
     for(var run:paragraph.runs()){
       Font f=font(run.style());
       float advance=DocumentModelAdapter.lineHeight(f,FRC);
       lineHeight=Math.max(lineHeight,advance);
       for(String token:run.text().split("(?<=\\s)|(?=\\s)",-1)){
         if(token.isEmpty())continue;
         if(token.equals("\n")){
           count++;y+=lineHeight;x=left;lineHeight=advance;
         }else if(token.equals("\t")){
           x=left+((float)Math.floor((x-left)/36)+1)*36;
           if(x>right){count++;x=left;y+=lineHeight;}
         }else{
           float w=width(token,f);
           if(w>right-left)throw new IllegalArgumentException("Unbreakable token too wide");
           if(x+w>right){count++;x=left;y+=lineHeight;lineHeight=advance;}
           if(y>bottom){pages.add(new DocumentModelAdapter.Page(lines,List.of()));lines=new ArrayList<>();y=54;}
           if(!token.isBlank())lines.add(new DocumentModelAdapter.TextRun(token,x,y,run.style().size(),run.style().bold(),run.style().color()));
           x+=w;
         }
         if(y>bottom){pages.add(new DocumentModelAdapter.Page(lines,List.of()));lines=new ArrayList<>();y=54;}
       }
     }
     count++;y+=lineHeight+5;x=left;lineHeight=15;
     if(y>bottom){pages.add(new DocumentModelAdapter.Page(lines,List.of()));lines=new ArrayList<>();y=54;}
   }
   pages.add(new DocumentModelAdapter.Page(lines,List.of()));
   return new Layout(new DocumentModelAdapter.DocumentModel(pages),pages.size(),count);
 }
 public static Layout parseAndConvert(byte[] xml)throws Exception{return convert(UdfXmlModelAdapter.parse(xml));}
}
