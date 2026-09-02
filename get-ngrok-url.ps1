# Prints the current public HTTPS URL of the running ngrok tunnel, by querying
# ngrok's own local API (its "web inspector", always on 127.0.0.1:4040 while ngrok is
# running). Called from start-ngrok-tunnel.cmd -- kept as its own file rather than an
# inline one-liner in the .cmd, since embedding this much PowerShell (nested quotes,
# $_ variables) directly inside a batch `for /f` backtick command turned out too
# fragile to parse reliably.
try {
    $tunnels = (Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 5).tunnels
    $https = $tunnels | Where-Object { $_.proto -eq "https" } | Select-Object -First 1
    if ($https) {
        Write-Output $https.public_url
    }
} catch {
    # Silent -- start-ngrok-tunnel.cmd treats an empty result as "couldn't read it
    # automatically" and falls back to telling the user to check the inspector UI.
}
