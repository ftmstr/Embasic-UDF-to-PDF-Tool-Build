# TUR267 — Offline golden UDF/PDF kabul sözleşmesi ve beta NO-GO (15.11.2026)

Kapsam: yalnız Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, `ci/secure-windows-bootstrap-20261018`. Başlangıç SHA: `ffcf68beeab84de1b64485a933de552477f1bbe3` (TUR266). Bu belge **gerçek UYAP uyumluluğu kanıtı veya yayın izni değildir**.

## 1. Güven sınırları
- Gerçek UYAP/UDF dosyaları, mahkeme/kişi verileri, yetkili referans PDF'ler, ekran görüntüleri, font/logo asılları, dosya hash'leri ve onay imzaları **Public GitHub'a, CI artefaktına veya loglara konulmaz**.
- İki ayrık kabul hattı: (A) hakları temiz, açıkça sentetik `format_id=1.0` Public altküme; (B) yetkili kapalı/offline ortamda gerçek UYAP golden. A'nın PASS olması B'ye ilişkin hiçbir eşdeğerlik iddiası oluşturmaz.
- Desteklenmeyen şema, referans veya font için sessiz kayıp, otomatik tahmin, fallback ile başarılı PDF kabulü **yasak**; fail-closed ve red nedeni kaydedilir.
- Legacy kök `src/main` kaynak kapısı ile bağımsız `app/` source/asset/license/NOTICE onayı ayrıdır. `PUBLIC_SOURCE_APPROVED_SHA` tek başına izin değildir.

## 2. Kapalı golden test seti (hazırlık; NOT_RUN)
Yetkili veri sorumlusu her senaryoya gizli yerel ID verir; hak/izin ve saklama süresini kayıt altına alır. Set en az şu sınıfları kapsar: düz metin/Unicode Türkçe, sayfa kırılması, çok sayfalı belge, satır-sütun birleşimli tablo, uzun hücre/taşma, gömülü ve harici görsel referansı, font substitution, kenar boşlukları ve geometri, bozuk/truncated UDF, bilinmeyen XML öğesi, büyük dosya ve iki dosya birleştirme. İzin yoksa senaryo NOT_RUN kalır.

Her golden için offline operatör, kaynak UDF ve yetkili referans PDF'yi ayrı salt-okunur klasörlerden açar; çıktı ayrı geçici alana yazılır. Girdi değişmezliği SHA-256 ile **yalnız yerel kayıtta** doğrulanır. PDF başlık/EOF yeterli değildir: sayfa sayısı, sayfa boyutu, metin çıkarımı ve okuma sırası, Unicode glif varlığı, tablo hücre koordinatları, görsel varlığı ve çözünürlüğü, font gömme/lisans durumu, görsel karşılaştırma toleransları ayrıca değerlendirilir. Referansın kendisindeki olası hatalar manuel inceleme gerektirir.

## 3. Karar ve kanıt şeması (yerel, dışarı aktarılmaz)
`case_id, authorized_source, schema_version, permission_record, input_sha256, reference_pdf_sha256, output_sha256, engine_commit, app_commit, OS_build, java_version, page_count_expected/actual, page_geometry, text_unicode, table_cells, images, fonts, pixel_diff_method/threshold, reviewer, result(PASS|FAIL|NOT_RUN|BLOCKED), reason, timestamp`.

Her ölçüt önceden onaylı eşik veya manuel çift inceleme gerektirir. Tek bir kritik FAIL/UNKNOWN, golden eşdeğerlik GO kararını engeller. Kişisel veri içermeyen **yalnız toplulaştırılmış** sonuçlar dış rapora aktarılabilir; önce ayrı paylaşım incelemesi gerekir.

## 4. Public beta teslim kapısı
1. Exact SHA'ya bağlı tüm `app/` tracked path envanteri, kaynak kökeni, sahiplik/yeniden dağıtım yetkisi, üçüncü taraf font/görsel ve NOTICE belgeleri bağımsız incelenir; bilinmeyen yol BLOCKED.
2. Aynı SHA'da Windows/Linux Maven, normal Windows jpackage app-image, fiziksel Robot ve PDF geometri/font kabulü PASS; başarısız CI günlükleri incelenir.
3. Gerçek UYAP golden offline yetkili ortamda PASS; hakları temiz sentetik altküme sonucu bunun yerine geçmez.
4. Fiziksel exFAT/unsupported hardlink ve hostile mevcut hedef PDF CAS kabulü; hosted VHD yoksa NOT_RUN.
5. Kaynak, lisans, sürüm ve dağıtım için ayrı insan onayı; main merge/tag/release/ZIP/installer otomatik yapılmaz.

## 5. TUR267 başlangıç kanıtı
TUR266 `ffcf68beeab84de1b64485a933de552477f1bbe3`: Desktop MVP run 43645240613 SUCCESS; PDF Engine 43645240610 SUCCESS; upstream 43645240549 SUCCESS; Secure Bootstrap 43645240551 FAILURE. Bunlar yalnız önceki SHA'nın iş akışı sonuçlarıdır. Golden UYAP NOT_VERIFIED; fiziksel exFAT NOT_RUN. İlerleme yüzdesi değiştirilmez.

Sonraki iş: Public `app/` tam tracked source/license/NOTICE envanterini SHA'ya bağlamak; offline golden ortamında yetkili yürütme yapılmadıkça gerçek UYAP GO iddia etmemek. Yayın kararı: **NO-GO**.
