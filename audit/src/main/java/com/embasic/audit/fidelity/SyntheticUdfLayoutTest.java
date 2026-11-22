package com.embasic.audit.fidelity;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static com.embasic.audit.fidelity.SyntheticUdfLayout.*;
/** Synthetic-only compatibility assertions; no UDF user records. */
public final class SyntheticUdfLayoutTest {
  private static int checks;
  private static void ok(boolean test) {
    if(!test)throw new AssertionError("failed "+checks);
    checks++;
  }
  private static void rejects(Runnable action) {
    try{action.run();throw new AssertionError("expected rejection");}
    catch(IllegalArgumentException expected){checks++;}
  }
  public static void main(String[] args) throws Exception {
    Font font=new Font("DejaVu Sans",Font.PLAIN,12);
    FontRenderContext frc=new FontRenderContext(new AffineTransform(),true,true);
    ok(wrap("Kısa metin daha uzun metin",font,frc,85).size()>1);
    ok(wrap("İstanbul",font,frc,120).equals(List.of("İstanbul")));
    rejects(()->wrap("çokuzunbirkelime",font,frc,15));
    rejects(()->wrap("abc",font,frc,0));
    ok(nextTabStop(45,0,36)==72);
    ok(nextTabStop(72,0,36)==108);
    rejects(()->nextTabStop(2,0,0));
    ok(cellWidths(new float[]{60,80,70},List.of(new Cell("A",2),new Cell("B",1)))
        .equals(List.of(140f,70f)));
    rejects(()->cellWidths(new float[]{60},List.of(new Cell("A",2))));
    ok(pageCount(new float[]{30,35,40},70)==2);
    ok(pageCount(new float[]{70,70},70)==2);
    rejects(()->pageCount(new float[]{120},70));
    ok(syntheticXmlParagraphs("<template><paragraph>Türkçe ğüşöçı</paragraph></template>"
        .getBytes(StandardCharsets.UTF_8)).equals(List.of("Türkçe ğüşöçı")));
    rejects(()->{
      try{syntheticXmlParagraphs("<template><drawing/></template>".getBytes(StandardCharsets.UTF_8));}
      catch(IllegalArgumentException ex){throw ex;}
      catch(Exception ex){throw new IllegalStateException(ex);}
    });
    System.out.println("PASS: "+checks+" synthetic XML/layout checks");
  }
}
