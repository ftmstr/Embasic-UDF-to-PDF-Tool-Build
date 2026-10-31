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
& mvn -B -ntp -f (Join-Path $root 'app/pom.xml') '-Dtest=GuiRobotSmokeTest' '-Dembasic.gui.robot=true' test
if ($LASTEXITCODE -ne 0) {
    throw 'GUI Robot testi başarısız veya etkileşimli masaüstü yok. GUI kabulü doğrulanamadı.'
}
Write-Host 'GUI Robot testi tamamlandı. Rapor: app/target/surefire-reports/' -ForegroundColor Green
