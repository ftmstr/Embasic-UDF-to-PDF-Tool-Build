#requires -Version 7.0
<#
.SYNOPSIS
  TUR260: opt-in Windows-hosted-only physical exFAT unsupported-hardlink
  jpackage GUI acceptance. A disposable virtual disk is the ONLY formatted disk.
.DESCRIPTION
  If the hosted runner cannot create an isolated disposable VHD, report
  PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN. Never attempt to format an
  existing physical disk. Test failures after a verified exFAT probe FAIL CI.
#>
[CmdletBinding()]
param([switch]$AllowEphemeralDisk)
$ErrorActionPreference = 'Stop'
function Not-Run([string]$why) {
    Write-Host ("PHYSICAL_UNSUPPORTED_HARDLINK_EXE=NOT_RUN reason=" + $why)
}
if (-not $AllowEphemeralDisk -or -not $IsWindows -or
    $env:GITHUB_ACTIONS -ne 'true' -or
    $env:RUNNER_ENVIRONMENT -ne 'github-hosted' -or
    [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    Not-Run 'isolated_hosted_windows_permission_missing'
    exit 0
}
$admin = [Security.Principal.WindowsPrincipal]::new(
    [Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $admin.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Not-Run 'hosted_runner_is_not_administrator'
    exit 0
}
$runnerTemp = (Resolve-Path -LiteralPath $env:RUNNER_TEMP).Path
$token = [guid]::NewGuid().ToString('N')
$root = Join-Path $runnerTemp ('embasic-tur260-disposable-' + $token)
New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
$vhd = Join-Path $root 'tur260-ephemeral.vhdx'
$mounted = $false
$provenExfat = $false
try {
    $existingDiskNumbers = @(Get-Disk -ErrorAction Stop | ForEach-Object Number)
    try {
        # New-VHD may be absent on hosted Windows. DiskPart CREATE VDISK
        # is a fallback for file creation ONLY. Never let DiskPart SELECT,
        # CLEAN, FORMAT or ASSIGN anything; all later operations target
        # the disk returned from this unique VHD's Get-DiskImage object.
        if ($null -ne (Get-Command New-VHD -ErrorAction SilentlyContinue)) {
            New-VHD -Path $vhd -SizeBytes 268435456 -Dynamic -ErrorAction Stop | Out-Null
        } else {
            if ($vhd.Contains('"')) { throw 'Invalid disposable VHD filename' }
            $instructions = Join-Path $root 'create-vdisk.txt'
            Set-Content -LiteralPath $instructions -Encoding ASCII -Value @(
                ('create vdisk file="' + $vhd + '" maximum=256 type=expandable'),
                'exit')
            $diskpartLog = Join-Path $root 'diskpart-create.log'
            & diskpart.exe /s $instructions 2>&1 | Out-File -LiteralPath $diskpartLog
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $vhd -PathType Leaf)) {
                throw 'Disposable VHD creation unavailable on runner'
            }
        }
        if (-not (Test-Path -LiteralPath $vhd -PathType Leaf)) {
            throw 'Disposable VHD was not created'
        }
        Mount-DiskImage -ImagePath $vhd -StorageType VHD -NoDriveLetter -ErrorAction Stop | Out-Null
        $mounted = $true
        $image = Get-DiskImage -ImagePath $vhd -ErrorAction Stop
        $disk = $image | Get-Disk -ErrorAction Stop
        if ($null -eq $disk -or $disk.Count -gt 1 -or
            $existingDiskNumbers -contains $disk.Number -or
            $disk.IsBoot -or $disk.IsSystem -or
            $disk.PartitionStyle -ne 'RAW' -or
            $disk.Size -lt 200MB -or $disk.Size -gt 300MB) {
            throw 'Disposable VHD disk identity guard rejected formatting'
        }
        # Format only a newly mounted RAW disk, identified directly by
        # Get-DiskImage for the GUID-named file under RUNNER_TEMP.
        $diskNumber = [uint32]$disk.Number
        Initialize-Disk -Number $diskNumber -PartitionStyle MBR -ErrorAction Stop | Out-Null
        $partition = New-Partition -DiskNumber $diskNumber -UseMaximumSize -AssignDriveLetter -ErrorAction Stop
        if ($null -eq $partition -or -not $partition.DriveLetter) {
            throw 'Disposable VHD partition was not assigned a letter'
        }
        Format-Volume -Partition $partition -FileSystem exFAT -NewFileSystemLabel 'EMBASIC_T260' -Confirm:$false -Force -ErrorAction Stop | Out-Null
        $volume = Get-Volume -DriveLetter $partition.DriveLetter -ErrorAction Stop
        $backedDisk = Get-Partition -DriveLetter $partition.DriveLetter -ErrorAction Stop | Get-Disk -ErrorAction Stop
        if ($volume.FileSystem -ne 'exFAT' -or
            $volume.FileSystemLabel -ne 'EMBASIC_T260' -or
            $backedDisk.Number -ne $diskNumber) {
            throw 'Formatted volume is not a verified disposable exFAT VHD'
        }
        $provenExfat = $true
    } catch {
        # Missing Hyper-V / Storage privileges / VHD support is a capability
        # limitation, never proof that the PDF app accepted a filesystem race.
        if ($provenExfat) { throw }
        Not-Run ('hosted_vhd_environment_unavailable_' + $_.Exception.GetType().Name)
        return
    }
    $scratch = Join-Path ([string]$partition.DriveLetter + ':\') 'embasic-tur260-only'
    New-Item -ItemType Directory -Path $scratch -ErrorAction Stop | Out-Null
    & pwsh -NoProfile -File app/scripts/Test-UnsupportedHardLinkVolume.ps1 -Directory $scratch
    if ($LASTEXITCODE -ne 0) {
        # The real physical probe is inconclusive if error is not precisely
        # Windows ERROR_NOT_SUPPORTED(50). No attempt at a GUI claim.
        Not-Run 'physical_exfat_native_hardlink_error_not_verified'
        return
    }
    Write-Host 'TUR260_EXFAT_VHD_READY filesystem=exFAT rawDisk=NEW hardlink=UNSUPPORTED onlySynthetic=TRUE'
    & pwsh -NoProfile -File app/scripts/Assert-PackagedGuiInput.ps1 -AllowGui -UnsupportedLinkVolumeDirectory $scratch
    if ($LASTEXITCODE -ne 0) {
        throw 'Verified exFAT volume physical packaged GUI acceptance FAILED'
    }
    Write-Host 'PHYSICAL_UNSUPPORTED_HARDLINK_EXE=PASS filesystem=exFAT independentVHD=TRUE physicalGUI=TRUE'
} finally {
    if ($mounted) {
        # Dismount the exact GUID-named image; do NOT touch disk by number
        # during cleanup, and never eject other mounted storage.
        try {
            Dismount-DiskImage -ImagePath $vhd -ErrorAction Stop | Out-Null
        } catch {
            Write-Warning 'Disposable VHD dismount failed; prevent further host disk mutation.'
            throw
        }
    }
    if (Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop
    }
}
