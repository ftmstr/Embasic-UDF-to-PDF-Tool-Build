package com.embasic.audit.fidelity;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

/** PDFBox path/stroke inspection of synthetic PDF only; PDF coordinates are bottom-origin. */
public final class VectorStrokeOracle extends PDFGraphicsStreamEngine {
 private final GeneralPath path=new GeneralPath();
 private final List<Rectangle2D> strokes=new ArrayList<>();
 private Point2D current;
 public VectorStrokeOracle(PDPage page){super(page);}
 public List<Rectangle2D> strokes(){return List.copyOf(strokes);}
 @Override public void appendRectangle(Point2D p0,Point2D p1,Point2D p2,Point2D p3){
   path.moveTo((float)p0.getX(),(float)p0.getY());path.lineTo((float)p1.getX(),(float)p1.getY());
   path.lineTo((float)p2.getX(),(float)p2.getY());path.lineTo((float)p3.getX(),(float)p3.getY());path.closePath();
   current=p0;
 }
 @Override public void drawImage(PDImage image) {}
 @Override public void clip(int windingRule) {path.setWindingRule(windingRule);}
 @Override public void moveTo(float x,float y){path.moveTo(x,y);current=new Point2D.Float(x,y);}
 @Override public void lineTo(float x,float y){path.lineTo(x,y);current=new Point2D.Float(x,y);}
 @Override public void curveTo(float x1,float y1,float x2,float y2,float x3,float y3){path.curveTo(x1,y1,x2,y2,x3,y3);current=new Point2D.Float(x3,y3);}
 @Override public Point2D getCurrentPoint(){return current;}
 @Override public void closePath(){path.closePath();}
 @Override public void endPath(){path.reset();}
 @Override public void strokePath(){strokes.add(path.getBounds2D());path.reset();}
 @Override public void fillPath(int windingRule){path.reset();}
 @Override public void fillAndStrokePath(int windingRule){strokePath();}
 @Override public void shadingFill(org.apache.pdfbox.cos.COSName shadingName){}
 public static boolean hasRectangle(List<Rectangle2D> paths,double x,double y,double width,double height,double tolerance){
   for(Rectangle2D r:paths)if(Math.abs(r.getX()-x)<=tolerance&&Math.abs(r.getY()-y)<=tolerance
       &&Math.abs(r.getWidth()-width)<=tolerance&&Math.abs(r.getHeight()-height)<=tolerance)return true;
   return false;
 }
}
