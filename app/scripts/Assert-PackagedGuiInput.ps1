#requires -Version 7.0
<#
.SYNOPSIS
  Opt-in physical Windows Robot acceptance of the ephemeral jpackage EXE.
.DESCRIPTION
  The target must be the launched app-image's visible, enabled MVP JFrame.
  Physical mouse focus, Ctrl+O/Escape and Ctrl+T are delivered by java.awt.Robot
  in a separate JVM. The chooser must close and the empty grid background must
  visibly change and restore across theme and thumbnail-zoom round-trips. No UDF is imported,
  only temporary rights-clean synthetic UDF/PDF fixtures are used; nothing is published.
#>
[CmdletBinding()]
param(
    [switch]$AllowGui,
    [switch]$JdiRenderRace,
    [string]$ExePath
)
$ErrorActionPreference = 'Stop'
if (-not $AllowGui -or -not $IsWindows) {
    throw 'Packaged physical Robot requires explicit -AllowGui on Windows.'
}
$exe = Join-Path $env:RUNNER_TEMP 'embasic-app-image/EmbasicPublicMvp/EmbasicPublicMvp.exe'
if ($JdiRenderRace) {
    if ([string]::IsNullOrWhiteSpace($ExePath)) {
        throw 'JDI render race requires an explicitly separate ephemeral test EXE.'
    }
    $testRoot = Join-Path $env:RUNNER_TEMP 'embasic-jdi-app-image'
    $resolvedExe = (Resolve-Path -LiteralPath $ExePath -ErrorAction Stop).Path
    $resolvedRoot = (Resolve-Path -LiteralPath $testRoot -ErrorAction Stop).Path
    if (-not $resolvedExe.StartsWith($resolvedRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase) -or
        -not $resolvedExe.EndsWith('EmbasicJdiRace.exe', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Debug acceptance may only launch the isolated CI-only jpackage image.'
    }
    $exe = $resolvedExe
} elseif (-not [string]::IsNullOrWhiteSpace($ExePath)) {
    throw 'An alternate EXE is permitted only in the explicitly enabled JDI test.'
}
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

    public static string WindowTitle(IntPtr hwnd) {
        var title = new StringBuilder(256);
        if (hwnd == IntPtr.Zero) return "";
        GetWindowText(hwnd, title, title.Capacity);
        return title.ToString();
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
$raceProcess = $null
try {
    if ($JdiRenderRace) {
        # Capture only ephemeral test-JVM launcher diagnostics to distinguish
        # missing modules, agent startup errors and unavailable GUI sessions.
        $debugStdOut = Join-Path $env:RUNNER_TEMP 'embasic-tur244-app-stdout.log'
        $debugStdErr = Join-Path $env:RUNNER_TEMP 'embasic-tur244-app-stderr.log'
        $process = Start-Process -FilePath $exe -PassThru -ErrorAction Stop -RedirectStandardOutput $debugStdOut -RedirectStandardError $debugStdErr
    } else {
        $process = Start-Process -FilePath $exe -PassThru -ErrorAction Stop
    }
    if ($process.SessionId -ne [System.Diagnostics.Process]::GetCurrentProcess().SessionId) {
        throw 'Packaged EXE is in another Windows session; GUI NOT_VERIFIED.'
    }
    $deadline = (Get-Date).AddSeconds(30)
    $main = [IntPtr]::Zero
    $ids = [System.Collections.Generic.List[uint32]]::new()
    while ((Get-Date) -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) {
            if ($JdiRenderRace) {
                $sanitized = ''
                foreach ($logFile in @($debugStdErr, $debugStdOut)) {
                    if (Test-Path -LiteralPath $logFile) {
                        $raw = (Get-Content -LiteralPath $logFile -Raw -ErrorAction SilentlyContinue)
                        if ($raw) {
                            $raw = $raw.Replace($env:RUNNER_TEMP, '<TEMP>')
                            $sanitized += $raw.Substring(0, [Math]::Min(500, $raw.Length))
                        }
                    }
                }
                Write-Warning ('TEST_ONLY_JDWP_LAUNCH_DIAGNOSTIC exit=' + $process.ExitCode + ' message=' + $sanitized)
            }
            throw 'Packaged EXE exited before GUI acceptance.'
        }
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
    $fixtureDir = Join-Path $env:RUNNER_TEMP $(if ($JdiRenderRace) {
        'embasic-tur244-jdi-robot-fixtures'
    } else {
        'embasic-tur214-robot-fixtures'
    })
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
    # TUR216: make the same physical keyboard reorder visible in the actual
    # saved PDF, not merely in screenshots of the Swing cards.
    & java -cp $classes PackagedInputRobot movedown
    if ($LASTEXITCODE -ne 0) { throw 'Physical Alt+Down before PDF save failed.' }
    $pdfOrderGrid = Get-StableGridHash $gridRegion
    if ($pdfOrderGrid -eq $beforeMove) { throw 'Physical PDF-order preparation did not move the first card.' }
    $outputDir = Join-Path $env:RUNNER_TEMP $(if ($JdiRenderRace) {
        'embasic-tur244-jdi-robot-output'
    } else {
        'embasic-tur216-robot-output'
    })
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    $outputPdf = Join-Path $outputDir 'tur216-reordered.pdf'
    if (Test-Path -LiteralPath $outputPdf) {
        throw 'TUR216 output unexpectedly exists before first physical save.'
    }
    function Wait-DialogTitle([string]$expected, [int]$seconds) {
        $deadline = (Get-Date).AddSeconds($seconds)
        while ((Get-Date) -lt $deadline) {
            $owned = [PackagedGuiWindows]::FindOwnedDialog($main)
            if ($owned -ne [IntPtr]::Zero) {
                $title = [PackagedGuiWindows]::WindowTitle($owned)
                if ($title -eq $expected -and
                    [PackagedGuiWindows]::GetForegroundWindow() -eq $owned) {
                    return $owned
                }
                if ($title -ne $expected -and $title -ne '') {
                    throw "Unexpected packaged modal title while waiting for $expected : $title"
                }
            }
            Start-Sleep -Milliseconds 200
        }
        throw "Packaged modal $expected not physically observed within $seconds seconds."
    }
    function Wait-NoOwnedDialog([int]$seconds) {
        $deadline = (Get-Date).AddSeconds($seconds)
        while ((Get-Date) -lt $deadline) {
            if ([PackagedGuiWindows]::FindOwnedDialog($main) -eq [IntPtr]::Zero -and
                [PackagedGuiWindows]::GetForegroundWindow() -eq $main) { return }
            Start-Sleep -Milliseconds 200
        }
        throw 'Packaged modal did not close or main window did not regain physical focus.'
    }
    & java -cp $classes PackagedInputRobot save
    if ($LASTEXITCODE -ne 0) { throw 'Physical Ctrl+S failed.' }
    $saveChooser = Wait-DialogTitle 'PDF olarak kaydet' 15
    & java -cp $classes PackagedInputRobot choosepdf $outputPdf
    if ($LASTEXITCODE -ne 0) { throw 'Physical PDF filename selection failed.' }
    $deadline = (Get-Date).AddSeconds(50)
    $savedDialog = [IntPtr]::Zero
    while ((Get-Date) -lt $deadline) {
        $owned = [PackagedGuiWindows]::FindOwnedDialog($main)
        if ($owned -ne [IntPtr]::Zero) {
            $title = [PackagedGuiWindows]::WindowTitle($owned)
            if ($title -eq 'İşlem tamamlandı' -and
                [PackagedGuiWindows]::GetForegroundWindow() -eq $owned -and
                (Test-Path -LiteralPath $outputPdf -PathType Leaf)) {
                $savedDialog = $owned
                break
            }
            if ($title -ne '' -and $title -ne 'PDF olarak kaydet' -and
                $title -ne 'İşlem tamamlandı') {
                throw "Unexpected save modal: $title"
            }
        }
        Start-Sleep -Milliseconds 250
    }
    if ($savedDialog -eq [IntPtr]::Zero) {
        throw 'No physical PDF success dialog and output; Ctrl+S acceptance NOT_VERIFIED.'
    }
    & java -cp $classes PackagedInputRobot cancel
    if ($LASTEXITCODE -ne 0) { throw 'Physical PDF success dialog dismissal failed.' }
    Wait-NoOwnedDialog 10
    $shadedJar = Join-Path (Get-Location).Path 'app/target/embasic-public-desktop-0.1.0-SNAPSHOT-all.jar'
    if (-not (Test-Path -LiteralPath $shadedJar -PathType Leaf)) {
        throw 'Local test classpath shaded JAR missing; PDF order NOT_VERIFIED.'
    }
    & javac -cp $shadedJar -d $classes app/scripts/PackagedPdfOrderProbe.java
    if ($LASTEXITCODE -ne 0) { throw 'Physical PDF verifier failed to compile.' }
    & java -cp "$classes;$shadedJar" PackagedPdfOrderProbe $outputPdf
    if ($LASTEXITCODE -ne 0) { throw 'Physical PDF page order/geometry verification failed.' }
    $approvedSha = (Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash

    # Existing output: user declines overwrite using real Windows keyboard.
    # The previously accepted PDF must remain byte-for-byte unchanged.
    & java -cp $classes PackagedInputRobot save
    if ($LASTEXITCODE -ne 0) { throw 'Physical second Ctrl+S failed.' }
    $secondChooser = Wait-DialogTitle 'PDF olarak kaydet' 15
    & java -cp $classes PackagedInputRobot choosepdf $outputPdf
    if ($LASTEXITCODE -ne 0) { throw 'Physical existing-PDF selection failed.' }
    $overwriteDialog = Wait-DialogTitle 'Dosya mevcut' 15
    & java -cp $classes PackagedInputRobot cancel
    if ($LASTEXITCODE -ne 0) { throw 'Physical overwrite refusal failed.' }
    Wait-NoOwnedDialog 10
    $afterRefusalSha = (Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash
    if ($afterRefusalSha -ne $approvedSha) {
        throw 'Previously accepted PDF changed after physical overwrite refusal.'
    }
    Write-Host 'PACKAGED-PDF-OVERWRITE-REFUSAL-PASS: existing synthetic PDF SHA-256 preserved.'

    if ($JdiRenderRace) {
        # TUR244: Only an ephemeral EXE exposes loopback JDWP. Arm a distinct
        # JDI JVM AFTER the approved PDF exists and BEFORE the second Ctrl+S.
        & javac --add-modules jdk.jdi -d $classes app/scripts/PackagedRenderBoundaryController.java
        if ($LASTEXITCODE -ne 0) { throw 'JDI test-only controller compilation failed.' }
        $renderMatches = @(Select-String -LiteralPath 'app/src/main/java/com/embasic/publicapp/PdfExporter.java' -SimpleMatch 'if (!previewOnly) boundary.afterDocument(++completedDocuments);')
        if ($renderMatches.Count -ne 1) {
            throw 'Real renderer source-line identity is ambiguous; JDI NOT_VERIFIED.'
        }
        $breakLine = $renderMatches[0].LineNumber
        $raceReplacement = Join-Path $fixtureDir '02-BRAVO-TWO-jdi-replacement.udf'
        if (Test-Path -LiteralPath $raceReplacement) {
            throw 'JDI synthetic replacement unexpectedly exists.'
        }
        New-SyntheticUdf $raceReplacement 'BRAVO-NEW'
        $beforeRaceSha = (Get-FileHash -LiteralPath $second -Algorithm SHA256).Hash
        $beforeRaceTime = (Get-Item -LiteralPath $second).LastWriteTimeUtc
        if ((Get-FileHash -LiteralPath $raceReplacement -Algorithm SHA256).Hash -eq $beforeRaceSha) {
            throw 'JDI synthetic replacement has the same SHA as source.'
        }
        $raceLog = Join-Path $env:RUNNER_TEMP 'embasic-tur244-jdi-controller.log'
        $raceErrorLog = Join-Path $env:RUNNER_TEMP 'embasic-tur244-jdi-controller.error.log'
        $javaBinary = Join-Path $env:JAVA_HOME 'bin/java.exe'
        $argsJdi = @('--add-modules', 'jdk.jdi', '-cp', ('"' + $classes + '"'),
            'PackagedRenderBoundaryController', '51277', [string]$breakLine,
            ('"' + $second + '"'), ('"' + $raceReplacement + '"'),
            ('"' + $env:RUNNER_TEMP + '"'))
        $raceProcess = Start-Process -FilePath $javaBinary -ArgumentList $argsJdi -NoNewWindow -PassThru -RedirectStandardOutput $raceLog -RedirectStandardError $raceErrorLog -ErrorAction Stop
        $armed = $false
        for ($attempt = 0; $attempt -lt 100; $attempt++) {
            if (Test-Path -LiteralPath $raceLog) {
                $evidence = Get-Content -LiteralPath $raceLog -Raw -ErrorAction SilentlyContinue
                if ($evidence -and $evidence.Contains('EMBASIC_TUR244_JDI_READY')) {
                    $armed = $true
                    break
                }
            }
            $raceProcess.Refresh()
            if ($raceProcess.HasExited) { break }
            Start-Sleep -Milliseconds 200
        }
        if (-not $armed) {
            throw 'JDI did not arm a real render breakpoint; physical race NOT_VERIFIED.'
        }
        if ((Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash -ne $approvedSha) {
            throw 'Approved PDF changed before JDI render race.'
        }
        & java -cp $classes PackagedInputRobot save
        if ($LASTEXITCODE -ne 0) { throw 'JDI physical Ctrl+S failed.' }
        $jdiChooser = Wait-DialogTitle 'PDF olarak kaydet' 15
        & java -cp $classes PackagedInputRobot choosepdf $outputPdf
        if ($LASTEXITCODE -ne 0) { throw 'JDI physical output selection failed.' }
        $jdiOverwrite = Wait-DialogTitle 'Dosya mevcut' 15
        & java -cp $classes PackagedInputRobot confirmyes
        if ($LASTEXITCODE -ne 0) { throw 'JDI physical YES action failed.' }
        $raceErrorDialog = Wait-DialogTitle 'Embasic UDF hatası' 60
        if (-not $raceProcess.WaitForExit(20000) -or $raceProcess.ExitCode -ne 0) {
            throw 'JDI controller did not finish the real render boundary event.'
        }
        $jdiEvidence = Get-Content -LiteralPath $raceLog -Raw -ErrorAction Stop
        if (-not $jdiEvidence.Contains('EMBASIC_TUR244_JDI_RENDER_BOUNDARY_HIT')) {
            throw 'Real JDI breakpoint mutation marker not observed; no physical PASS.'
        }
        if ((Get-FileHash -LiteralPath $second -Algorithm SHA256).Hash -eq $beforeRaceSha) {
            throw 'Synthetic UDF did not mutate while the actual render thread was paused.'
        }
        if ((Get-Item -LiteralPath $second).LastWriteTimeUtc -ne $beforeRaceTime) {
            throw 'Synthetic UDF mtime was not restored after debugger mutation.'
        }
        if ((Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash -ne $approvedSha) {
            throw 'Approved PDF changed after physical render-time source mutation.'
        }
        if (@(Get-ChildItem -LiteralPath $outputDir -Filter '.embasic-pending-*.pdf' -File).Count -ne 0) {
            throw 'Staging leak after real packaged JDI render rejection.'
        }
        & java -cp $classes PackagedInputRobot cancel
        if ($LASTEXITCODE -ne 0) { throw 'JDI real error dialog dismissal failed.' }
        Wait-NoOwnedDialog 10
        if ((Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash -ne $approvedSha) {
            throw 'Approved PDF changed after dismissing JDI error dialog.'
        }
        Write-Host 'PACKAGED-JDWP-RENDER-RACE-PASS: isolated ephemeral Windows EXE, loopback JDI real save render thread breakpoint, independent JVM source mutation, restored mtime, physical overwrite YES and error dialog; original PDF SHA preserved and staging clean.'
    }

    # TUR217: after a successful save, replace one imported synthetic UDF
    # with different rights-clean bytes at the SAME path. Restore its mtime
    # to exercise the source SHA gate rather than relying on timestamps.
    # Physical overwrite consent must never authorize a stale PDF export.
    $sourceShaBefore = (Get-FileHash -LiteralPath $first -Algorithm SHA256).Hash
    $originalSourceTime = (Get-Item -LiteralPath $first).LastWriteTimeUtc
    $replacement = Join-Path $fixtureDir '01-ALPHA-ONE-replacement.udf'
    if (Test-Path -LiteralPath $replacement) {
        throw 'Unexpected pre-existing TUR217 replacement fixture.'
    }
    New-SyntheticUdf $replacement 'ALPHA-TWO'
    [System.IO.File]::Move($replacement, $first, $true)
    [System.IO.File]::SetLastWriteTimeUtc($first, $originalSourceTime)
    $sourceShaAfter = (Get-FileHash -LiteralPath $first -Algorithm SHA256).Hash
    if ($sourceShaAfter -eq $sourceShaBefore) {
        throw 'Synthetic UDF mutation did not change SHA-256; test invalid.'
    }
    if ((Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash -ne $approvedSha) {
        throw 'Accepted PDF changed before stale-source overwrite attempt.'
    }
    $pendingBefore = @(Get-ChildItem -LiteralPath $outputDir -Filter '.embasic-pending-*.pdf' -File)
    if ($pendingBefore.Count -ne 0) {
        throw 'Unexpected staging file before TUR217 physical overwrite test.'
    }
    & java -cp $classes PackagedInputRobot save
    if ($LASTEXITCODE -ne 0) { throw 'Physical stale-source Ctrl+S failed.' }
    $staleChooser = Wait-DialogTitle 'PDF olarak kaydet' 15
    & java -cp $classes PackagedInputRobot choosepdf $outputPdf
    if ($LASTEXITCODE -ne 0) { throw 'Physical stale-source PDF target selection failed.' }
    $staleOverwrite = Wait-DialogTitle 'Dosya mevcut' 15
    & java -cp $classes PackagedInputRobot confirmyes
    if ($LASTEXITCODE -ne 0) { throw 'Physical overwrite YES input failed.' }
    # This is an error path: a success dialog or silent dismissal is a failure.
    $sourceError = Wait-DialogTitle 'Embasic UDF hatası' 45
    if ((Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash -ne $approvedSha) {
        throw 'Accepted PDF was modified after changed-source overwrite consent.'
    }
    if (@(Get-ChildItem -LiteralPath $outputDir -Filter '.embasic-pending-*.pdf' -File).Count -ne 0) {
        throw 'PDF staging file leaked after changed-source rejection.'
    }
    & java -cp $classes PackagedInputRobot cancel
    if ($LASTEXITCODE -ne 0) { throw 'Physical changed-source error dismissal failed.' }
    Wait-NoOwnedDialog 10
    if ((Get-FileHash -LiteralPath $outputPdf -Algorithm SHA256).Hash -ne $approvedSha) {
        throw 'Accepted PDF changed after changed-source error dismissal.'
    }
    Write-Host 'PACKAGED-STALE-SOURCE-OVERWRITE-REJECTED-PASS: same-path synthetic UDF SHA changed, physical YES, error dialog, original PDF SHA preserved, no staging leak.'


    Write-Host 'PACKAGED-GUI-ROBOT-PASS: ephemeral EXE, physical chooser, theme, zoom, two synthetic imports, multi-selection and keyboard reorder and Ctrl+S PDF order.'

} finally {
    if ($null -ne $raceProcess) {
        $raceProcess.Refresh()
        if (-not $raceProcess.HasExited) {
            Stop-Process -Id $raceProcess.Id -Force -ErrorAction SilentlyContinue
            $raceProcess.WaitForExit(5000) | Out-Null
        }
    }
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
