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
        New-Item -ItemType HardLink -Path $dest -Target $source -ErrorAction Stop | Out-Null
        Write-Host 'HARDLINK_SUPPORTED: Bu dizin unsupported-hardlink fiziksel GUI kabulü için uygun değil.'
    } catch {
        # Only the native Windows ERROR_NOT_SUPPORTED (50) is an unsupported-link
        # candidate. Access denied, invalid paths, policy and other failures must
        # never be misreported as a filesystem capability finding.
        $errorRecord = $_
        $exception = $errorRecord.Exception
        $unsupported = $false
        while ($null -ne $exception) {
            # Require a native Win32 error or HRESULT_FROM_WIN32(50), not
            # an arbitrary exception whose low 16 bits happen to equal 50.
            $nativeUnsupported = ($exception -is [System.ComponentModel.Win32Exception]) -and
                ($exception.NativeErrorCode -eq 50)
            $hresultUnsupported = ($exception -is [System.IO.IOException]) -and
                ([uint32]($exception.HResult -band 0xFFFFFFFFL) -eq 0x80070032L)
            if ($nativeUnsupported -or $hresultUnsupported) {
                $unsupported = $true
                break
            }
            $exception = $exception.InnerException
        }
        if ($unsupported) {
            $refused = $true
            Write-Host 'HARDLINK_UNSUPPORTED: Windows ERROR_NOT_SUPPORTED (50); fiziksel GUI kabulü henüz yapılmadı.'
        } else {
            Write-Warning ('HARDLINK_INDETERMINATE: Hard-link denemesi başarısız; desteklenmiyor sonucu çıkarılamaz. ' + $errorRecord.FullyQualifiedErrorId)
            throw
        }
    }
} finally {
    # Never delete user paths; both names contain a fresh GUID.
    if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Force }
    if (Test-Path -LiteralPath $source) { Remove-Item -LiteralPath $source -Force }
}
if (-not $refused) { exit 1 }
