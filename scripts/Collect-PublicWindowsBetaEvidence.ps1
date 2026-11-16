#requires -Version 5.1
<#
.SYNOPSIS
  Collects reproducible, local-only Windows beta evidence for W01/W07.
.DESCRIPTION
  Does not launch the application, upload files, claim physical hardware,
  or mark any acceptance step PASS. Never point it at confidential case data.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$AppExecutable,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedLauncherSha256
)
$ErrorActionPreference = 'Stop'
# Use unresolved provider paths before any side effects: Resolve-Path of a
# junction can conceal the original reparse point.
function Resolve-LocalPath([string]$p) {
    $native = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($p)
    return [IO.Path]::GetFullPath($native)
}
function Assert-NoReparseComponents([string]$p) {
    $cursor = [IO.Path]::GetFullPath($p)
    while ($true) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force -ErrorAction Stop
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw ('Reparse-point path component is not allowed for beta evidence: ' + $cursor)
            }
        }
        $parent = [IO.Directory]::GetParent($cursor)
        if ($null -eq $parent) { break }
        $cursor = $parent.FullName
    }
}
$requestedExe = Resolve-LocalPath $AppExecutable
$requestedOutput = Resolve-LocalPath $OutputDirectory
Assert-NoReparseComponents $requestedExe
Assert-NoReparseComponents $requestedOutput
$exe = (Resolve-Path -LiteralPath $requestedExe -ErrorAction Stop).ProviderPath
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw 'AppExecutable must be a file' }
if ([IO.Path]::GetExtension($exe).ToLowerInvariant() -ne '.exe') { throw 'Expected Windows .exe app-image launcher' }
# Check the intended output path before creating any directory. Rejected
# input must not leave even an empty directory inside the app-image.
$appDirectory = [IO.Path]::GetDirectoryName($exe).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$outDirectory = $requestedOutput.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
if ($outDirectory.Equals($appDirectory, [StringComparison]::OrdinalIgnoreCase) -or
    $outDirectory.StartsWith($appDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Evidence output must be outside the application directory'
}
New-Item -ItemType Directory -Path $requestedOutput -Force -ErrorAction Stop | Out-Null
Assert-NoReparseComponents $requestedOutput
$out = (Resolve-Path -LiteralPath $requestedOutput -ErrorAction Stop).ProviderPath
# The executable hash and byte count must describe one stable read-only
# snapshot; a mutable launcher is not admissible beta evidence.
$stream = New-Object IO.FileStream($exe, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
try {
    $launcherBytes = $stream.Length
    if ($launcherBytes -le 0) { throw 'Empty app-image launcher is not valid beta evidence' }
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $launcherSha = ([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
        if ($stream.Length -ne $launcherBytes) { throw 'Launcher changed during hashing' }
        if ($ExpectedLauncherSha256 -and -not $launcherSha.Equals($ExpectedLauncherSha256, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Launcher SHA-256 does not match the independently approved beta artifact'
        }
    } finally { $hasher.Dispose() }
} finally { $stream.Dispose() }
$stamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$target = Join-Path $out ("public-windows-beta-" + $stamp + ".json")
if (Test-Path -LiteralPath $target) { throw 'Evidence filename collision' }
$os = Get-CimInstance Win32_OperatingSystem
$javaVersion = 'NOT_VERIFIED' # Record the actual runtime separately; PATH is not proof of bundled Java.
$evidence = [ordered]@{
    schema = 'embasic-public-windows-beta-evidence-v1'
    captured_utc = [DateTimeOffset]::UtcNow.ToString('o')
    scope = 'independent Public app only'
    executable_name = [IO.Path]::GetFileName($exe)
    executable_sha256 = $launcherSha
    expected_launcher_sha256 = $(if ($ExpectedLauncherSha256) { $ExpectedLauncherSha256.ToLowerInvariant() } else { 'NOT_PROVIDED' })
    executable_bytes = $launcherBytes
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
# Exclusive CreateNew is atomic; Test-Path alone would permit an
# overwrite race between two collectors started in the same second.
$bytes = (New-Object Text.UTF8Encoding($false)).GetBytes($json + [Environment]::NewLine)
$evidenceStream = New-Object IO.FileStream($target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $evidenceStream.Write($bytes, 0, $bytes.Length)
    $evidenceStream.Flush($true)
} finally { $evidenceStream.Dispose() }
Write-Output ("EVIDENCE_FILE=" + $target)
Write-Output 'W01=NOT_RUN W07=NOT_RUN RELEASE=NO-GO'
