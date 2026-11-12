#requires -Version 7.0
<#
.SYNOPSIS
  Opt-in physical Windows Robot acceptance of the ephemeral jpackage EXE.
.DESCRIPTION
  The target must be the launched app-image's visible, enabled MVP JFrame.
  Physical mouse focus, Ctrl+O/Escape and Ctrl+T are delivered by java.awt.Robot
  in a separate JVM. The chooser must close and the empty grid background must
  visibly change and restore across theme and thumbnail-zoom round-trips. No UDF is imported,
  no user files are read, and no executable or archive is published.
#>
[CmdletBinding()]
param([switch]$AllowGui)
$ErrorActionPreference = 'Stop'
if (-not $AllowGui -or -not $IsWindows) {
    throw 'Packaged physical Robot requires explicit -AllowGui on Windows.'
}
$exe = Join-Path $env:RUNNER_TEMP 'embasic-app-image/EmbasicPublicMvp/EmbasicPublicMvp.exe'
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw 'Ephemeral jpackage EXE not found; packaged GUI NOT_VERIFIED.'
}
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class PackagedGuiWindows {
    public delegate bool EnumCallback(IntPtr hwnd, IntPtr ignored);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumCallback callback, IntPtr ignored);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool IsWindowEnabled(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr hwnd, StringBuilder title, int capacity);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hwnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
    [DllImport("user32.dll")] public static extern IntPtr GetWindow(IntPtr hwnd, uint command);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }

    public static IntPtr FindMainWindow(uint[] processIds) {
        IntPtr found = IntPtr.Zero;
        EnumWindows((hwnd, ignored) => {
            uint owner;
            GetWindowThreadProcessId(hwnd, out owner);
            if (Array.IndexOf(processIds, owner) < 0 ||
                !IsWindowVisible(hwnd) || !IsWindowEnabled(hwnd)) return true;
            var title = new StringBuilder(256);
            GetWindowText(hwnd, title, title.Capacity);
            RECT client;
            if (title.ToString() == "Embasic UDF to PDF \u2014 Public MVP" &&
                GetClientRect(hwnd, out client) &&
                client.Right - client.Left >= 320 &&
                client.Bottom - client.Top >= 240) {
                found = hwnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static IntPtr FindOwnedDialog(IntPtr mainWindow) {
        IntPtr found = IntPtr.Zero;
        EnumWindows((hwnd, ignored) => {
            if (hwnd == mainWindow || !IsWindowVisible(hwnd) ||
                !IsWindowEnabled(hwnd) || GetWindow(hwnd, 4) != mainWindow)
                return true;
            var title = new StringBuilder(256);
            GetWindowText(hwnd, title, title.Capacity);
            if (title.Length == 0) return true;
            found = hwnd;
            return false;
        }, IntPtr.Zero);
        return found;
    }

    public static int[] BackgroundSamplePoint(IntPtr hwnd) {
        RECT r;
        if (!GetWindowRect(hwnd, out r) || r.Right - r.Left < 850 ||
            r.Bottom - r.Top < 610) return null;
        // Empty workspace: center-right grid, away from toolbar and add tile.
        return new int[] { r.Left + (r.Right - r.Left) * 3 / 4,
                           r.Top + (r.Bottom - r.Top) * 3 / 5 };
    }

    public static int[] ZoomTileRegion(IntPtr hwnd) {
        RECT r;
        if (!GetWindowRect(hwnd, out r) || r.Right - r.Left < 850 ||
            r.Bottom - r.Top < 610) return null;
        // Bounded top-left viewport region containing the in-grid + UDF tile.
        // Excludes the title bar, toolbar, status row and moving mouse cursor.
        return new int[] { r.Left + 35, r.Top + 145, 300, 320 };
    }

    public static int[] TitleClickPoint(IntPtr hwnd) {
        RECT r;
        if (!GetWindowRect(hwnd, out r) || r.Right - r.Left < 320 ||
            r.Bottom - r.Top < 240) return null;
        return new int[] { r.Left + 120, r.Top + 12 };
    }
}
'@
$classes = Join-Path $env:RUNNER_TEMP 'embasic-packaged-robot-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& javac -d $classes app/scripts/PackagedInputRobot.java
if ($LASTEXITCODE -ne 0) { throw 'Packaged Robot helper failed to compile.' }
$process = $null
try {
    $process = Start-Process -FilePath $exe -PassThru -ErrorAction Stop
    if ($process.SessionId -ne [System.Diagnostics.Process]::GetCurrentProcess().SessionId) {
        throw 'Packaged EXE is in another Windows session; GUI NOT_VERIFIED.'
    }
    $deadline = (Get-Date).AddSeconds(30)
    $main = [IntPtr]::Zero
    $ids = [System.Collections.Generic.List[uint32]]::new()
    while ((Get-Date) -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) { throw 'Packaged EXE exited before GUI acceptance.' }
        $ids.Clear()
        $ids.Add([uint32]$process.Id)
        $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.Id)" -ErrorAction Stop)
        foreach ($child in $children) {
            $childProcess = Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue
            if ($null -ne $childProcess -and -not $childProcess.HasExited -and
                $childProcess.SessionId -eq $process.SessionId) {
                $ids.Add([uint32]$child.ProcessId)
            }
        }
        $main = [PackagedGuiWindows]::FindMainWindow($ids.ToArray())
        if ($main -ne [IntPtr]::Zero) { break }
        Start-Sleep -Milliseconds 300
    }
    if ($main -eq [IntPtr]::Zero) {
        throw 'No owned, enabled, visible packaged MVP window; GUI NOT_VERIFIED.'
    }
    if ([PackagedGuiWindows]::FindOwnedDialog($main) -ne [IntPtr]::Zero) {
        throw 'Unexpected pre-existing modal window; refusing ambiguous acceptance.'
    }
    $point = [PackagedGuiWindows]::TitleClickPoint($main)
    if ($null -eq $point) { throw 'Packaged window has invalid geometry.' }
    & java -cp $classes PackagedInputRobot focus $point[0] $point[1]
    if ($LASTEXITCODE -ne 0) { throw 'Physical Robot mouse focus failed.' }
    Start-Sleep -Milliseconds 300
    if ([PackagedGuiWindows]::GetForegroundWindow() -ne $main) {
        throw 'Physical Robot did not focus the packaged MVP window.'
    }
    & java -cp $classes PackagedInputRobot open
    if ($LASTEXITCODE -ne 0) { throw 'Physical Robot Ctrl+O failed.' }
    $deadline = (Get-Date).AddSeconds(12)
    $dialog = [IntPtr]::Zero
    while ((Get-Date) -lt $deadline) {
        $dialog = [PackagedGuiWindows]::FindOwnedDialog($main)
        if ($dialog -ne [IntPtr]::Zero) { break }
        Start-Sleep -Milliseconds 200
    }
    if ($dialog -eq [IntPtr]::Zero) {
        throw 'Ctrl+O did not open a visible owned chooser; packaged GUI FAIL.'
    }
    if ([PackagedGuiWindows]::GetForegroundWindow() -ne $dialog) {
        throw 'Chooser is not the foreground window; input acceptance inconclusive.'
    }
    & java -cp $classes PackagedInputRobot cancel
    if ($LASTEXITCODE -ne 0) { throw 'Physical Robot Escape failed.' }
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        if ([PackagedGuiWindows]::FindOwnedDialog($main) -eq [IntPtr]::Zero) { break }
        Start-Sleep -Milliseconds 200
    }
    if ([PackagedGuiWindows]::FindOwnedDialog($main) -ne [IntPtr]::Zero -or
        [PackagedGuiWindows]::FindMainWindow($ids.ToArray()) -ne $main) {
        throw 'Chooser failed to close or packaged MVP window disappeared.'
    }
    if ([PackagedGuiWindows]::GetForegroundWindow() -ne $main) {
        throw 'Packaged MVP did not regain foreground after chooser cancellation.'
    }
    $samplePoint = [PackagedGuiWindows]::BackgroundSamplePoint($main)
    if ($null -eq $samplePoint) { throw 'Cannot sample packaged grid at invalid window geometry.' }
    function Get-GridPixel([int]$x, [int]$y) {
        $value = & java -cp $classes PackagedInputRobot pixel $x $y
        if ($LASTEXITCODE -ne 0 -or $value -notmatch '^#[0-9A-F]{6}$') {
            throw 'Physical packaged GUI pixel probe failed; theme NOT_VERIFIED.'
        }
        return $value
    }
    $before = Get-GridPixel $samplePoint[0] $samplePoint[1]
    & java -cp $classes PackagedInputRobot theme
    if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+T theme toggle failed.' }
    Start-Sleep -Milliseconds 700
    if ([PackagedGuiWindows]::GetForegroundWindow() -ne $main) {
        throw 'Packaged MVP lost foreground during theme acceptance.'
    }
    $after = Get-GridPixel $samplePoint[0] $samplePoint[1]
    if ($after -eq $before) {
        throw 'Ctrl+T did not visibly change the empty workspace background.'
    }
    & java -cp $classes PackagedInputRobot theme
    if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+T theme restore failed.' }
    Start-Sleep -Milliseconds 700
    $restored = Get-GridPixel $samplePoint[0] $samplePoint[1]
    if ($restored -ne $before) {
        throw 'Ctrl+T did not restore the original workspace background.'
    }
    Write-Host "PACKAGED-THEME-ROBOT-PASS: physical Ctrl+T twice, grid $before -> $after -> $restored; no files imported."

    # Compare rendered pixels, not a synthetic Swing action invocation: this
    # verifies that the packaged EXE responds to Ctrl+= and Ctrl+0 while the
    # real Windows desktop owns keyboard focus. No UDF fixture is required.
    $region = [PackagedGuiWindows]::ZoomTileRegion($main)
    if ($null -eq $region) { throw 'Packaged zoom region has invalid geometry.' }
    function Get-ZoomRegionSha([int[]]$bounds) {
        $hash = & java -cp $classes PackagedInputRobot region $bounds[0] $bounds[1] $bounds[2] $bounds[3]
        if ($LASTEXITCODE -ne 0 -or $hash -notmatch '^[0-9a-f]{64}' -or $hash.Length -ne 64) {
            throw 'Physical packaged zoom screenshot probe failed; zoom NOT_VERIFIED.'
        }
        return $hash
    }
    if ([PackagedGuiWindows]::GetForegroundWindow() -ne $main) {
        throw 'Packaged MVP lost foreground before physical zoom acceptance.'
    }
    $zoomBefore = Get-ZoomRegionSha $region
    & java -cp $classes PackagedInputRobot zoom
    if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+= zoom failed.' }
    Start-Sleep -Milliseconds 800
    if ([PackagedGuiWindows]::GetForegroundWindow() -ne $main -or
        [PackagedGuiWindows]::FindOwnedDialog($main) -ne [IntPtr]::Zero) {
        throw 'Unexpected window focus or modal dialog during packaged zoom.'
    }
    $zoomAfter = Get-ZoomRegionSha $region
    if ($zoomAfter -eq $zoomBefore) {
        throw 'Physical Ctrl+= did not change the rendered + UDF tile region.'
    }
    & java -cp $classes PackagedInputRobot resetzoom
    if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+0 zoom reset failed.' }
    Start-Sleep -Milliseconds 800
    $zoomRestored = Get-ZoomRegionSha $region
    if ($zoomRestored -ne $zoomBefore) {
        throw 'Physical Ctrl+0 did not restore the rendered + UDF tile region.'
    }
    Write-Host 'PACKAGED-ZOOM-ROBOT-PASS: physical Ctrl+= changed the rendered + UDF tile; Ctrl+0 restored SHA-256 pixels; no files imported.'
    # TUR214: real packaged EXE input acceptance with two rights-clean
    # synthetic UDF ZIPs. The only sources are created under RUNNER_TEMP.
    # This is deliberately not a claim of official UYAP compatibility.
    $fixtureDir = Join-Path $env:RUNNER_TEMP 'embasic-tur214-robot-fixtures'
    New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null
    function New-SyntheticUdf([string]$path, [string]$marker) {
        $xml = "<template format_id='1.0'><content>$marker</content><properties pageSize='A4' orientation='portrait' margin='48'/><elements><paragraph><content startOffset='0' length='$($marker.Length)'/></paragraph></elements></template>"
        $archive = [System.IO.Compression.ZipFile]::Open($path, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            $entry = $archive.CreateEntry('content.xml')
            $writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
            try { $writer.Write($xml) } finally { $writer.Dispose() }
        } finally { $archive.Dispose() }
    }
    $first = Join-Path $fixtureDir '01-ALPHA-ONE.udf'
    $second = Join-Path $fixtureDir '02-BRAVO-TWO.udf'
    New-SyntheticUdf $first 'ALPHA-ONE'
    New-SyntheticUdf $second 'BRAVO-TWO'
    function Get-StableGridHash([int[]]$bounds) {
        $last = ''
        $streak = 0
        for ($attempt = 0; $attempt -lt 35; $attempt++) {
            if ([PackagedGuiWindows]::GetForegroundWindow() -ne $main -or
                [PackagedGuiWindows]::FindOwnedDialog($main) -ne [IntPtr]::Zero) {
                throw 'Unexpected foreground window or modal dialog during synthetic grid acceptance.'
            }
            $current = Get-ZoomRegionSha $bounds
            if ($current -eq $last) { $streak++ } else { $streak = 0 }
            if ($streak -ge 2) { return $current }
            $last = $current
            Start-Sleep -Milliseconds 500
        }
        throw 'Synthetic grid did not settle within 18 seconds; physical acceptance NOT_VERIFIED.'
    }
    function Import-SyntheticFile([string]$path) {
        & java -cp $classes PackagedInputRobot open
        if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+O failed during synthetic import.' }
        $deadline = (Get-Date).AddSeconds(12)
        $chooser = [IntPtr]::Zero
        while ((Get-Date) -lt $deadline) {
            $chooser = [PackagedGuiWindows]::FindOwnedDialog($main)
            if ($chooser -ne [IntPtr]::Zero) { break }
            Start-Sleep -Milliseconds 200
        }
        if ($chooser -eq [IntPtr]::Zero -or [PackagedGuiWindows]::GetForegroundWindow() -ne $chooser) {
            throw 'Synthetic import chooser did not receive physical focus.'
        }
        & java -cp $classes PackagedInputRobot choose $path
        if ($LASTEXITCODE -ne 0) { throw 'Physical synthetic filename entry failed.' }
        $deadline = (Get-Date).AddSeconds(12)
        while ((Get-Date) -lt $deadline) {
            if ([PackagedGuiWindows]::FindOwnedDialog($main) -eq [IntPtr]::Zero -and
                [PackagedGuiWindows]::GetForegroundWindow() -eq $main) { return }
            Start-Sleep -Milliseconds 250
        }
        throw 'Synthetic chooser failed to close or application did not regain focus.'
    }
    $gridRegion = [PackagedGuiWindows]::ZoomTileRegion($main)
    if ($null -eq $gridRegion) { throw 'Synthetic grid region unavailable.' }
    $emptyGrid = Get-StableGridHash $gridRegion
    Import-SyntheticFile $first
    $oneCard = Get-StableGridHash $gridRegion
    if ($oneCard -eq $emptyGrid) { throw 'First synthetic UDF did not visibly enter packaged grid.' }
    Import-SyntheticFile $second
    $twoCards = Get-StableGridHash $gridRegion
    if ($twoCards -eq $oneCard) { throw 'Second synthetic UDF did not visibly enter packaged grid.' }
    & java -cp $classes PackagedInputRobot selectall
    if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+A failed.' }
    $allSelected = Get-StableGridHash $gridRegion
    if ($allSelected -eq $twoCards) { throw 'Physical Ctrl+A did not visibly select the imported cards.' }
    # Plain click collapses multi-selection to the first card, then Alt+Down
    # changes the actual visual queue; Alt+Up must restore the same pixels.
    $firstX = $gridRegion[0] + 90
    $firstY = $gridRegion[1] + 80
    & java -cp $classes PackagedInputRobot focus $firstX $firstY
    if ($LASTEXITCODE -ne 0) { throw 'Physical first-card click failed.' }
    $beforeMove = Get-StableGridHash $gridRegion
    if ($beforeMove -eq $allSelected) { throw 'Plain click did not collapse multi-selection.' }
    & java -cp $classes PackagedInputRobot movedown
    if ($LASTEXITCODE -ne 0) { throw 'Physical Alt+Down failed.' }
    $afterMove = Get-StableGridHash $gridRegion
    if ($afterMove -eq $beforeMove) { throw 'Physical Alt+Down did not visibly reorder the two UDF cards.' }
    & java -cp $classes PackagedInputRobot moveup
    if ($LASTEXITCODE -ne 0) { throw 'Physical Alt+Up failed.' }
    $restoredMove = Get-StableGridHash $gridRegion
    if ($restoredMove -ne $beforeMove) {
        throw 'Physical Alt+Up did not restore original two-card grid pixels.'
    }
    Write-Host 'PACKAGED-SELECTION-REORDER-ROBOT-PASS: two rights-clean synthetic UDFs, Ctrl+A, plain-click collapse, Alt+Down/Up visual order round-trip; no real UYAP source.'
    Write-Host 'PACKAGED-GUI-ROBOT-PASS: ephemeral EXE, physical chooser, theme, zoom, two synthetic imports, multi-selection and keyboard reorder.'

} finally {
    if ($null -ne $process) {
        $process.Refresh()
        if (-not $process.HasExited) {
            & taskkill.exe /PID $process.Id /T /F | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw 'Packaged GUI process tree cleanup failed.'
            }
            $process.WaitForExit(10000) | Out-Null
        }
    }
}
