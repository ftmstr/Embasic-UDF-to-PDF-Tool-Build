package com.embasic.audit.fidelity;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
/** Synthetic tests with fictional XML; no UYAP assets. */
public final class UdfXmlModelAdapterTest {
 private static int checks;
 private static void ok(boolean p){if(!p)throw new AssertionError("check "+checks);checks++;}
 private static void rejects(String s)throws Exception{
   try{UdfXmlModelAdapter.parse(s.getBytes(StandardCharsets.UTF_8));throw new AssertionError("expected rejection");}
   catch(IllegalArgumentException|org.xml.sax.SAXException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
   String raw="A😀İzmir";
   String xml="<template format_id='1.0'><content>"+raw+"</content><styles><style name='s' family='DejaVu Sans' size='12' bold='true' foreground='#123456'/></styles><elements><paragraph><content resolver='s' startOffset='1' length='2'/><content resolver='s' size='14' startOffset='3' length='5'/></paragraph></elements></template>";
   var result=UdfXmlModelAdapter.parse(xml.getBytes(StandardCharsets.UTF_8));
   ok(result.version().equals("1.0"));
   ok(result.paragraphs().size()==1);
   ok(result.paragraphs().get(0).runs().size()==2);
   ok(result.paragraphs().get(0).runs().get(0).text().equals("😀"));
   ok(result.paragraphs().get(0).runs().get(1).text().equals("İzmir"));
   ok(result.paragraphs().get(0).runs().get(0).style().bold());
   ok(result.paragraphs().get(0).runs().get(0).style().size()==12);
   ok(result.paragraphs().get(0).runs().get(1).style().size()==14);
   ok(result.paragraphs().get(0).runs().get(0).style().color().equals(new Color(0x123456)));
   rejects(xml.replace("startOffset='1'","startOffset='2'"));
   rejects(xml.replace("resolver='s'","resolver='missing'"));
   rejects(xml.replace("format_id='1.0'",""));
   rejects(xml.replace("<elements>","<elements><drawing/>"));
   rejects(xml.replace("length='5'","length='99'"));
   rejects("<!DOCTYPE template [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><template format_id='1.0'><content>&xxe;</content><elements/></template>");
   ok(UdfXmlModelAdapter.parse("<template format_id='0.9'><content>x</content><elements/></template>".getBytes(StandardCharsets.UTF_8)).version().equals("0.9"));
   System.out.println("PASS: "+checks+" synthetic UDF XML checks");
 }
}
