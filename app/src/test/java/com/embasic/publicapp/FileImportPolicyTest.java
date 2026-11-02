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
        // GUI import rejects physical aliases on a worker before UdfReader
        // opens the archive; equal contents in separate files remain allowed.
        FileImportPolicy.preflightDistinctImport(original, List.of());
        FileImportPolicy.preflightDistinctImport(separate, List.of(original));
        IllegalArgumentException earlyAlias = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightDistinctImport(hardLink, List.of(original)));
        assertTrue(earlyAlias.getMessage().contains("yinelenen"));
        Path removed = folder.resolve("removed.udf");
        FileImportPolicy.preflightDistinctImport(separate, List.of(removed));
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightDistinctImport(removed, List.of(original)));
        FileImportPolicy.preflightReadableMergeFiles(
                FileImportPolicy.requireCompleteMerge(List.of(original, separate)));
        IllegalArgumentException aliasError = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightReadableMergeFiles(
                        FileImportPolicy.requireCompleteMerge(List.of(original, hardLink))));
        assertTrue(aliasError.getMessage().contains("yinelenen"),
                "A hard-link alias must be rejected before either archive is parsed");
    }
    @Test void guiExportRejectsAliasedOrMissingSourcesBeforePdfStaging(@TempDir Path folder) throws Exception {
        // The GUI accepts paths without blocking the EDT on filesystem identity
        // probes. Its background save must reject hard links before PDFBox runs.
        Path source = Files.writeString(folder.resolve("source.udf"), "public synthetic fixture");
        Path alias = Files.createLink(folder.resolve("alias.udf"), source);
        Path output = folder.resolve("merged.pdf");
        var paragraph = new UdfReader.Paragraph(List.of(
                new UdfReader.Run("public test", new UdfReader.Style(
                        11, false, java.awt.Color.BLACK))));
        var doc = new UdfReader.DocumentSpec("source.udf", "1.0", List.of(paragraph));
        var docs = List.of(doc, doc);
        String fingerprint = FileImportPolicy.sourceSha256(source);
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(source, alias), docs,
                        List.of(fingerprint, fingerprint), output, false));
        assertTrue(duplicate.getMessage().contains("yinelenen"));
        assertFalse(Files.exists(output), "Rejected GUI merge must not stage or publish a PDF");

        Files.delete(alias);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(source, alias), docs,
                        List.of(fingerprint, fingerprint), output, false));
        assertTrue(missing.getMessage().contains("okunabilir"));
        assertFalse(Files.exists(output));

        // A source changed after import must not silently export the stale
        // document cached by the GUI, even if it remains a readable .udf file.
        Files.writeString(source, "changed synthetic fixture");
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> DesktopApp.saveWorkspace(List.of(source), List.of(doc),
                        List.of(fingerprint), output, false));
        assertTrue(stale.getMessage().contains("değiştirildi"));
        assertFalse(Files.exists(output), "A changed source must never stage a PDF");
        // Reimporting the current file permits the fresh snapshot to proceed
        // to normal PDF validation rather than failing the fingerprint gate.
        assertNotEquals(fingerprint, FileImportPolicy.sourceSha256(source));
    }

    @Test void capsHugeFileBatchesWithoutDroppingAcceptedOrder(@TempDir Path folder) throws Exception {
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
        // A sparse, rights-clean synthetic file must be rejected by size
        // metadata before hashing 64 MiB or parsing an oversized ZIP.
        Path oversized = folder.resolve("oversized.udf");
        try (var channel = java.nio.channels.FileChannel.open(oversized,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(FileImportPolicy.MAX_ARCHIVE_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        assertTrue(Files.size(oversized) > FileImportPolicy.MAX_ARCHIVE_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(oversized));
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightReadableMergeFiles(List.of(oversized)));
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightDistinctImport(oversized, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> UdfReader.read(oversized));
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
