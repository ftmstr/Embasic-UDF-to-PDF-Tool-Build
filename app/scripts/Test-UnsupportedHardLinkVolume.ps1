#requires -Version 7.0
<#
.SYNOPSIS
  Preflight a user-selected Windows volume for a physical unsupported-hard-link PDF save test.
.DESCRIPTION
  Uses only two fresh synthetic temporary names in the selected directory.
  Never touches an existing PDF or invokes the application. A supported link
  is NOT evidence for the unsupported-hard-link GUI scenario.
  Exit 0 only when the filesystem refuses hard links; otherwise exit 1.
.EXAMPLE
  pwsh -NoProfile -File app/scripts/Test-UnsupportedHardLinkVolume.ps1 -Directory 'X:\\scratch'
#>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Windows fiziksel dosya sistemi kabulü gereklidir.' }
$folder = (Resolve-Path -LiteralPath $Directory -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $folder -PathType Container)) {
    throw 'Geçerli bir klasör seçilmelidir.'
}
$token = [guid]::NewGuid().ToString('N')
$source = Join-Path $folder ('.embasic-link-probe-' + $token + '.tmp')
$dest = Join-Path $folder ('.embasic-link-probe-' + $token + '.link')
$refused = $false
try {
    # CreateNew guarantees this probe never replaces a pre-existing file.
    $stream = [System.IO.File]::Open($source, [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        $bytes = [System.Text.Encoding]::ASCII.GetBytes('EMBASIC_SYNTHETIC_HARDLINK_PROBE')
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
    try {
        [System.IO.File]::CreateHardLink($dest, $source) | Out-Null
        Write-Host 'HARDLINK_SUPPORTED: Bu dizin unsupported-hardlink fiziksel GUI kabulü için uygun değil.'
    } catch [System.IO.IOException] {
        $refused = $true
        Write-Host 'HARDLINK_REFUSED: Bu dizin fiziksel GUI ret kabulü için adaydır; uygulama testi henüz yapılmadı.'
    } catch [System.UnauthorizedAccessException] {
        $refused = $true
        Write-Host 'HARDLINK_ACCESS_DENIED: İzin hatası; uygulama hata kabulü henüz yapılmadı.'
    }
} finally {
    # Never delete user paths; both names contain a fresh GUID.
    if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Force }
    if (Test-Path -LiteralPath $source) { Remove-Item -LiteralPath $source -Force }
}
if (-not $refused) { exit 1 }
