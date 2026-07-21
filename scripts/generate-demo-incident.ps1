param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ApiKey = "opm_demo_key"
)
$headers = @{"X-OpsMind-Key"=$ApiKey;"Content-Type"="application/json"}
$events = 1..3 | ForEach-Object {
  @{
    eventId = "demo-$([guid]::NewGuid())"
    occurredAt = [DateTime]::UtcNow.ToString("o")
    level = "ERROR"
    message = "Database connection timeout while processing payment request $_"
    traceId = "demo-trace-$_"
    host = "payment-api-$_"
    attributes = @{region="local"; release="2026.07-demo"}
  }
}
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/ingestion/logs" -Headers $headers -Body ($events | ConvertTo-Json -Depth 5)
Write-Output "Demo logs accepted. Open http://localhost:5173/incidents"
