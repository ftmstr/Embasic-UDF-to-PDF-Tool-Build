package com.embasic.audit.fidelity;
import java.util.*;
/** Fictional data only; independent geometry regression. */
public final class TableGeometryTest {
 private static int checks;
 private static void ok(boolean b){if(!b)throw new AssertionError("case "+checks);checks++;}
 private static void reject(Runnable r){
  try{r.run();throw new AssertionError("expected exception");}
  catch(IllegalArgumentException e){checks++;}
 }
 public static void main(String[] args){
  var rows=List.of(
   new TableGeometry.Row(List.of(new TableGeometry.Cell(2,1,"Header")),22),
   new TableGeometry.Row(List.of(new TableGeometry.Cell(1,1,"A"),new TableGeometry.Cell(1,1,"B")),30));
  var x=TableGeometry.layout(rows,new float[]{80,120},40,50,100);
  ok(x.pages()==1);ok(x.cells().size()==3);ok(x.cells().get(0).width()==200);
  ok(x.cells().get(2).x()==120);ok(x.cells().get(2).y()==72);
  var span=TableGeometry.layout(List.of(
   new TableGeometry.Row(List.of(new TableGeometry.Cell(1,2,"a"),new TableGeometry.Cell(1,1,"b")),30),
   new TableGeometry.Row(List.of(new TableGeometry.Cell(1,1,"c")),40)),
   new float[]{30,50},0,0,100);
  ok(span.cells().get(0).height()==70);
  ok(span.cells().get(2).column()==1);
  var paged=TableGeometry.layout(rows,new float[]{80,120},0,0,45);
  ok(paged.pages()==2);ok(paged.pageStarts().equals(List.of(0,1)));
  reject(()->TableGeometry.layout(rows,new float[]{80},0,0,100));
  reject(()->TableGeometry.layout(List.of(new TableGeometry.Row(List.of(new TableGeometry.Cell(1,2,"x")),30)),new float[]{50},0,0,100));
  reject(()->TableGeometry.layout(List.of(
   new TableGeometry.Row(List.of(new TableGeometry.Cell(1,2,"a"),new TableGeometry.Cell(1,1,"b")),40),
   new TableGeometry.Row(List.of(new TableGeometry.Cell(1,1,"c")),40)),new float[]{40,40},0,0,50));
  reject(()->TableGeometry.layout(rows,new float[]{80,120},0,0,20));
  reject(()->TableGeometry.layout(rows,new float[]{Float.NaN,30},0,0,100));
  TableGeometry.requireFont("DejaVu Sans",Set.of("DejaVu Sans"));checks++;
  reject(()->TableGeometry.requireFont("MissingFont",Set.of("DejaVu Sans")));
  System.out.println("PASS: "+checks+" synthetic table geometry checks");
 }
}
