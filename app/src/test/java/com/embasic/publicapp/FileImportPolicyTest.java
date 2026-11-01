package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FileImportPolicyTest {
    @Test void retainsOrderAndIgnoresDuplicatesAndOtherExtensions(@TempDir Path folder) throws Exception {
        var plan=FileImportPolicy.plan(List.of(Path.of("previous.udf")),
                List.of(Path.of("next.udf"),Path.of("notes.txt"),
                        Path.of("next.udf"),Path.of("./previous.udf"),
                        Path.of("Üçüncü.UDF")));
        assertEquals(2,plan.accepted().size());
        assertEquals("next.udf",plan.accepted().get(0).getFileName().toString());
        assertEquals("Üçüncü.UDF",plan.accepted().get(1).getFileName().toString());
        assertFalse(plan.limitReached());
        assertEquals(1, plan.unsupportedCount(), "Non-UDF imports must be disclosed");
        assertEquals(2, plan.duplicateCount(), "Existing and repeated UDFs must be disclosed");
        // CLI cannot silently skip entries like the interactive grid does.
        assertThrows(IllegalArgumentException.class, () ->
                FileImportPolicy.requireCompleteMerge(List.of(Path.of("next.udf"),
                        Path.of("notes.txt"))));
        assertThrows(IllegalArgumentException.class, () ->
                FileImportPolicy.requireCompleteMerge(List.of(Path.of("next.udf"),
                        Path.of("./next.udf"))));
        assertEquals(List.of(Path.of("next.udf").toAbsolutePath().normalize(),
                        Path.of("Üçüncü.UDF").toAbsolutePath().normalize()),
                FileImportPolicy.requireCompleteMerge(List.of(Path.of("next.udf"),
                        Path.of("Üçüncü.UDF"))));
        // Two distinct files with identical contents are valid; two paths to
        // one physical file would duplicate pages even though names differ.
        Path original = Files.writeString(folder.resolve("original.udf"), "synthetic");
        Path separate = Files.writeString(folder.resolve("separate.udf"), "synthetic");
        Path hardLink = Files.createLink(folder.resolve("alias.udf"), original);
        FileImportPolicy.preflightReadableMergeFiles(
                FileImportPolicy.requireCompleteMerge(List.of(original, separate)));
        IllegalArgumentException aliasError = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightReadableMergeFiles(
                        FileImportPolicy.requireCompleteMerge(List.of(original, hardLink))));
        assertTrue(aliasError.getMessage().contains("yinelenen"),
                "A hard-link alias must be rejected before either archive is parsed");
    }
    @Test void capsHugeFileBatchesWithoutDroppingAcceptedOrder() {
        List<Path> files=new ArrayList<>();
        for(int i=0;i<2_500;i++)files.add(Path.of("fixture-"+i+".udf"));
        var plan=FileImportPolicy.plan(List.of(),files);
        assertEquals(FileImportPolicy.MAX_CARDS,plan.accepted().size());
        assertTrue(plan.limitReached());
        assertEquals(0, plan.unsupportedCount());
        assertEquals(0, plan.duplicateCount());
        assertEquals("fixture-0.udf",plan.accepted().get(0).getFileName().toString());
        assertEquals("fixture-159.udf",plan.accepted().get(159).getFileName().toString());
        assertThrows(IllegalArgumentException.class, () -> FileImportPolicy.requireCompleteMerge(files),
                "CLI must reject all 2,500 arguments before parsing the first archive");
        var accepted = FileImportPolicy.requireCompleteMerge(
                files.subList(0, FileImportPolicy.MAX_CARDS));
        assertEquals(160, accepted.size());
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightReadableMergeFiles(accepted),
                "A missing input must fail before opening any ZIP");
    }
    @Test void existingLimitAndRepeatedBatchAreSafe() {
        List<Path> existing=new ArrayList<>();
        for(int i=0;i<160;i++)existing.add(Path.of("fixture-"+i+".udf"));
        var plan=FileImportPolicy.plan(existing,List.of(Path.of("fixture-0.udf"),Path.of("new.udf")));
        assertTrue(plan.accepted().isEmpty());
        assertTrue(plan.limitReached());
        assertEquals(1, plan.duplicateCount());
        assertEquals(0, plan.unsupportedCount());
        assertThrows(IllegalArgumentException.class,()->FileImportPolicy.plan(null,List.of()));
        assertThrows(IllegalArgumentException.class, () -> FileImportPolicy.requireCompleteMerge(null));
        assertThrows(IllegalArgumentException.class, () -> FileImportPolicy.requireCompleteMerge(List.of()));
    }
}
