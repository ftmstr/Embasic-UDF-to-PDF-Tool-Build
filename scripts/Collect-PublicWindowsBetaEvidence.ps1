#requires -Version 5.1
<#
.SYNOPSIS
  Collects reproducible, local-only Windows beta evidence for W01/W07.
.DESCRIPTION
  Does not launch the application, upload files, claim physical hardware,
  or mark any acceptance step PASS. Never point it at confidential case data.
.EXAMPLE
  powershell -NoProfile -File scripts/Collect-PublicWindowsBetaEvidence.ps1 -AppExecutable C:\beta\Embasic.exe -OutputDirectory C:\beta-evidence
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$AppExecutable,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$exe = (Resolve-Path -LiteralPath $AppExecutable -ErrorAction Stop).ProviderPath
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw 'AppExecutable must be a file' }
if ([IO.Path]::GetExtension($exe).ToLowerInvariant() -ne '.exe') { throw 'Expected Windows .exe app-image launcher' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$out = (Resolve-Path -LiteralPath $OutputDirectory).ProviderPath
if ($exe.StartsWith($out + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Evidence output must be outside the application directory'
}
$stamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$target = Join-Path $out ("public-windows-beta-" + $stamp + ".json")
if (Test-Path -LiteralPath $target) { throw 'Evidence filename collision' }
$os = Get-CimInstance Win32_OperatingSystem
$java = Get-Command java -ErrorAction SilentlyContinue
$javaVersion = if ($java) { (& java -version 2>&1 | Select-Object -First 1 | Out-String).Trim() } else { 'NOT_FOUND' }
$evidence = [ordered]@{
    schema = 'embasic-public-windows-beta-evidence-v1'
    captured_utc = [DateTimeOffset]::UtcNow.ToString('o')
    scope = 'independent Public app only'
    executable_name = [IO.Path]::GetFileName($exe)
    executable_sha256 = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
    executable_bytes = (Get-Item -LiteralPath $exe).Length
    windows_caption = $os.Caption
    windows_version = $os.Version
    windows_build = $os.BuildNumber
    java_version = $javaVersion
    physical_windows_operator_attestation = 'NOT_VERIFIED'
    W01 = 'NOT_RUN'
    W07 = 'NOT_RUN'
    source_license_notice = 'NOT_VERIFIED'
    native_UYAP = 'NOT_VERIFIED'
    exFAT_physical = 'NOT_RUN'
    hostile_CAS = 'NO-GO'
    release = 'NO-GO'
}
$json = $evidence | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText($target, $json + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
Write-Output ("EVIDENCE_FILE=" + $target)
Write-Output 'W01=NOT_RUN W07=NOT_RUN RELEASE=NO-GO'
