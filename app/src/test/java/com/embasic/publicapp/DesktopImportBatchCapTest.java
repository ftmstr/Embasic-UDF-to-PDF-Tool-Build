package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rights-clean, headless tests for an Explorer/provider-owned oversized
 * batch. No large arrays are allocated, and no Windows UI is required.
 */
final class DesktopImportBatchCapTest {

    @Test void oversizedNativeExplorerListFailsBeforeTouchingAnyProviderEntry() {
        List<File> oversized = new AbstractList<>() {
            @Override public int size() { return 4_096; }
            @Override public File get(int index) {
                throw new AssertionError("Oversized Explorer payload must not be iterated");
            }
        };
        var error = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.extractDroppedPaths(oversized));
        assertTrue(error.getMessage().contains("160"), error.getMessage());
        assertTrue(error.getMessage().contains("hiçbir dosya"), error.getMessage());
    }

    @Test void oversizedChooserArrayFailsBeforeReadingNullOrConvertingFiles() {
        // 161 entries are enough to prove the bound without allocating a
        // misleading multi-million-file fixture. Every entry is deliberately
        // null to prove the early length gate runs before dereferencing.
        File[] tooMany = new File[FileImportPolicy.MAX_CARDS + 1];
        var error = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.chooserPaths(tooMany, new File("ignored.udf")));
        assertTrue(error.getMessage().contains("160"), error.getMessage());
        assertTrue(error.getMessage().contains("hiçbir dosya"), error.getMessage());
    }

    @Test void directImportGuardIsConstantTimeAndNormalBatchesArePreserved() {
        assertThrows(IllegalArgumentException.class, () ->
                DesktopApp.requireBoundedImportBatch(Integer.MAX_VALUE, "Dosya seçimi"));
        assertDoesNotThrow(() -> DesktopApp.requireBoundedImportBatch(
                FileImportPolicy.MAX_CARDS, "Explorer bırakması"));

        File first = new File("first.udf"), second = new File("second.udf");
        List<Path> expected = List.of(first.toPath(), second.toPath());
        assertEquals(expected, DesktopApp.extractDroppedPaths(List.of(first, second)));
        assertEquals(expected,
                DesktopApp.chooserPaths(new File[]{first, second}, first));
        assertEquals(List.of(first.toPath()), DesktopApp.chooserPaths(null, first));
        File[] atCap = new File[FileImportPolicy.MAX_CARDS];
        Arrays.fill(atCap, first);
        assertEquals(FileImportPolicy.MAX_CARDS,
                DesktopApp.chooserPaths(atCap, first).size(),
                "The existing batch planner still checks duplicates atomically");
    }
}
