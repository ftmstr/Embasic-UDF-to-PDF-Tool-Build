package com.embasic.audit.fidelity;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
/** Synthetic mixed paragraph/table flow smoke tests. No private UDF. */
public final class MixedDocumentFlowTest {
 private static int checks;
 private static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 private static void reject(String xml)throws Exception {
  try { MixedDocumentFlow.parse(xml.getBytes(StandardCharsets.UTF_8));throw new AssertionError("Expected rejection"); }
  catch(IllegalArgumentException|org.xml.sax.SAXException expected){checks++;}
 }
 private static MixedDocumentFlow.Flow parse(String xml)throws Exception{
  return MixedDocumentFlow.parse(xml.getBytes(StandardCharsets.UTF_8));
 }
 public static void main(String[] args)throws Exception{
  String doc="<syntheticDocument><paragraph>Ön yazı İğüş</paragraph><table widths='100,130'>"
   +"<row height='26'><cell colspan='2'>Başlık</cell></row>"
   +"<row height='30'><cell rowspan='2'>A</cell><cell>B</cell></row>"
   +"<row height='30'><cell>C</cell></row></table><paragraph>Son yazı</paragraph></syntheticDocument>";
  var flow=parse(doc);
  ok(flow.blocks()==3,"order preserved as three blocks");
  ok(flow.pages()==1,"fits single page");
  BufferedImage image=new BufferedImage(596,842,BufferedImage.TYPE_INT_RGB);
  Graphics2D g=image.createGraphics();
  try{
    g.setColor(Color.WHITE);g.fillRect(0,0,596,842);
    ok(flow.pageable().getPrintable(0).print(g,flow.pageable().getPageFormat(0),0)==java.awt.print.Printable.PAGE_EXISTS,"printable");
  }finally{g.dispose();}
  ok(image.getRGB(48,70)!=Color.WHITE.getRGB(),"table border after first paragraph");
  StringBuilder large=new StringBuilder("<syntheticDocument><paragraph>Giriş</paragraph><table widths='80,100'>");
  for(int i=0;i<40;i++)large.append("<row height='30'><cell colspan='2'>Satır ").append(i).append("</cell></row>");
  large.append("</table><paragraph>Bitiş</paragraph></syntheticDocument>");
  var more=parse(large.toString());
  ok(more.pages()>1,"multipage mixed table");
  ok(more.blocks()==3,"block ordering on multipage");
  var wrapped=parse("<syntheticDocument><paragraph>"+("kelime ".repeat(200))+"</paragraph></syntheticDocument>");
  ok(wrapped.pages()>=1,"wrapped text");
  reject("<syntheticDocument><table widths='100'><row height='900'><cell>X</cell></row></table></syntheticDocument>");
  reject("<syntheticDocument><table widths='10'><row height='30'><cell>Çok uzun hücre metni</cell></row></table></syntheticDocument>");
  reject("<syntheticDocument><drawing/></syntheticDocument>");
  reject("<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><syntheticDocument><paragraph>&xxe;</paragraph></syntheticDocument>");
  Path out=Files.createTempFile("embasic-mixed-", ".pdf");Files.delete(out);
  try {
    Path f=Path.of("/usr/share/fonts/truetype/dejavu");
    PageLayoutProbe.writePdf(flow.pageable(),out,f.resolve("DejaVuSans.ttf"),f.resolve("DejaVuSans-Bold.ttf"));
    ok(Files.size(out)>1000,"vector PDF");
  }finally{Files.deleteIfExists(out);}
  System.out.println("PASS: "+checks+" synthetic mixed-flow checks");
 }
}
