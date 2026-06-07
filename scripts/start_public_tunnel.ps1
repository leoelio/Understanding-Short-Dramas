$ErrorActionPreference = "Stop"

$CloudflaredPath = "D:\tools\cloudflared\cloudflared.exe"
$TargetUrl = "http://127.0.0.1:8000"
$LogDir = "D:\tools\cloudflared"
$OutLog = Join-Path $LogDir "banju-tunnel.out.log"
$ErrLog = Join-Path $LogDir "banju-tunnel.err.log"

if (!(Test-Path $CloudflaredPath)) {
  throw "cloudflared not found at $CloudflaredPath"
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Remove-Item -LiteralPath $OutLog, $ErrLog -Force -ErrorAction SilentlyContinue

$existing = Get-Process cloudflared -ErrorAction SilentlyContinue
if ($existing) {
  Write-Host "Existing cloudflared process:"
  $existing | Select-Object Id, ProcessName, StartTime
  $text = ""
  if (Test-Path $OutLog) { $text += "`n" + (Get-Content -LiteralPath $OutLog -Raw -ErrorAction SilentlyContinue) }
  if (Test-Path $ErrLog) { $text += "`n" + (Get-Content -LiteralPath $ErrLog -Raw -ErrorAction SilentlyContinue) }
  $match = [regex]::Match($text, "https://[a-z0-9-]+\.trycloudflare\.com")
  if ($match.Success) {
    Write-Host "Public URL: $($match.Value)"
  }
  Write-Host "Stop it first with scripts\stop_public_tunnel.ps1 if you need a new URL."
  exit 0
}

$process = Start-Process `
  -FilePath $CloudflaredPath `
  -ArgumentList @("tunnel", "--url", $TargetUrl, "--no-autoupdate") `
  -RedirectStandardOutput $OutLog `
  -RedirectStandardError $ErrLog `
  -WindowStyle Hidden `
  -PassThru

Write-Host "Started cloudflared PID $($process.Id). Waiting for quick tunnel URL..."

for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 1
  $text = ""
  if (Test-Path $OutLog) { $text += "`n" + (Get-Content -LiteralPath $OutLog -Raw -ErrorAction SilentlyContinue) }
  if (Test-Path $ErrLog) { $text += "`n" + (Get-Content -LiteralPath $ErrLog -Raw -ErrorAction SilentlyContinue) }
  $match = [regex]::Match($text, "https://[a-z0-9-]+\.trycloudflare\.com")
  if ($match.Success) {
    Write-Host "Public URL: $($match.Value)"
    exit 0
  }
}

Write-Host "Tunnel started, but URL was not found yet. Check logs:"
Write-Host $OutLog
Write-Host $ErrLog
