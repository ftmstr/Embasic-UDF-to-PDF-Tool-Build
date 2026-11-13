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
    @Test void rejectsEmptyArchiveBeforeImportHashOrPdfExport(@TempDir Path folder) throws Exception {
        Path empty = Files.createFile(folder.resolve("empty.udf"));
        Path output = folder.resolve("output.pdf");
        IllegalArgumentException importError = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightDistinctImport(empty, List.of()));
        assertTrue(importError.getMessage().contains("Boş UDF"));
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.preflightReadableMergeFiles(List.of(empty)));
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(empty));
        assertThrows(IllegalArgumentException.class, () -> UdfReader.read(empty));
        assertFalse(Files.exists(output), "An empty source must never create a PDF");
    }


    private static Path createLargeSyntheticUdf(Path folder, String name) throws Exception {
        return createLargeSyntheticUdf(folder, name, "S");
    }

    private static Path createLargeSyntheticUdf(Path folder, String name, String fill)
            throws Exception {
        Path source = folder.resolve(name);
        // No original user data: valid ZIP structure containing only generated
        // text. NO_COMPRESSION ensures multiple 64 KiB reads while hashing.
        String xml = "<template format_id='1.0'><content>"
                + fill.repeat(300_000)
                + "</content><elements><paragraph><content startOffset='0' "
                + "length='300000'/></paragraph></elements></template>";
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(source))) {
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION);
            zip.putNextEntry(new java.util.zip.ZipEntry("content.xml"));
            zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(Files.size(source) > 2 * 64 * 1024);
        return source;
    }

    @Test void truncatingOpenStreamAfterFirstHashChunkIsRejected(@TempDir Path folder)
            throws Exception {
        Path source = createLargeSyntheticUdf(folder, "truncate-during-sha.udf");
        String stable = FileImportPolicy.sourceSha256(source);
        assertEquals(64, stable.length());
        long originalLength = Files.size(source);
        int[] boundaries = {0};
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(source, () -> {
                    boundaries[0]++;
                    // This is executed synchronously while the actual hashing
                    // InputStream is OPEN, after its first positive read.
                    try (var writer = java.nio.channels.FileChannel.open(
                            source, java.nio.file.StandardOpenOption.WRITE)) {
                        writer.truncate(0);
                    }
                }));
        assertEquals(1, boundaries[0], "First-chunk callback must run exactly once");
        assertEquals(0, Files.size(source));
        assertTrue(originalLength > 64 * 1024);
        assertTrue(rejected.getMessage().contains("değiştirildi"),
                "A nonempty initial UDF must not hash a partial stream");
        assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(source));
        System.out.println("EMBASIC_TUR241_SHA_TRUNCATE firstChunk=OBSERVED"
                + " stream=OPEN partial=REJECT zeroAfter=REJECT syntheticOnly=true");
    }

    @Test void growingOpenStreamAfterFirstHashChunkIsRejected(@TempDir Path folder)
            throws Exception {
        Path source = createLargeSyntheticUdf(folder, "grow-during-sha.udf");
        String stable = FileImportPolicy.sourceSha256(source);
        long originalLength = Files.size(source);
        int[] boundaries = {0};
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(source, () -> {
                    boundaries[0]++;
                    Files.write(source, new byte[8192],
                            java.nio.file.StandardOpenOption.APPEND);
                }));
        assertEquals(1, boundaries[0]);
        assertEquals(originalLength + 8192, Files.size(source));
        assertTrue(rejected.getMessage().contains("değiştirildi"));
        assertNotEquals(stable, FileImportPolicy.sourceSha256(source));
        System.out.println("EMBASIC_TUR241_SHA_GROW firstChunk=OBSERVED"
                + " stream=OPEN overflow=REJECT syntheticOnly=true");
    }

    @Test void sameSizeSourceRewriteWhileHashStreamOpenIsRejected(@TempDir Path folder)
            throws Exception {
        Path source = createLargeSyntheticUdf(folder, "same-sized-source.udf", "S");
        Path replacement = createLargeSyntheticUdf(folder, "same-sized-replacement.udf", "T");
        byte[] alternate = Files.readAllBytes(replacement);
        long originalSize = Files.size(source);
        assertEquals(originalSize, alternate.length);
        var original = Files.readAttributes(source,
                java.nio.file.attribute.BasicFileAttributes.class);
        String oldSha = FileImportPolicy.sourceSha256(source);
        int[] firstChunk = {0};
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(source, () -> {
                    firstChunk[0]++;
                    // The real source stream is OPEN after hashing 64 KiB;
                    // write a distinct, same-length valid synthetic UDF via
                    // its existing path, then restore metadata to emulate ABA.
                    Files.write(source, alternate,
                            java.nio.file.StandardOpenOption.WRITE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    Files.setLastModifiedTime(source, original.lastModifiedTime());
                    var replaced = Files.readAttributes(source,
                            java.nio.file.attribute.BasicFileAttributes.class);
                    assertEquals(originalSize, replaced.size());
                    assertEquals(original.lastModifiedTime(), replaced.lastModifiedTime());
                    if (original.fileKey() != null && replaced.fileKey() != null)
                        assertEquals(original.fileKey(), replaced.fileKey(),
                                "In-place content rewrite must preserve identity");
                }));
        assertEquals(1, firstChunk[0]);
        assertTrue(rejected.getMessage().contains("değiştirildi"),
                "Equal-size in-place source must not produce a mixed valid hash");
        assertNotEquals(oldSha, FileImportPolicy.sourceSha256(source));
        System.out.println("EMBASIC_TUR242_SAME_SIZE_INFLIGHT"
                + " firstChunk=OBSERVED mtime=RESTORED size=UNCHANGED"
                + " hybrid=REJECT twoPass=ENFORCED");
    }

    @Test void sameBytesNewFileIdentityBetweenHashPassesIsRejected(@TempDir Path folder)
            throws Exception {
        Path source = createLargeSyntheticUdf(folder, "identity-source.udf");
        Path newInode = folder.resolve("independent-copy.udf");
        Files.copy(source, newInode);
        var original = Files.readAttributes(source,
                java.nio.file.attribute.BasicFileAttributes.class);
        assertNotNull(original.fileKey(), "Native Windows/Linux CI must supply file keys");
        String stableHash = FileImportPolicy.sourceSha256(source);
        int[] boundaries = {0};
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> FileImportPolicy.sourceSha256(source, () -> {}, () -> {
                    boundaries[0]++;
                    Files.move(source, folder.resolve("retired-identity.udf"));
                    Files.move(newInode, source);
                    Files.setLastModifiedTime(source, original.lastModifiedTime());
                    var after = Files.readAttributes(source,
                            java.nio.file.attribute.BasicFileAttributes.class);
                    assertEquals(original.size(), after.size());
                    assertEquals(original.lastModifiedTime(), after.lastModifiedTime());
                    assertNotNull(after.fileKey());
                    assertNotEquals(original.fileKey(), after.fileKey(),
                            "The test requires a distinct filesystem object");
                }));
        assertEquals(1, boundaries[0]);
        assertTrue(rejected.getMessage().contains("değiştirildi"));
        assertEquals(stableHash, FileImportPolicy.sourceSha256(source),
                "Only the file identity changes: bytes must remain equal");
        System.out.println("EMBASIC_TUR242_FILE_IDENTITY_SWAP"
                + " passBoundary=OBSERVED sameBytes=TRUE mtime=RESTORED"
                + " fileKey=CHANGED hash=REJECT");
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
