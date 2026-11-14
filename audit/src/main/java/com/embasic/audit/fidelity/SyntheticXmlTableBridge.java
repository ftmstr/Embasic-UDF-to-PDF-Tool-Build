package com.embasic.audit.fidelity;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
/**
 * Synthetic table XML bridge only. Does not claim support for UYAP table schemas.
 * Input: <syntheticTable widths="100,120"><row height="30"><cell colspan="2">A</cell></row></syntheticTable>
 */
public final class SyntheticXmlTableBridge {
 private SyntheticXmlTableBridge() {}
 public record Parsed(List<TableGeometry.Row> rows,float[] widths){}
 public static Parsed parse(byte[] xml)throws Exception{
  if(xml==null||xml.length==0||xml.length>1024*1024)throw new IllegalArgumentException("XML size");
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
  f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);
  f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
  f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
  f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  Element root=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
  if(!"syntheticTable".equals(root.getTagName()))throw new IllegalArgumentException("Not a synthetic table");
  String[] parts=root.getAttribute("widths").split(",");
  if(parts.length==0||parts.length>64)throw new IllegalArgumentException("Columns");
  float[] widths=new float[parts.length];
  for(int i=0;i<parts.length;i++)widths[i]=Float.parseFloat(parts[i].trim());
  List<TableGeometry.Row> rows=new ArrayList<>();
  NodeList n=root.getChildNodes();
  for(int i=0;i<n.getLength();i++){
   if(!(n.item(i) instanceof Element row))continue;
   if(!"row".equals(row.getTagName()))throw new IllegalArgumentException("Unknown row");
   float height=Float.parseFloat(row.getAttribute("height"));
   List<TableGeometry.Cell> cells=new ArrayList<>();NodeList cn=row.getChildNodes();
   for(int j=0;j<cn.getLength();j++){
    if(!(cn.item(j) instanceof Element cell))continue;
    if(!"cell".equals(cell.getTagName()))throw new IllegalArgumentException("Unknown cell");
    int colspan=cell.hasAttribute("colspan")?Integer.parseInt(cell.getAttribute("colspan")):1;
    int rowspan=cell.hasAttribute("rowspan")?Integer.parseInt(cell.getAttribute("rowspan")):1;
    if(cell.getElementsByTagName("*").getLength()>0)throw new IllegalArgumentException("Nested cell XML");
    cells.add(new TableGeometry.Cell(colspan,rowspan,cell.getTextContent()));
   }
   rows.add(new TableGeometry.Row(cells,height));
  }
  return new Parsed(List.copyOf(rows),widths);
 }
 public static PageLayoutProbe.PageLayout printable(byte[] xml)throws Exception{
  Parsed p=parse(xml);
  return TablePageRenderer.synthetic(p.rows(),p.widths());
 }
}
