# TUR266 — Nihai sürüme geçişte kaynak kapısı ayrımı (15.11.2026)

Repo: Public ftmstr/Embasic-UDF-to-PDF-Tool-Build
İzinli dal: ci/secure-windows-bootstrap-20261018
Başlangıç exact HEAD: b302d6d512690efa10c8d87771bef750e7c81217 (TUR265).

## Doğrulanmış P0 kök neden
scripts/public-ci-gate.py eski kök src/main/ ve kök pom.xml için hazırlanmış REQUIRED envanterini onaylıyor. Yeni bağımsız Java 17 / Swing / PDFBox uygulaması app/ altında. Bu nedenle yalnız PUBLIC_SOURCE_APPROVED_SHA ayarlamak yeni uygulamayı onaylamaz; daha önceki unapproved_bootstrap_file reddi gerçek bir güvenlik sınırıdır. TUR265 Secure Bootstrap job 132585926937 bu ret nedeniyle FAILURE olarak kapanmıştır.

## Değişiklikler
- scripts/public-ci-gate.py: Public app/ modülü görüldüğünde independent_public_mvp_source_review_required hata kodunu üretir; yürürlükteki eski kaynak, bootstrap ve SHA onay şartlarını gevşetmez.
- scripts/test-public-ci-gate.py: iki mevcut regresyonun beklenen açık ret nedenini günceller ve eşleşen SHA'nın bile Public-only uygulamaya izin vermediğini sınar.
- app/scripts/Show-EmbasicProgress.ps1: bağımsız kaynak inceleme, gerçek UYAP golden, tüm sürümler, hostile CAS, exFAT ve dış beta durumlarını PublicationAllowed=false altında listeler; 44,3% ağırlıklı mühendislik plan tahminidir, gerçek kullanıcı final olgunluğu değildir.
- .github/workflows/public-mvp-ci.yml: dashboard ReleaseReadiness ret/NOT_RUN durumları eksik veya yanlış gösterilirse CI fail-closed olur.
- docs/TUR-266-FINAL-READINESS-GATES.md: teknik kayıt, izlenebilir kaynak ve doğrulama sınırları.

## TUR265 baseline kesin sonuçları (14.11.2026)
- Public MVP Windows/Linux: run 43640216959 SUCCESS; her iki OS Maven 209 JUnit (208 PASS, 1 normal Robot SKIP). Sentetik 160 UDF Windows 5,25 s / Linux 1,60 s (512 MiB JVM heap sınırı); kullanıcıya açık performans vaadi değil.
- Gerçek paketli Windows app-image: run 43640216905 SUCCESS; fiziksel GUI Robot, Ctrl+S PDF, tema, zoom, sıra, onaylı PDF SHA; ayrı test-only JDWP/JDI render-race PASS.
- Aynı jpackage logunda PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN reason=hosted_vhd_environment_unavailable_stage_mount_disposable_vhd_exception_CimException. Host diski biçimlendirilmedi.
- PDF Engine 43640216963 SUCCESS; upstream SHA/lisans denetimi 43640216919 SUCCESS.
- Secure Bootstrap 43640216934 FAILURE; SOURCE_GATE: BLOCKED (unapproved_bootstrap_file), PUBLIC_SOURCE_APPROVED_SHA boş. Bu başarısızlık release kapısının çalışmasıdır; dağıtım izni değildir.

## TUR260 exact SHA kontrolü
e19258033e1adfac3f7e2c4ef7ca73703d5e07b3 için MVP 43617954850 Windows/Linux SUCCESS ve normal jpackage 43617954838 SUCCESS; PDF Engine 43617954788 ile upstream 43617954748 CANCELLED; secure 43617954794 kaynak onayını tamamlamadı. TUR260 exFAT NOT_RUN hiçbir zaman PASS olarak yorumlanmadı.

## Nihai sürümün koşulları
1. app/ için ayrı ve incelenmiş source/asset/license/NOTICE onay mimarisi; yalnız tek SHA değişkeni yeterli değildir. İçerik hakkı ve byte imzaları ayrı doğrulanmalı.
2. Gerçek native UYAP ve yetkili golden PDF eşleşmesi yalnız kapalı/offline ortamda, hiçbir gerçek dosyayı Public GitHub'a taşımadan ispatlanmalı. Şimdiki UdfReader yalnız sentetik format_id=1.0 alt kümesini kabul ediyor.
3. Gerçek Windows mevcut onaylı target hostile CAS ve desteklemeyen dosya sistemi fiziksel kabulü; VHD erişim yoksa NOT_RUN korunur.
4. Hakları temiz beta kabulü, third-party font/logo hakları, son paket SHA ve imzalı/izinli yayın prosedürü.

Yayın: NO-GO. Public branch dışına yazma, Private erişim, main merge, tag, release, installer, ZIP, golden dosya aktarımı veya kaynak kapısını bypass etme YOK. Yeni SHA CI kapanmadan TUR266 PASS olarak raporlanmaz.
