package com.embasic.audit.fidelity;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
/** Runnable synthetic PDF vector table regression. No confidential documents. */
public final class TablePageRendererTest {
 private static int checks;
 private static void ok(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 private static void reject(Runnable action){
   try{action.run();throw new AssertionError("Missing expected rejection");}
   catch(IllegalArgumentException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
   var rowA=new TableGeometry.Row(java.util.List.of(new TableGeometry.Cell(2,1,"Header")),30);
   var rowB=new TableGeometry.Row(java.util.List.of(new TableGeometry.Cell(1,2,"A"),new TableGeometry.Cell(1,1,"B")),40);
   var rowC=new TableGeometry.Row(java.util.List.of(new TableGeometry.Cell(1,1,"C")),40);
   var geometry=TableGeometry.layout(java.util.List.of(rowA,rowB,rowC),new float[]{100,120},48,48,200);
   ok(geometry.pages()==1,"single page");
   ok(geometry.cells().get(0).width()==220,"colspan");
   ok(geometry.cells().get(1).height()==80,"rowspan");
   var layout=TablePageRenderer.render(geometry,PageLayoutProbe.HEIGHT-48);
   ok(layout.getNumberOfPages()==1,"pageable count");
   BufferedImage raster=new BufferedImage(596,842,BufferedImage.TYPE_INT_RGB);
   Graphics2D g=raster.createGraphics();try{
     g.setColor(Color.WHITE);g.fillRect(0,0,596,842);
     ok(layout.getPrintable(0).print(g,layout.getPageFormat(0),0)==java.awt.print.Printable.PAGE_EXISTS,"java2d draw");
   }finally{g.dispose();}
   ok(raster.getRGB(48,48)!=Color.WHITE.getRGB(),"border pixels");
   var many=new java.util.ArrayList<TableGeometry.Row>();
   for(int i=0;i<30;i++)many.add(new TableGeometry.Row(java.util.List.of(new TableGeometry.Cell(2,1,"Row "+i)),35));
   var paged=TablePageRenderer.synthetic(many,new float[]{90,130});
   ok(paged.getNumberOfPages()>1,"page splitting");
   reject(()->TablePageRenderer.synthetic(java.util.List.of(new TableGeometry.Row(java.util.List.of(new TableGeometry.Cell(1,1,"TEXT LONGER THAN NARROW CELL")),20)),new float[]{8}));
   Path pdf=Files.createTempFile("embasic-table-vector-", ".pdf");Files.delete(pdf);
   try{
     Path f=Path.of("/usr/share/fonts/truetype/dejavu");
     PageLayoutProbe.writePdf(layout,pdf,f.resolve("DejaVuSans.ttf"),f.resolve("DejaVuSans-Bold.ttf"));
     ok(Files.size(pdf)>1000,"vector PDF generated");
     ok(Files.readAllBytes(pdf)[0]=='%',"PDF header");
   }finally{Files.deleteIfExists(pdf);}
   System.out.println("PASS: "+checks+" synthetic vector-table tests");
 }
}
