# Export U01-U13 HTML wireframes to PNG (2400x1500).
# Usage: powershell -ExecutionPolicy Bypass -File .\_render.ps1
$ErrorActionPreference = "Stop"

$edge = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if (-not (Test-Path $edge)) { $edge = "C:\Program Files\Google\Chrome\Application\chrome.exe" }
if (-not (Test-Path $edge)) { throw "Edge or Chrome not found." }

$root = $PSScriptRoot
$tmp = Join-Path $root "__shot_tmp.png"

Get-ChildItem -Path $root -Filter "U*.html" | Sort-Object Name | ForEach-Object {
    $name = $_.BaseName
    $target = Join-Path $root ($name + ".png")
    $uri = ([uri]$_.FullName).AbsoluteUri

    if (Test-Path $target) { Remove-Item $target -Force }
    if (Test-Path $tmp) { Remove-Item $tmp -Force }

    $shotArg = '--screenshot="{0}"' -f $tmp
    Start-Process -FilePath $edge -Wait -NoNewWindow -ErrorAction SilentlyContinue -ArgumentList @(
        "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--force-device-scale-factor=2", "--window-size=1200,750",
        "--no-first-run", "--no-default-browser-check",
        $shotArg, $uri
    )

    Start-Sleep -Milliseconds 300
    if (Test-Path $tmp) {
        Move-Item $tmp $target -Force
        Write-Output ("ok: " + $name)
    } else {
        Write-Warning ("failed: " + $name)
    }
}

if (Test-Path $tmp) { Remove-Item $tmp -Force }
