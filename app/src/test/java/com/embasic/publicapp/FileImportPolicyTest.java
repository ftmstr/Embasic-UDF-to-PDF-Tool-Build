package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FileImportPolicyTest {
    @Test void retainsOrderAndIgnoresDuplicatesAndOtherExtensions() {
        var plan=FileImportPolicy.plan(List.of(Path.of("previous.udf")),
                List.of(Path.of("next.udf"),Path.of("notes.txt"),
                        Path.of("next.udf"),Path.of("./previous.udf"),
                        Path.of("Üçüncü.UDF")));
        assertEquals(2,plan.accepted().size());
        assertEquals("next.udf",plan.accepted().get(0).getFileName().toString());
        assertEquals("Üçüncü.UDF",plan.accepted().get(1).getFileName().toString());
        assertFalse(plan.limitReached());
    }
    @Test void capsHugeFileBatchesWithoutDroppingAcceptedOrder() {
        List<Path> files=new ArrayList<>();
        for(int i=0;i<2_500;i++)files.add(Path.of("fixture-"+i+".udf"));
        var plan=FileImportPolicy.plan(List.of(),files);
        assertEquals(FileImportPolicy.MAX_CARDS,plan.accepted().size());
        assertTrue(plan.limitReached());
        assertEquals("fixture-0.udf",plan.accepted().get(0).getFileName().toString());
        assertEquals("fixture-159.udf",plan.accepted().get(159).getFileName().toString());
    }
    @Test void existingLimitAndRepeatedBatchAreSafe() {
        List<Path> existing=new ArrayList<>();
        for(int i=0;i<160;i++)existing.add(Path.of("fixture-"+i+".udf"));
        var plan=FileImportPolicy.plan(existing,List.of(Path.of("fixture-0.udf"),Path.of("new.udf")));
        assertTrue(plan.accepted().isEmpty());
        assertTrue(plan.limitReached());
        assertThrows(IllegalArgumentException.class,()->FileImportPolicy.plan(null,List.of()));
    }
}
