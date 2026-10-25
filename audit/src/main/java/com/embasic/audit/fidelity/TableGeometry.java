package com.embasic.audit.fidelity;
import java.util.*;
/** Independent synthetic table geometry: no UYAP code or confidential documents. */
public final class TableGeometry {
 private TableGeometry(){}
 public record Cell(int colspan,int rowspan,String text){
  public Cell{if(colspan<1||rowspan<1||text==null)throw new IllegalArgumentException("cell");}
 }
 public record Row(List<Cell> cells,float height){
  public Row{cells=List.copyOf(cells);if(cells.isEmpty()||!Float.isFinite(height)||height<=0)throw new IllegalArgumentException("row");}
 }
 public record Placement(int row,int column,int colspan,int rowspan,float x,float y,float width,float height,String text){}
 public record Result(List<Placement> cells,List<Integer> pageStarts,int pages){}
 public static Result layout(List<Row> rows,float[] widths,float left,float top,float availableHeight){
  if(rows==null||rows.isEmpty()||widths==null||widths.length==0||!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(availableHeight)||availableHeight<=0)throw new IllegalArgumentException("geometry");
  float total=0;for(float w:widths){if(!Float.isFinite(w)||w<=0)throw new IllegalArgumentException("column width");total+=w;}
  if(!Float.isFinite(total))throw new IllegalArgumentException("total width");
  int n=rows.size(),cols=widths.length;boolean[][] filled=new boolean[n][cols];
  List<int[]> locations=new ArrayList<>();List<Cell> used=new ArrayList<>();
  float[] heights=new float[n];
  for(int i=0;i<n;i++)heights[i]=rows.get(i).height();
  for(int r=0;r<n;r++){
   int cursor=0;
   for(Cell cell:rows.get(r).cells()){
    while(cursor<cols&&filled[r][cursor])cursor++;
    if(cursor+cell.colspan()>cols||r+cell.rowspan()>n)throw new IllegalArgumentException("span outside table");
    for(int rr=r;rr<r+cell.rowspan();rr++)for(int cc=cursor;cc<cursor+cell.colspan();cc++){
     if(filled[rr][cc])throw new IllegalArgumentException("overlapping span");
     filled[rr][cc]=true;
    }
    locations.add(new int[]{r,cursor});used.add(cell);cursor+=cell.colspan();
   }
   for(int cc=0;cc<cols;cc++)if(!filled[r][cc])throw new IllegalArgumentException("table hole");
  }
  float[] rowTop=new float[n];List<Integer> starts=new ArrayList<>();starts.add(0);float y=0;
  for(int i=0;i<n;i++){
   if(heights[i]>availableHeight)throw new IllegalArgumentException("row too tall");
   if(y+heights[i]>availableHeight){starts.add(i);y=0;}
   rowTop[i]=y;y+=heights[i];
  }
  List<Placement> out=new ArrayList<>();
  for(int i=0;i<used.size();i++){
   Cell cell=used.get(i);int r=locations.get(i)[0],c=locations.get(i)[1];
   float w=0,h=0,x=left;
   for(int k=0;k<c;k++)x+=widths[k];
   for(int k=c;k<c+cell.colspan();k++)w+=widths[k];
   for(int k=r;k<r+cell.rowspan();k++)h+=heights[k];
   for(int s:starts)if(s>r&&s<r+cell.rowspan())throw new IllegalArgumentException("rowspan crosses page");
   if(rowTop[r]+h>availableHeight)throw new IllegalArgumentException("span overflow");
   out.add(new Placement(r,c,cell.colspan(),cell.rowspan(),x,top+rowTop[r],w,h,cell.text()));
  }
  return new Result(List.copyOf(out),List.copyOf(starts),starts.size());
 }
 public static void requireFont(String requested,Set<String> available){
  if(requested==null||available==null||!available.contains(requested))throw new IllegalArgumentException("missing font: "+requested);
 }
}
