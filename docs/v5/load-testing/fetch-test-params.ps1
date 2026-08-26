# Fetches a fresh admin JWT plus one real patient id and one real doctor id from a
# running HMS instance, and writes them one-per-line to a text file. Shared by both
# jmeter.cmd and k6.cmd (repo root) -- a JWT expires and ids are seed-data-dependent,
# so neither can be a static .env value the way JMETER_HOME/K6_HOME are.
#
# Not meant to be run by hand normally -- jmeter.cmd/k6.cmd call this automatically on
# every run. Direct use: powershell -File fetch-test-params.ps1 -BaseUrl http://localhost:8080 -OutFile out.txt
#
# Plain ASCII only in this file, deliberately -- Windows PowerShell 5.1 reads a .ps1
# file with no byte-order-mark using the system ANSI codepage, not UTF-8. A non-ASCII
# character (an em-dash was the one that actually broke this) gets misread and can
# corrupt parsing/execution further down the file in ways that are very hard to
# diagnose (this cost real debugging time to track down -- confirmed by bisecting a
# minimal reproduction: the exact same script with a real em-dash character anywhere
# in it, even inside a string in a catch block that never runs, made the later
# Bearer-token header silently end up empty and every authenticated call 401 instead
# of a token). Keep it that way rather than relying on this or any other editor to
# preserve a BOM.

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "Admin@123",
    [string]$OutFile = "$env:TEMP\hms_load_test_params.txt"
)

$ErrorActionPreference = "Stop"

try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
    if ($health.status -ne "UP") {
        throw "App reports status '$($health.status)', not UP."
    }
} catch {
    Write-Error "Could not reach $BaseUrl/actuator/health -- is the app running (./mvnw spring-boot:run)? $($_.Exception.Message)"
    exit 1
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $login.data.token
if (-not $token) {
    Write-Error "Login succeeded but no token came back -- unexpected response shape."
    exit 1
}

$headers = @{ Authorization = "Bearer $token" }
$patients = Invoke-RestMethod -Uri "$BaseUrl/api/v1/patients?page=0&size=1" -Headers $headers
$doctors = Invoke-RestMethod -Uri "$BaseUrl/api/v1/doctors?page=0&size=1" -Headers $headers
$patientId = $patients.data.content[0].patientId
$doctorId = $doctors.data.content[0].doctorId

if (-not $patientId) {
    Write-Error "No patient id found -- is there at least one seeded/created patient?"
    exit 1
}
if (-not $doctorId) {
    Write-Error "No doctor id found -- is there at least one seeded/created doctor?"
    exit 1
}

Set-Content -Path $OutFile -Value $token -Encoding ascii
Add-Content -Path $OutFile -Value $patientId -Encoding ascii
Add-Content -Path $OutFile -Value $doctorId -Encoding ascii

Write-Host "Token + patient/doctor ids written to $OutFile"
