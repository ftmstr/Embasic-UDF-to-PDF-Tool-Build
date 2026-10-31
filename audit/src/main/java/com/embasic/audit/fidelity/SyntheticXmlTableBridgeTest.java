package com.embasic.audit.fidelity;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Synthetic XML -> table geometry -> vector PDF regression. */
public final class SyntheticXmlTableBridgeTest {
 private static int count;
 private static void ok(boolean test,String name){if(!test)throw new AssertionError(name);count++;}
 private static byte[] xml(String s){return s.getBytes(StandardCharsets.UTF_8);}
 private static void rejects(String s)throws Exception{
  try{SyntheticXmlTableBridge.printable(xml(s));throw new AssertionError("expected rejection");}
  catch(IllegalArgumentException|org.xml.sax.SAXException expected){count++;}
 }
 public static void main(String[] args)throws Exception{
  String single="<syntheticTable widths='100,120'><row height='30'><cell colspan='2'>Header</cell></row>"
   +"<row height='30'><cell rowspan='2'>A</cell><cell>B</cell></row>"
   +"<row height='30'><cell>C</cell></row></syntheticTable>";
  var parsed=SyntheticXmlTableBridge.parse(xml(single));
  ok(parsed.rows().size()==3,"rows");ok(parsed.widths().length==2,"columns");
  var model=TableGeometry.layout(parsed.rows(),parsed.widths(),48,48,700);
  ok(model.cells().get(0).width()==220,"colspan");
  ok(model.cells().get(1).height()==60,"rowspan");
  var printable=SyntheticXmlTableBridge.printable(xml(single));
  ok(printable.getNumberOfPages()==1,"one page");
  StringBuilder many=new StringBuilder("<syntheticTable widths='100,120'>");
  for(int i=0;i<30;i++)many.append("<row height='36'><cell colspan='2'>R").append(i).append("</cell></row>");
  many.append("</syntheticTable>");
  var multi=SyntheticXmlTableBridge.printable(xml(many.toString()));
  ok(multi.getNumberOfPages()>1,"multiple pages");
  Path path=Files.createTempFile("embasic-xml-table-", ".pdf");Files.delete(path);
  try{
    var fonts=Path.of("/usr/share/fonts/truetype/dejavu");
    PageLayoutProbe.writePdf(multi,path,fonts.resolve("DejaVuSans.ttf"),fonts.resolve("DejaVuSans-Bold.ttf"));
    ok(Files.size(path)>1000,"vector PDF");
  }finally{Files.deleteIfExists(path);}
  rejects("<syntheticTable widths='100'><row height='30'><cell colspan='2'>X</cell></row></syntheticTable>");
  rejects("<syntheticTable widths='100'><row height='30'><cell rowspan='2'>X</cell></row></syntheticTable>");
  rejects("<syntheticTable widths='100'><other/></syntheticTable>");
  rejects("<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><syntheticTable widths='100'><row height='30'><cell>&xxe;</cell></row></syntheticTable>");
  System.out.println("PASS: "+count+" synthetic XML table integration checks");
 }
}
