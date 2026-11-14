# Tur 57: PDFBox default font-cache replay (CI only)

This probe verifies PDFBox default metadata handling **without** configuring a custom
pdfbox.fontcache JVM property. Its four Maven Surefire invocations create four
**separate JVMs** with a shared disposable temporary home, while the Maven
process, operating-system fonts, true user.home and LOCALAPPDATA are not modified.

Run manually (CI-only synthetic data, Java 17, Maven, installed Arial/Segoe
or DejaVu Sans fonts):

    python app/scripts/profile_fontcache_default.py

CI automatically runs this expensive, opt-in proof on BOTH Windows and Linux
only for a Public branch push commit containing **[fontcache-default-4x]**.
This probe is deliberately separate from the existing
**[fontcache-3x]** experiment that explicitly sets pdfbox.fontcache.

The test order is:

1. cold: select the PDFBox default path; create font metadata and synthetic PDF
2. updated: replace **only the temporary font copy** with a different valid
   installed font and assert embedded TrueType bytes differ in a fresh JVM
3. metadata-rebuild: corrupt the temporary default cache, then prove a new JVM
   rebuilds it, embeds the updated valid font, and renders supported text
4. bad-font: corrupt only the temporary font-copy source, then prove no
   existing approved synthetic PDF bytes change and no staging file remains

The script requires concrete JUnit SUCCESS receipts and valid/repaired on-disk
metadata before it prints a single aggregate PASS. It keeps
pdfbox.fontcache.skipchecksums=false. The test class has a nonstandard suffix
(Probe, not Test), so the ordinary Maven suite will not change user.home.

**Security limits:** a successful fallback is **not** proof that PDFBox's
default directory is ACL-hardened. It also does not provide atomic Windows
directory-handle / TOCTOU protection. The Windows Java provider lacks
SecureDirectoryStream in the tested GitHub runners. This is not physical GUI
Robot acceptance, native UYAP-to-PDF pixel parity, redistribution permission,
or an authorized executable release. Preserve NO-GO for these claims.
