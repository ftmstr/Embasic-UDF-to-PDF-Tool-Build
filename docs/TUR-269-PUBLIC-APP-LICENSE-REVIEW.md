# TUR269 — Public app/ bağımsız kaynak/lisans/NOTICE beta kapısı (15.11.2026 02:00 TRT)

**Kapsam:** yalnız Public `ftmstr/Embasic-UDF-to-PDF-Tool-Build`, `ci/secure-windows-bootstrap-20261018`. Başlangıç HEAD `65fd3ee4c976979e203c7ce3db29f21fcc60c571` (TUR268). Bu belge yayın onayı değildir. Private kaynaklar veya gerçek UYAP dosyaları kullanılmadı.

## İncelenen somut Public kaynak kanıtları

- `app/pom.xml` blob SHA-1 `3abdb70859b95f5346532c017ed905ea8d20959d`; Java 17, `org.apache.pdfbox:pdfbox:3.0.8`, test için `org.junit.jupiter:junit-jupiter:5.11.4`.
- Paketleme `org.apache.maven.plugins:maven-shade-plugin:3.6.0` ile `all` sınıflandırıcılı shaded artifact üretiyor. Build eklentileri `maven-compiler-plugin:3.13.0`, `maven-surefire-plugin:3.5.2`. Shaded dağıtım için transitive lisans/NOTICE ve paketlenen kaynak/asset denetimi ayrıca gerekir; POM bağımlılık listesi tek başına yeterli değildir.
- İzin verilen daldaki `LICENSE`, `app/LICENSE`, `THIRD-PARTY-NOTICES.txt`, `app/THIRD-PARTY-NOTICES.txt` yollarının doğrudan GitHub içerik sorguları 404 döndürdü. Bu, **yalnız bu dört aday yolun** bulunmadığına kanıttır; başka konumdaki lisans belgelerinin yokluğunu ispatlamaz.
- `scripts/audit-public-app-readiness.py` mevcut: Git HEAD ağacından app blob SHA-256 envanteri çıkarıyor, doğrudan Maven bağımlılıklarını listeliyor, `publication_allowed=false` ve `status=BLOCKED` üretiyor. İncelenen kod, shade eklentilerini ve transitif bağımlılıkları onaylamıyor; bu boşluk ayrıca kapatılmalıdır.
- Legacy `src/main` kaynak kapısı ile bağımsız Public `app/` yayın onayı ayrı kalır. `PUBLIC_SOURCE_APPROVED_SHA` tek başına lisans/dağıtım izni değildir.

## CI kanıtı — başlangıç SHA

TUR268 `65fd3ee4c976979e203c7ce3db29f21fcc60c571` için sorgu anında: Desktop MVP [43650828156](https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43650828156) IN_PROGRESS; PDF Engine [43650828157](https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43650828157) SUCCESS; upstream [43650828172](https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43650828172) SUCCESS; Secure Bootstrap [43650828177](https://github.com/ftmstr/Embasic-UDF-to-PDF-Tool-Build/actions/runs/43650828177) FAILURE. Yeni commitin CI'sı ayrıca doğrulanmadan PASS sayılmaz.

## Beta için kapatılacak kanıt boşlukları

1. Exact commit üzerinde tam `app/` tracked path ve SHA-256 envanterini kapalı yerel raporda üret; kaynak sahipliği ve her asset/font/logo için hak belgesini insan incelemesine sun.
2. Maven shade sonucu dahil **gerçek dağıtım içeriğinin** transitive SBOM, lisans, NOTICE, yeniden dağıtım yükümlülüklerini bağımsız incele. Bulunmayan/incelemesi yapılmayan belgeyi APPROVED sayma.
3. Yetkili, hakları temiz offline golden UDF/PDF karşılaştırmasını `docs/TUR-267-OFFLINE-GOLDEN-BETA-ACCEPTANCE.md` sözleşmesine göre gerçek Windows ortamında uygula; hassas veriyi Public GitHub'a veya CI'ya taşıma.
4. Fiziksel Windows GUI, gerçek yazıcı/PDF ve exFAT/hostile CAS kanıtlarını ayrı kaydet; çalıştırılmayanları PASS sayma.

**Kapılar:** source/license/NOTICE BLOCKED; native UYAP NOT_VERIFIED; fiziksel exFAT NOT_RUN; release NO-GO. Main merge, tag, release, installer, ZIP, dağıtım YOK. İlerleme yüzdesi değişmedi. Beta ve nihai tarihler düşük güvenli, koşullu; eksik hak/Windows/golden kanıtı olmadan takvim taahhüdü yok.
