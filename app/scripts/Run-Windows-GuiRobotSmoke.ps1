#requires -Version 7.0
<#
.SYNOPSIS
  Explicit local Windows GUI acceptance (mouse drag, autoscroll, accessible target).
.DESCRIPTION
  Requires an interactive unlocked Windows desktop, JDK 17, Maven and a local
  checkout of the public-only repository. Never runs in ordinary scheduled CI.
  The test uses synthetic ZIP UDF fixtures and publishes nothing.
.EXAMPLE
  pwsh -NoProfile -File app/scripts/Run-Windows-GuiRobotSmoke.ps1 -AllowGui
#>
[CmdletBinding()]
param([switch]$AllowGui)
$ErrorActionPreference = 'Stop'
if (-not $AllowGui) { throw 'GUI kontrolü yalnız açık -AllowGui izniyle çalışır.' }
if (-not $IsWindows) { throw 'Bu test etkileşimli Windows masaüstü gerektirir.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Write-Host 'Embasic PUBLIC — yerel Windows Robot GUI kabul kontrolü' -ForegroundColor Cyan
Write-Host 'UYARI: Test geçerken fareyi veya klavyeyi kullanmayın; sentetik verilerle çalışır.' -ForegroundColor Yellow
$report = Join-Path $root 'app/target/surefire-reports/TEST-com.embasic.publicapp.GuiRobotSmokeTest.xml'
# Remove stale evidence so an old successful report cannot satisfy this invocation.
if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report -Force }
& mvn -B -ntp -f (Join-Path $root 'app/pom.xml') '-Dtest=GuiRobotSmokeTest' '-Dembasic.gui.robot=true' test
if ($LASTEXITCODE -ne 0) {
    throw 'GUI Robot testi başarısız veya etkileşimli masaüstü yok. GUI kabulü doğrulanamadı.'
}
# Maven may exit zero when a JUnit test is skipped; that is NOT physical GUI acceptance.
if (-not (Test-Path -LiteralPath $report)) { throw 'GUI Robot JUnit XML raporu bulunamadı; kabul doğrulanamadı.' }
[xml]$result = Get-Content -LiteralPath $report -Raw
$suite = $result.testsuite
# Reject malformed/incomplete Surefire evidence: absent counters otherwise cast to zero.
if ($null -eq $suite) { throw 'GUI Robot JUnit XML testsuite öğesi eksik.' }
foreach ($field in @('tests', 'skipped', 'failures', 'errors')) {
    $attribute = $suite.GetAttribute($field)
    $count = 0
    if ([string]::IsNullOrWhiteSpace($attribute) -or
        -not [int]::TryParse($attribute, [ref]$count) -or $count -lt 0) {
        throw "GUI Robot JUnit XML geçersiz veya eksik sayaç: $field"
    }
}
if ([int]$suite.tests -lt 1 -or [int]$suite.skipped -ne 0 -or
    [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or
    @($suite.testcase).Count -ne [int]$suite.tests) {
    throw 'GUI Robot testleri eksik, atlandı veya başarısız; fiziksel kabul PASS değildir.'
}
Write-Host 'GUI Robot testi tamamlandı; JUnit XML sıfır skip/failure/error doğrulandı.' -ForegroundColor Green
