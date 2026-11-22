package com.embasic.audit.fidelity;
import java.awt.Color;
import java.nio.file.*;
import java.util.*;
/** Synthetic-only Unicode and page-boundary regression for UnifiedDocumentPipeline. */
public final class UnifiedRobustnessTest {
 private static int checks;
 private static void ok(boolean good,String label){if(!good)throw new AssertionError(label);checks++;}
 private static UnifiedDocumentPipeline.Output fromText(String text){
  var style=new UdfXmlModelAdapter.Style("DejaVu Sans",11,false,Color.BLACK);
  var paragraph=new UdfXmlModelAdapter.Paragraph(List.of(new UdfXmlModelAdapter.Run(text,style)));
  return UnifiedDocumentPipeline.render(new UnifiedDocumentPipeline.Model("1.0",
    List.of(new UnifiedDocumentPipeline.ParagraphBlock(paragraph))));
 }
 private static void reject(Runnable r) {
  try{r.run();throw new AssertionError("Expected rejection");}
  catch(IllegalArgumentException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
  ok(fromText("Türkçe İğüş emoji 😀").pages().getNumberOfPages()==1,"Unicode");
  ok(fromText("İ\tA\nB").pages().getNumberOfPages()==1,"tab and line break");
  ok(fromText(("satır\n").repeat(80)).pages().getNumberOfPages()>1,"newline pagination");
  ok(fromText(("kısa kelime ").repeat(1300)).pages().getNumberOfPages()>1,"long paragraph");
  reject(()->fromText("U".repeat(300)) );
  var style=new UdfXmlModelAdapter.Style("UnsupportedFont",11,false,Color.BLACK);
  reject(()->UnifiedDocumentPipeline.render(new UnifiedDocumentPipeline.Model("1.0",
   List.of(new UnifiedDocumentPipeline.ParagraphBlock(new UdfXmlModelAdapter.Paragraph(
    List.of(new UdfXmlModelAdapter.Run("x",style))))))));
  var output=fromText("Türkçe ğüş İ emoji 😀");
  Path f=Path.of("/usr/share/fonts/truetype/dejavu");
  Path pdf=Files.createTempFile("embasic-robustness-", ".pdf");Files.delete(pdf);
  try {
   PageLayoutProbe.writePdf(output.pages(),pdf,f.resolve("DejaVuSans.ttf"),f.resolve("DejaVuSans-Bold.ttf"));
   ok(Files.size(pdf)>1000,"vector PDF");
  }finally{Files.deleteIfExists(pdf);}
  System.out.println("PASS: "+checks+" synthetic robustness checks");
 }
}
