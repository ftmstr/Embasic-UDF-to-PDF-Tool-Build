package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Textual target status is testable independently of Swing accessibility providers. */
final class GridDropAccessibilityTest {
    @Test void includesPositionAndBeforeAfterMeaning() {
        assertEquals("1. kartın önüne taşı. Esc ile iptal edebilirsiniz.",
                GridDropIndicator.description(0, 160, false));
        assertEquals("160. kartın arkasına taşı. Esc ile iptal edebilirsiniz.",
                GridDropIndicator.description(159, 160, true));
        assertEquals("Seçilen dosyaları listenin sonuna taşı.",
                GridDropIndicator.description(160, 160, false));
    }
    @Test void invalidTargetsNeverClaimValidReorderPosition() {
        for(int index : List.of(-50,-1,161,999)) {
            assertTrue(GridDropIndicator.description(index,160,false)
                    .contains("Geçerli bırakma hedefi yok"));
        }
        assertTrue(GridDropIndicator.description(0,-1,true)
                .contains("Geçerli bırakma hedefi yok"));
    }
}
