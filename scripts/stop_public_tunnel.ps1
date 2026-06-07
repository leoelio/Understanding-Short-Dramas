$processes = Get-Process cloudflared -ErrorAction SilentlyContinue
if (!$processes) {
  Write-Host "No cloudflared process is running."
  exit 0
}

$processes | Select-Object Id, ProcessName, StartTime
$processes | Stop-Process -Force
Write-Host "Stopped public tunnel."
