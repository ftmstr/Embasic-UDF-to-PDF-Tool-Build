package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Safeguards selecting the native Windows PDFBox font metadata cache path.
 * These tests never delete, trust, or modify PDFBox checksums themselves.
 * They are not a claim of cold-start acceleration on a physical Windows PC.
 */
final class PdfBoxFontCacheDirectoryTest {
    @TempDir Path sandbox;

    private Path home() throws Exception {
        return Files.createDirectories(sandbox.resolve("profile"));
    }

    private Path local(Path home) throws Exception {
        return Files.createDirectories(home.resolve("AppData").resolve("Local"));
    }

    @Test void cachesInStableWritableUserProfileAndNeverModifiesExistingMetadata()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Optional<Path> selected = PdfBoxFontCacheDirectory.prepare(home, local);
        Path preferred = local.resolve("Embasic-UDF-to-PDF");
        // GitHub-hosted Windows temp folders can inherit an untrusted
        // write-capable ACL. Rejection is then correct, not a CI failure.
        if (selected.isPresent()) {
            assertEquals(Optional.of(preferred), selected);
        } else {
            assertTrue(System.getProperty("os.name", "").toLowerCase(
                    java.util.Locale.ROOT).startsWith("windows"));
            Files.createDirectories(preferred);
        }
        assertTrue(Files.isDirectory(preferred));
        // Corrupt metadata must be revalidated by PDFBox at render time.
        // Merely selecting a directory must neither silently bless its
        // contents nor erase the file to conceal an invalid cache.
        Path metadata = Files.writeString(preferred.resolve(".pdfbox.cache"),
                "SYNTHETIC INVALID CACHE DATA", StandardCharsets.UTF_8);
        byte[] invalid = Files.readAllBytes(metadata);
        assertEquals(selected,
                PdfBoxFontCacheDirectory.prepare(home, local));
        assertArrayEquals(invalid, Files.readAllBytes(metadata),
                "Application must not rewrite or 'repair' PDFBox cache bytes");
    }

    @Test void rejectsDirectoriesOutsideProfileOrNonDirectoryOrMissingHome()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Optional<Path> initial = PdfBoxFontCacheDirectory.prepare(home, local);
        if (initial.isEmpty()) {
            assertTrue(System.getProperty("os.name", "").toLowerCase(
                    java.util.Locale.ROOT).startsWith("windows"),
                    "Linux fixture with normal ownership should be eligible");
            System.out.println("EMBASIC_FONT_CACHE_ACL inheritedWindows=REJECTED");
        }
        Path outside = Files.createDirectory(sandbox.resolve("shared"));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, outside),
                "Never store font metadata in an arbitrary shared directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(null, local));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, null));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                sandbox.resolve("not-present"), local));
        Path file = Files.writeString(home.resolve("wrong-kind"), "not-directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, file),
                "A file is not a writable directory, regardless of its extension");
    }

    @Test void rejectsExistingCacheAliasAndDirectoryInsteadOfOverwriting()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Optional<Path> initial = PdfBoxFontCacheDirectory.prepare(home, local);
        Path preferred = Files.createDirectories(local.resolve("Embasic-UDF-to-PDF"));
        Path metadata = Files.createDirectory(preferred.resolve(".pdfbox.cache"));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
        assertTrue(Files.isDirectory(metadata), "Failed selection cannot mutate user data");
        Files.delete(metadata);

        Path real = Files.writeString(preferred.resolve("backing.txt"), "KEEP ORIGINAL");
        boolean linked = false;
        try {
            Files.createSymbolicLink(metadata, real.getFileName());
            linked = true;
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
            assertEquals("KEEP ORIGINAL", Files.readString(real));
            assertTrue(Files.isSymbolicLink(metadata));
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException
                | SecurityException notPermittedOnWindows) {
            // Standard hosted Windows accounts may not have symlink privilege.
        } finally {
            if (linked) Files.delete(metadata);
        }
        assertEquals(initial,
                PdfBoxFontCacheDirectory.prepare(home, local));
    }

    @Test void rejectsSymlinkedApplicationDirectoryAndFallsBackWithoutTouchingTarget()
            throws Exception {
        Path home = home();
        Path local = local(home);
        Path external = Files.createDirectory(local.resolve("external-store"));
        Path app = local.resolve("Embasic-UDF-to-PDF");
        boolean linked = false;
        try {
            Files.createSymbolicLink(app, external.getFileName());
            linked = true;
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
            assertTrue(Files.isDirectory(external));
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException
                | SecurityException notPermittedOnWindows) {
            // Symlink permission varies on Windows runners.
        } finally {
            if (linked) Files.delete(app);
        }
        assertFalse(Files.exists(app));
    }

    @Test void rejectsIntermediateProfileSymlinkWithoutWritingOutsideProfile()
            throws Exception {
        Path home = home();
        Path external = Files.createDirectories(sandbox.resolve("external").resolve("Local"));
        Path alias = home.resolve("AppData");
        boolean linked = false;
        try {
            Files.createSymbolicLink(alias, external.getParent());
            linked = true;
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, alias.resolve("Local")),
                    "Intermediate symlink must not redirect the PDFBox cache");
            assertFalse(Files.exists(external.resolve("Embasic-UDF-to-PDF")));
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException
                | SecurityException notPermittedOnWindows) {
            // Windows runner may not permit creating symbolic links.
        } finally {
            if (linked) Files.delete(alias);
        }
    }

    @Test void emptyRelativeOrHomeRootMustNeverBeUsedAsWindowsCache()
            throws Exception {
        Path home = home();
        Path local = local(home);
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                Path.of(""), local),
                "A missing user.home must not resolve to the process directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                home, Path.of("")),
                "A missing LOCALAPPDATA must never become the working directory");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                home, home),
                "The cache must be a dedicated subdirectory, not the profile root");
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                Path.of("relative-home"), local));
        assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                home, Path.of("relative-local")));
    }

    @Test void syntheticUntrustedWritePermissionsAreRejectedRegardlessOfOwner() {
        String owner = "LAPTOP\\ExampleUser";
        for (String principal : java.util.List.of(
                "Everyone", "NT AUTHORITY\\Authenticated Users",
                "BUILTIN\\Users", "OTHER\\Guest", "LOCAL\\SomeoneElse")) {
            assertFalse(PdfBoxFontCacheDirectory.trustedWriter(principal, owner),
                    "Reject untrusted Windows writer: " + principal);
        }
        for (String trusted : java.util.List.of(
                owner, "nt authority\\system", "BUILTIN\\Administrators",
                "CREATOR OWNER")) {
            assertTrue(PdfBoxFontCacheDirectory.trustedWriter(trusted, owner));
        }
        assertFalse(PdfBoxFontCacheDirectory.trustedWriter(null, owner));
        assertFalse(PdfBoxFontCacheDirectory.trustedWriter(owner, null));
        assertFalse(PdfBoxFontCacheDirectory.grantsMutation(
                java.util.Set.of(java.nio.file.attribute.AclEntryPermission.READ_DATA,
                        java.nio.file.attribute.AclEntryPermission.READ_ACL)));
        for (var mutation : java.util.List.of(
                java.nio.file.attribute.AclEntryPermission.WRITE_DATA,
                java.nio.file.attribute.AclEntryPermission.APPEND_DATA,
                java.nio.file.attribute.AclEntryPermission.WRITE_ACL,
                java.nio.file.attribute.AclEntryPermission.WRITE_OWNER,
                java.nio.file.attribute.AclEntryPermission.DELETE_CHILD)) {
            assertTrue(PdfBoxFontCacheDirectory.grantsMutation(
                    java.util.Set.of(mutation)),
                    "Must reject write-capable ACE " + mutation);
        }
    }

    @Test void windowsNativeAclViewIsReadWithoutChangingProfilePermissions()
            throws Exception {
        if (!System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
            return; // This is Windows-only evidence, not physical GUI acceptance.
        Path home = home();
        Path local = local(home);
        var view = Files.getFileAttributeView(local,
                java.nio.file.attribute.AclFileAttributeView.class);
        assertNotNull(view, "Windows NTFS ACL view must exist to select a trusted cache");
        var owner = Files.getOwner(home);
        assertNotNull(owner);
        var entries = view.getAcl();
        assertFalse(entries.isEmpty());
        boolean trusted = PdfBoxFontCacheDirectory.trustedAcl(local, owner);
        // If inherited runner permissions allow another account to write,
        // refusing to use the cache is the correct fail-closed behavior.
        if (!trusted) {
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local));
        } else {
            assertTrue(PdfBoxFontCacheDirectory.prepare(home, local).isPresent(),
                    "A trusted local test profile should be eligible");
        }
        System.out.println("EMBASIC_FONT_CACHE_ACL nativeWindows=PASS"
                + " trusted=" + trusted + " metadataAltered=false");
    }

    @Test void actualWindowsNtfsJunctionDoesNotRedirectFontCacheOutsideProfile()
            throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(
                java.util.Locale.ROOT).startsWith("windows"))
            return;
        Path home = home();
        Path target = Files.createDirectories(
                sandbox.resolve("junction-target").resolve("Local"));
        Path junction = home.resolve("AppData");
        // cmd.exe /d suppresses user AutoRun; all paths come from JUnit's
        // disposable temp root, never any user's existing junction.
        Process create = new ProcessBuilder("cmd.exe", "/d", "/c",
                "mklink /J \"" + junction + "\" \"" + target.getParent() + "\"")
                .redirectErrorStream(true).start();
        assertTrue(create.waitFor(12, java.util.concurrent.TimeUnit.SECONDS),
                "Junction setup should not hang a CI worker");
        if (create.exitValue() != 0) {
            System.out.println("EMBASIC_FONT_CACHE_JUNCTION nativeWindows=NOT_RUN"
                    + " reason=runner_permission");
            return;
        }
        try {
            assertTrue(Files.isDirectory(junction));
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(
                    home, junction.resolve("Local")),
                    "A Windows junction must not redirect cache outside the private home");
            assertFalse(Files.exists(target.resolve("Embasic-UDF-to-PDF")),
                    "No cache bytes may be written into the junction target");
            System.out.println("EMBASIC_FONT_CACHE_JUNCTION nativeWindows=PASS"
                    + " externalCacheCreated=false");
        } finally {
            Process remove = new ProcessBuilder("cmd.exe", "/d", "/c",
                    "rmdir \"" + junction + "\"")
                    .redirectErrorStream(true).start();
            assertTrue(remove.waitFor(12, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, remove.exitValue(), "Remove only the disposable junction");
            assertTrue(Files.isDirectory(target),
                    "Junction cleanup must preserve its backing directory");
        }
    }

    private static int icacls(Path path, String... operations) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("icacls.exe");
        command.add(path.toString());
        command.addAll(java.util.List.of(operations));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // The test never logs account names, file paths or ACL output.
        try (var input = process.getInputStream()) {
            input.readAllBytes();
        }
        if (!process.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Disposable ACL probe timed out");
        }
        return process.exitValue();
    }

    @Test void isolatedWindowsOwnerOnlyAclAcceptedThenUntrustedWriteGrantRejected()
            throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(
                java.util.Locale.ROOT).startsWith("windows"))
            return;
        // Exclusively JUnit's ephemeral test profile: NEVER alter the actual
        // USERPROFILE, LOCALAPPDATA, system fonts, or production cache.
        // A hosted runner's @TempDir alias can traverse a junction outside
        // this test profile. Start at the verified canonical backing directory
        // so the production real-path gate can be tested fairly.
        Path home = Files.createDirectories(
                sandbox.toRealPath().resolve("isolated-positive-profile"));
        Path appData = Files.createDirectories(home.resolve("AppData"));
        Path local = Files.createDirectories(appData.resolve("Local"));
        if (!home.equals(home.toRealPath())
                || !local.equals(local.toRealPath())) {
            System.out.println("EMBASIC_FONT_CACHE_ACL_POSITIVE nativeWindows=NOT_RUN"
                    + " reason=noncanonical_disposable_root");
            return;
        }
        var owner = Files.getOwner(home);
        for (Path directory : java.util.List.of(home, appData, local)) {
            // icacls /inheritance:r removes inherited ACEs only; /grant:r
            // replaces the named user's ACE only. Other explicit writers
            // can remain in a disposable temp folder. First make the ACL
            // independent of its parent, then replace the *entire* DACL.
            // Never modify actual Windows LOCALAPPDATA or USERPROFILE.
            if (icacls(directory, "/inheritance:d") != 0) {
                System.out.println("EMBASIC_FONT_CACHE_ACL_POSITIVE nativeWindows=NOT_RUN"
                        + " reason=disposable_acl_privilege");
                return;
            }
            var view = Files.getFileAttributeView(directory,
                    java.nio.file.attribute.AclFileAttributeView.class);
            assertNotNull(view);
            var onlyOwner = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(java.util.EnumSet.allOf(
                            java.nio.file.attribute.AclEntryPermission.class))
                    .setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT,
                            java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT)
                    .build();
            view.setAcl(java.util.List.of(onlyOwner));
        }
        Optional<Path> accepted = PdfBoxFontCacheDirectory.prepare(home, local);
        if (accepted.isEmpty()) {
            // Sanitized diagnostics: boolean checks only; no profile paths,
            // account names or actual ACL entries are written to CI logs.
            Path cacheCandidate = local.resolve("Embasic-UDF-to-PDF");
            var expectedOwner = Files.getOwner(home);
            System.out.println("EMBASIC_FONT_CACHE_ACL_DIAG homeReal="
                    + home.equals(home.toRealPath())
                    + " localReal=" + local.equals(local.toRealPath())
                    + " sameOwner=" + expectedOwner.equals(Files.getOwner(local))
                    + " homeAcl=" + PdfBoxFontCacheDirectory.trustedAcl(home, expectedOwner)
                    + " localAcl=" + PdfBoxFontCacheDirectory.trustedAcl(local, expectedOwner)
                    + " appExists=" + Files.exists(cacheCandidate)
                    + " appAcl=" + (Files.exists(cacheCandidate)
                        && PdfBoxFontCacheDirectory.trustedAcl(cacheCandidate, expectedOwner)));
        }
        assertTrue(accepted.isPresent(),
                "Only the current owner with full control must be accepted");
        Path app = accepted.orElseThrow();
        assertTrue(PdfBoxFontCacheDirectory.trustedAcl(app, Files.getOwner(home)));

        // A cache file is a separate attack surface: its DACL may change
        // between JVM launches, independently of its parent directory.
        // Use only a disposable synthetic metadata file; never a real cache.
        Path metadata = Files.writeString(app.resolve(".pdfbox.cache"),
                "SYNTHETIC-CACHE-BYTES", StandardCharsets.UTF_8);
        byte[] originalBytes = Files.readAllBytes(metadata);
        assertEquals(Optional.of(app), PdfBoxFontCacheDirectory.prepare(home, local));
        try {
            assertEquals(0, icacls(metadata, "/grant:r", "*S-1-5-11:M"));
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local),
                    "A cache file that gains untrusted write rights must be rejected");
            assertArrayEquals(originalBytes, Files.readAllBytes(metadata),
                    "The trust checker may not erase or repair cache bytes");
        } finally {
            assertEquals(0, icacls(metadata, "/remove:g", "*S-1-5-11"));
        }
        assertEquals(Optional.of(app), PdfBoxFontCacheDirectory.prepare(home, local),
                "Restoring the disposable metadata ACL must restore acceptance");
        assertArrayEquals(originalBytes, Files.readAllBytes(metadata));
        System.out.println("EMBASIC_FONT_CACHE_METADATA_ACL"
                + " original=ACCEPTED otherWriter=REJECTED recovered=PASS"
                + " bytesUnchanged=PASS");
        Files.delete(metadata);
        try {
            int injected = icacls(app, "/grant:r", "*S-1-5-11:(OI)(CI)M");
            assertEquals(0, injected,
                    "Disposable folder must accept a controlled Authenticated Users ACE");
            assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local),
                    "Other users' modification rights must revoke cache acceptance");
            assertTrue(Files.isDirectory(app));
            System.out.println("EMBASIC_FONT_CACHE_ACL_POSITIVE nativeWindows=PASS"
                    + " ownerOnly=ACCEPTED injectedOtherWriter=REJECTED");
        } finally {
            // Restore the isolated test folder; never touch real system ACL.
            icacls(app, "/remove:g", "*S-1-5-11");
        }

        // Deterministic post-selection path race: after one validated
        // prepare(), replace only the disposable app folder with a real NTFS
        // junction to an external disposable directory, then revalidate.
        // A previously returned Path cannot be made an atomic capability by
        // this test. Detection at a NEW prepare is all this proves.
        Path backup = local.resolve("Embasic-UDF-to-PDF-safe-backup");
        Path external = Files.createDirectories(
                sandbox.toRealPath().resolve("post-selection-external"));
        Files.move(app, backup);
        boolean linked = false;
        try {
            Process create = new ProcessBuilder("cmd.exe", "/d", "/c",
                    "mklink /J \"" + app + "\" \"" + external + "\"")
                    .redirectErrorStream(true).start();
            try (var output = create.getInputStream()) { output.readAllBytes(); }
            assertTrue(create.waitFor(12, java.util.concurrent.TimeUnit.SECONDS));
            if (create.exitValue() == 0) {
                linked = true;
                assertEquals(Optional.empty(), PdfBoxFontCacheDirectory.prepare(home, local),
                        "A changed directory alias must be rejected at the next check");
                assertFalse(Files.exists(external.resolve(".pdfbox.cache")),
                        "Revalidation may not write any metadata to redirected folder");
                System.out.println("EMBASIC_FONT_CACHE_POST_SELECT_JUNCTION"
                        + " previouslyAccepted=true nextPrepare=REJECTED"
                        + " outsideWrites=0 atomicGuarantee=NOT_PROVEN");
            } else {
                System.out.println("EMBASIC_FONT_CACHE_POST_SELECT_JUNCTION"
                        + " nativeWindows=NOT_RUN reason=runner_permission");
            }
        } finally {
            if (linked) {
                Process remove = new ProcessBuilder("cmd.exe", "/d", "/c",
                        "rmdir \"" + app + "\"")
                        .redirectErrorStream(true).start();
                try (var output = remove.getInputStream()) { output.readAllBytes(); }
                assertTrue(remove.waitFor(12, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0, remove.exitValue());
            }
            Files.move(backup, app);
        }
        assertEquals(Optional.of(app), PdfBoxFontCacheDirectory.prepare(home, local),
                "Restoring original app directory must regain trust");
        assertTrue(Files.isDirectory(external));
    }

    @Test void explicitPdfBoxPropertyIsNotOverwrittenByAutomaticSelection()
            throws Exception {
        String original = System.getProperty("pdfbox.fontcache");
        String requested = sandbox.resolve("operator-managed").toString();
        try {
            System.setProperty("pdfbox.fontcache", requested);
            PdfBoxFontCacheDirectory.configureForDesktop();
            assertEquals(requested, System.getProperty("pdfbox.fontcache"),
                    "Existing operator choice is not replaced unexpectedly");
        } finally {
            if (original == null) System.clearProperty("pdfbox.fontcache");
            else System.setProperty("pdfbox.fontcache", original);
        }
    }
}
