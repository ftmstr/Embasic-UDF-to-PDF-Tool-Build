package com.embasic.audit.fidelity;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Synthetic only. Tests the independent UDF XML-subset -> Java2D vector PDF path. */
public final class XmlToPageLayoutTest {
 private static int checks;
 private static void ok(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
 private static byte[] xml(String text){
  String safe=text.replace("&","&amp;").replace("<","&lt;");
  String s="<template format_id='1.0'><content>"+safe+"</content><elements><paragraph><content startOffset='0' length='"+text.length()+"'/></paragraph></elements></template>";
  return s.getBytes(StandardCharsets.UTF_8);
 }
 public static void main(String[] args)throws Exception {
  var one=XmlToPageLayout.parseAndConvert(xml("Merhaba Dünya"));
  ok(one.pageCount()==1,"single page");
  ok(one.lineCount()==1,"single line");
  ok(!one.model().pages().get(0).runs().isEmpty(),"nonempty runs");
  var wrap=XmlToPageLayout.parseAndConvert(xml("A ".repeat(120)));
  ok(wrap.lineCount()>1,"soft wrapping");
  var tab=XmlToPageLayout.parseAndConvert(xml("A\tB"));
  var t=tab.model().pages().get(0).runs();
  ok(t.size()==2,"tab gives two visible runs");
  ok(t.get(1).x()>t.get(0).x(),"tab advanced");
  var newline=XmlToPageLayout.parseAndConvert(xml("A\nB"));
  ok(newline.lineCount()>1,"explicit newline");
  var huge=XmlToPageLayout.parseAndConvert(xml("A ".repeat(10000)));
  ok(huge.pageCount()>1,"overflow pagination");
  boolean rejects=false;
  try { XmlToPageLayout.parseAndConvert(xml("X".repeat(200))); }
  catch(IllegalArgumentException e){rejects=true;}
  ok(rejects,"fail closed on unbreakable text");
  var printable=DocumentModelAdapter.adapt(one.model());
  ok(printable.getNumberOfPages()==1,"Pageable integration");
  Path out=Files.createTempFile("embasic-turn24-", ".pdf");Files.delete(out);
  try {
    Path f=Path.of("/usr/share/fonts/truetype/dejavu");
    PageLayoutProbe.writePdf(printable,out,f.resolve("DejaVuSans.ttf"),f.resolve("DejaVuSans-Bold.ttf"));
    ok(Files.size(out)>1000,"vector PDF produced");
  }finally { Files.deleteIfExists(out); }
  System.out.println("PASS: "+checks+" synthetic XML-to-vector-PDF checks");
 }
}
