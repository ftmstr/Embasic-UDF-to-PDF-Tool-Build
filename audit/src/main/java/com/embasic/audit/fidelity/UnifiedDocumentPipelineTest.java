package com.embasic.audit.fidelity;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
/** Synthetic-only unified model, rendering and PDF regression. */
public final class UnifiedDocumentPipelineTest {
 private static int tests;
 private static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);tests++;}
 private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 private static String document(String blocks) {
  String raw="A😀İzmir Z";
  return "<template format_id='1.0'><content>"+raw+"</content><styles>"
   +"<style name='title' size='15' bold='true' foreground='#123456' family='DejaVu Sans'/>"
   +"</styles><elements>"+blocks+"</elements></template>";
 }
 private static String para(){return "<paragraph><content resolver='title' startOffset='1' length='2'/>"
    +"<content resolver='title' startOffset='3' length='5'/></paragraph>";}
 private static String table(){return "<table widths='90,120'><row height='30'><cell colspan='2'>Header</cell></row>"
    +"<row height='30'><cell rowspan='2'>A</cell><cell>B</cell></row>"
    +"<row height='30'><cell>C</cell></row></table>";}
 private static void reject(String xml)throws Exception{
  try{UnifiedDocumentPipeline.fromXml(bytes(xml));throw new AssertionError("Expected rejection");}
  catch(IllegalArgumentException|org.xml.sax.SAXException expected){tests++;}
 }
 public static void main(String[] args)throws Exception{
  var result=UnifiedDocumentPipeline.fromXml(bytes(document(para()+table()+para())));
  ok(result.model().version().equals("1.0"),"format_id retained");
  ok(result.model().blocks().size()==3,"ordered blocks");
  ok(result.model().blocks().get(0) instanceof UnifiedDocumentPipeline.ParagraphBlock,"first paragraph");
  ok(result.model().blocks().get(1) instanceof UnifiedDocumentPipeline.TableBlock,"middle table");
  var p=(UnifiedDocumentPipeline.ParagraphBlock)result.model().blocks().get(0);
  ok(p.paragraph().runs().get(0).text().equals("😀"),"UTF16 emoji preserved");
  ok(p.paragraph().runs().get(1).text().equals("İzmir"),"Turkish Unicode preserved");
  ok(p.paragraph().runs().get(0).style().bold(),"style bold");
  ok(p.paragraph().runs().get(0).style().size()==15,"style size");
  ok(p.paragraph().runs().get(0).style().color().equals(new Color(0x123456)),"style color");
  var t=(UnifiedDocumentPipeline.TableBlock)result.model().blocks().get(1);
  ok(t.table().rows().get(0).cells().get(0).colspan()==2,"colspan preserved");
  ok(t.table().rows().get(1).cells().get(0).rowspan()==2,"rowspan preserved");
  ok(result.pages().getNumberOfPages()==1,"pageable");
  Path pdf=Files.createTempFile("embasic-unified-", ".pdf");Files.delete(pdf);
  try{
    Path f=Path.of("/usr/share/fonts/truetype/dejavu");
    PageLayoutProbe.writePdf(result.pages(),pdf,f.resolve("DejaVuSans.ttf"),f.resolve("DejaVuSans-Bold.ttf"));
    ok(Files.size(pdf)>1000,"vector PDF output");
  }finally{Files.deleteIfExists(pdf);}
  reject(document("<drawing/>"));
  reject(document("<paragraph><content resolver='missing' startOffset='1' length='2'/></paragraph>"));
  reject(document("<paragraph><content resolver='title' startOffset='2' length='1'/></paragraph>"));
  reject(document("<table widths='20'><row height='30'><cell>Overflowing cell text</cell></row></table>"));
  reject("<!DOCTYPE template [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><template format_id='1.0'><content>&x;</content><elements/></template>");
  System.out.println("PASS: "+tests+" synthetic unified document checks");
 }
}
