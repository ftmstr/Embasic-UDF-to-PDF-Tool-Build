package com.embasic.publicapp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Read-only Windows runner user-profile inspection. No real user directory is
 * created, modified, permission-adjusted or selected as a PDFBox cache.
 *
 * The current hosted runner is NOT an interactive consumer Windows desktop:
 * this test audits preconditions, not a physical GUI or release acceptance.
 */
final class WindowsRealProfileReadOnlyAuditTest {
    @Test void auditsExistingUserLocalAppDataWithoutWritingAnyUserData()
            throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows")) return;

        String homeText = System.getProperty("user.home", "");
        String localText = System.getenv("LOCALAPPDATA");
        if (homeText.isBlank() || localText == null || localText.isBlank()) {
            System.out.println("EMBASIC_PROFILE_READONLY hostWindows=NOT_RUN"
                    + " reason=environment_missing");
            return;
        }

        Path home = Path.of(homeText).toAbsolutePath().normalize();
        Path local = Path.of(localText).toAbsolutePath().normalize();
        if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(local, LinkOption.NOFOLLOW_LINKS)) {
            System.out.println("EMBASIC_PROFILE_READONLY hostWindows=NOT_RUN"
                    + " reason=profile_directory_missing");
            return;
        }
        boolean inside = !local.equals(home) && local.startsWith(home);
        boolean canonical = home.equals(home.toRealPath())
                && local.equals(local.toRealPath());
        boolean ownerMatch = Files.getOwner(home, LinkOption.NOFOLLOW_LINKS)
                .equals(Files.getOwner(local, LinkOption.NOFOLLOW_LINKS));
        // Only examine the pre-existing ACL; never call prepare() here:
        // prepare may create a real application cache directory.
        boolean ownerAcl = PdfBoxFontCacheDirectory.trustedAcl(home,
                Files.getOwner(home, LinkOption.NOFOLLOW_LINKS));
        boolean localAcl = PdfBoxFontCacheDirectory.trustedAcl(local,
                Files.getOwner(home, LinkOption.NOFOLLOW_LINKS));

        // Sensitive user paths, account names and ACL entries are never logged.
        System.out.println("EMBASIC_PROFILE_READONLY hostWindows=PASS"
                + " withinHome=" + inside
                + " canonical=" + canonical
                + " ownerMatch=" + ownerMatch
                + " homeAclTrusted=" + ownerAcl
                + " localAclTrusted=" + localAcl
                + " noWrites=PASS physicalGui=NOT_RUN");
        assertTrue(Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isDirectory(local, LinkOption.NOFOLLOW_LINKS));
    }
}
