#requires -Version 7.0
<#
.SYNOPSIS
  EMBASIC UDF to PDF Public — renkli, kanit kaynakli proje panosu.
.DESCRIPTION
  Agirliklar bir planlama varsayimidir; yuzdeler gerçek UYAP uyumlulugunu
  veya resmî final yayin iznini belgelemez. Kaynak: Public-only depo.
.EXAMPLE
  pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1
.EXAMPLE
  pwsh -NoProfile -File app/scripts/Show-EmbasicProgress.ps1 -AsJson -Offline
#>
[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$AsJson,
    [string]$Repository = 'ftmstr/Embasic-UDF-to-PDF-Tool-Build',
    [string]$Branch = 'ci/secure-windows-bootstrap-20261018'
)
$ErrorActionPreference = 'Stop'
$today = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(3))
$reviewDate = '2026-11-13'
# Do not drift conditional release dates forward just because an hourly tour ran.
$forecastBase = [DateTime]::ParseExact('2026-10-31', 'yyyy-MM-dd', [System.Globalization.CultureInfo]::InvariantCulture)
# Weight totals are exactly 100. Completion is engineering judgement, not telemetry.
$milestones = @(
    [pscustomobject]@{ Name='Public kod tabani / Maven'; Weight=12; Complete=100; Evidence='Public kaynak + Linux/Windows Maven CI' }
    [pscustomobject]@{ Name='Windows launcher / smoke'; Weight=12; Complete=100; Evidence='Tur244 tested SHA 58720294 Windows jpackage run 43501024616 SUCCESS: ordinary packaged GUI Robot PASS and separate isolated loopback JDWP physical render-breakpoint race PASS; original PDF SHA/staging preserved; final dashboard SHA CI PENDING' }
    [pscustomobject]@{ Name='Masaustu UI / kullanim'; Weight=14; Complete=73; Evidence='Tur216 physical packaged Ctrl+S save, BRAVO-TWO then ALPHA-ONE A4 order, overwrite NO SHA preservation; Tur217 stale source YES error dialog and old PDF preserved; real user beta NOT_RUN' }
    [pscustomobject]@{ Name='UDF turleri ve surumleri'; Weight=26; Complete=12; Evidence='Sentetik ZIP PNG referans, A5/LEGAL; gercek UYAP schema dogrulanmadi' }
    [pscustomobject]@{ Name='PDF gorsel sadakati'; Weight=18; Complete=15; Evidence='Tur243 209 JUnit Windows/Linux each 208 PASS 1 normal Robot SKIP, 160 rights-clean UDF profile; Tur244 packaged real render-race source mutation/error modal PASS on tested SHA 58720294; native UYAP golden NOT_RUN' }
    [pscustomobject]@{ Name='Guvenlik / lisans / yayin'; Weight=10; Complete=35; Evidence='Tur244 physical packaged test-only JDWP SUSPEND_EVENT_THREAD real PdfExporter.save stack and UDF mutation PASS on tested SHA 58720294, approved PDF SHA unchanged/staging clean; production image unchanged, unsupported-hardlink physical NOT_RUN, existing-target hostile CAS/license/NOTICE/source gate NO-GO' }
    [pscustomobject]@{ Name='Beta kabul / dagitim'; Weight=8; Complete=10; Evidence='Paket CI ici; indirme paketi yayinlanmadi' }
)
if ((($milestones | Measure-Object -Property Weight -Sum).Sum) -ne 100) {
    throw 'Roadmap weights must total 100'
}
$score = 0.0
foreach ($milestone in $milestones) {
    if ($milestone.Complete -lt 0 -or $milestone.Complete -gt 100) {
        throw "Invalid milestone percentage: $($milestone.Name)"
    }
    $score += [double]$milestone.Weight * [double]$milestone.Complete / 100.0
}
$score = [math]::Round($score, 1)
$ci = [pscustomobject]@{ Status='OFFLINE'; Conclusion='NOT_CHECKED'; Url=''; HeadSha='' }
if (-not $Offline) {
    try {
        $encoded = [uri]::EscapeDataString($Branch)
        # Query the secure Windows workflow directly: a busy branch may have >30 unrelated runs.
        # Never treat pagination of unrelated workflows as proof that the release gate did not run.
        $endpoint = "https://api.github.com/repos/$Repository/actions/workflows/388403027/runs?branch=$encoded&per_page=30"
        $headers = @{ 'Accept'='application/vnd.github+json'; 'User-Agent'='Embasic-Public-Progress' }
        if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_TOKEN)) {
            $headers['Authorization'] = "Bearer $($env:GITHUB_TOKEN)"
        }
        $response = Invoke-RestMethod -Uri $endpoint -Headers $headers -TimeoutSec 12
        # Never report an older commit's green gate as evidence for the current branch HEAD.
        $headEndpoint = "https://api.github.com/repos/$Repository/commits/$encoded"
        $currentHead = Invoke-RestMethod -Uri $headEndpoint -Headers $headers -TimeoutSec 12
        if ([string]::IsNullOrWhiteSpace([string]$currentHead.sha)) { throw 'Cannot verify branch HEAD' }
        # A green MVP workflow must not conceal a failed secure Windows delivery gate.
        $run = $response.workflow_runs | Where-Object { $_.name -eq 'Windows Hosted CI (Secure Bootstrap)' -and $_.head_sha -eq $currentHead.sha } | Select-Object -First 1
        # Fail closed: a successful Desktop MVP run cannot substitute for a missing secure Windows gate.
        # Keep UNKNOWN/NO_RUN when the secure Windows workflow has no run in the queried window.
        if ($null -ne $run) {
            $ci = [pscustomobject]@{
                Status=[string]$run.status
                Conclusion=if ($run.conclusion) { [string]$run.conclusion } else { 'PENDING' }
                Url=[string]$run.html_url
                HeadSha=[string]$run.head_sha
            }
        } else {
            $ci = [pscustomobject]@{Status='UNKNOWN';Conclusion='NO_RUN';Url='';HeadSha=''}
        }
    } catch {
        $ci = [pscustomobject]@{Status='UNAVAILABLE';Conclusion='NETWORK_OR_RATE_LIMIT';Url='';HeadSha=''}
    }
}
$report = [pscustomobject]@{
    SchemaVersion=1
    Repository=$Repository
    Branch=$Branch
    ReviewDate=$reviewDate
    IstanbulTimestamp=$today.ToString('yyyy-MM-dd HH:mm:ss zzz')
    ProgressPercent=$score
    ProgressBasis='Manually weighted product milestones; NOT a UYAP accuracy or release-certification metric'
    Milestones=$milestones
    Ci=$ci
    GuiAcceptance=[pscustomobject]@{
        Status='CI_VERIFIED_LIMITED'
        Mode='Isolated packaged Windows CI; not an external user beta'
        Test='Packaged GUI Robot and test-only JDI render-boundary acceptance (TUR244/TUR245 evidence)'
        VerifiedPhysicalWindows=$true
    }
    Forecast=[pscustomobject]@{
        BetaEarliest=$forecastBase.AddDays(21).ToString('yyyy-MM-dd')
        BetaLatest=$forecastBase.AddDays(42).ToString('yyyy-MM-dd')
        FinalEarliest=$forecastBase.AddDays(70).ToString('yyyy-MM-dd')
        FinalLatest=$forecastBase.AddDays(126).ToString('yyyy-MM-dd')
        Confidence='LOW'
        Assumptions='Daily development, rights-cleared UDF samples, successful Windows UI tests, approved redistribution; any blocker moves the dates'
    }
    ReleaseGate='BLOCKED: real UDF fidelity, GUI acceptance, third-party license/NOTICE and download authorization outstanding'
}
if ($AsJson) {
    $report | ConvertTo-Json -Depth 8
    exit 0
}
function Draw-Bar([double]$pct, [int]$width=23) {
    $filled = [math]::Min($width,[math]::Max(0,[int][math]::Round($pct*$width/100)))
    return ('█' * $filled) + ('░' * ($width-$filled))
}
$edge = '═' * 84
Write-Host ''
Write-Host ('╔' + $edge + '╗') -ForegroundColor DarkCyan
Write-Host ('║  EMBASIC UDF → PDF  |  PUBLIC MVP / GELISTIRME KONTROL PANOSU' + (' ' * 21) + '║') -ForegroundColor Cyan
Write-Host ('╚' + $edge + '╝') -ForegroundColor DarkCyan
Write-Host "  Tarih (TR)  : $($report.IstanbulTimestamp)" -ForegroundColor Gray
Write-Host "  Kaynak     : $Repository" -ForegroundColor Gray
Write-Host "  Dal        : $Branch" -ForegroundColor Gray
Write-Host ''
Write-Host ("  GENEL URUN OLGUNLUGU  {0,5:N1}%  {1}" -f $score,(Draw-Bar $score 29)) -ForegroundColor Yellow
Write-Host '  NOT: Yuzde agirlikli tahmindir; CI PASS = urun FINAL demek degildir.' -ForegroundColor DarkYellow
Write-Host ''
foreach ($m in $milestones) {
    $color = if ($m.Complete -ge 90) { 'Green' } elseif ($m.Complete -ge 60) { 'Yellow' } else { 'Red' }
    $name = $m.Name.PadRight(29)
    Write-Host ("  {0} {1,3}%  {2}  [agirlik {3,2}%]" -f $name,$m.Complete,(Draw-Bar $m.Complete 18),$m.Weight) -ForegroundColor $color
    Write-Host ("     Kanit: " + $m.Evidence) -ForegroundColor DarkGray
}
Write-Host ''
$ciColor = if ($ci.Conclusion -eq 'success') { 'Green' } elseif ($ci.Conclusion -eq 'failure') { 'Red' } else { 'Yellow' }
Write-Host "  GITHUB CI  : $($ci.Status) / $($ci.Conclusion)" -ForegroundColor $ciColor
if ($ci.Url) { Write-Host "  CI URL     : $($ci.Url)" -ForegroundColor Gray }
Write-Host '  ROBOT GUI   : CI_VERIFIED_LIMITED — paketli Windows Robot/JDI kabulü var; dış kullanıcı beta NOT_RUN' -ForegroundColor Yellow
Write-Host '  YAYIN GATE: KAPALI — hak/lisans, gercek UDF, arayuz kabul testleri eksik' -ForegroundColor Red
Write-Host ''
Write-Host ("  ILK BETA ICIN PLANLAMA : {0} — {1}" -f $report.Forecast.BetaEarliest,$report.Forecast.BetaLatest) -ForegroundColor Cyan
Write-Host ("  FINAL ICIN KOSULLU ETA : {0} — {1}" -f $report.Forecast.FinalEarliest,$report.Forecast.FinalLatest) -ForegroundColor Magenta
Write-Host '  Guven dusuk: UDF kaynak ornekleri, tum surum destegi, lisans ve Windows GUI kabul kosullarina bagli.' -ForegroundColor DarkYellow
Write-Host '  Bu tarihler taahhut degil; engeller giderilmezse otomatik olarak ertelenir.' -ForegroundColor DarkYellow
Write-Host ''
