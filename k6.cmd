@echo off
REM Runs the HMS k6 load test (docs\v5\load-testing\hms-load-test.js) against a
REM locally running instance, pushing metrics to Prometheus via remote-write as it
REM runs (start prometheus.cmd first, then watch http://localhost:9090/graph -- see
REM docs\v5\prometheus-guide.md). Double-click this file, or run it from a terminal.
REM Requires:
REM   - The app already running (./mvnw spring-boot:run) and reachable on localhost:8080.
REM   - k6 installed locally (K6_HOME in .env pointing at the folder containing k6.exe).
REM
REM Reads K6_HOME from .env (same file the Spring Boot app reads its own secrets from)
REM -- edit that value there, not in this script, if k6 is installed somewhere else.
REM Fetches a fresh admin JWT and a real patient/doctor id itself on every run (see
REM docs\v5\load-testing\fetch-test-params.ps1) -- a JWT expires, so this can't be a
REM static .env value the way K6_HOME is.
REM
REM If Prometheus isn't running when this starts, k6 just logs a failed-push warning
REM per flush and carries on with the test -- it doesn't stop the run.
REM
REM k6's own live web-dashboard output (a second, alternative way to watch a run)
REM turned out unreliable on this machine specifically -- port 9092 consistently
REM failed to bind for reasons that were never root-caused, and worse, that failure
REM aborted the ENTIRE run (0 requests made), not just the dashboard. Deliberately
REM left out for that reason; Prometheus is the live-view mechanism this script uses.
REM
REM Optional overrides (set as env vars before running): VUS (default 50),
REM RAMP_UP/STEADY/RAMP_DOWN (default 10s/40s/10s)

set K6_HOME=
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "K6_HOME=" ".env" 2^>nul`) do set "K6_HOME=%%B"
)

if "%K6_HOME%"=="" (
    echo K6_HOME not found in .env.
    echo Add this line to .env:
    echo   K6_HOME=C:\path\to\your\k6\install\folder
    pause
    exit /b 1
)

if not exist "%K6_HOME%\k6.exe" (
    echo Could not find k6.exe under:
    echo   %K6_HOME%
    echo Check the K6_HOME value in .env.
    pause
    exit /b 1
)

if "%VUS%"=="" set VUS=50
if "%RAMP_UP%"=="" set RAMP_UP=10s
if "%STEADY%"=="" set STEADY=40s
if "%RAMP_DOWN%"=="" set RAMP_DOWN=10s
set APP_BASE_URL=http://localhost:8080

set PARAMS_FILE=%TEMP%\hms_load_test_params.txt
echo Fetching a fresh admin token and real patient/doctor ids from %APP_BASE_URL% ...
powershell -NoProfile -ExecutionPolicy Bypass -File "docs\v5\load-testing\fetch-test-params.ps1" -BaseUrl %APP_BASE_URL% -OutFile "%PARAMS_FILE%"
if errorlevel 1 (
    echo Could not fetch a token/patient id/doctor id -- see the error above.
    pause
    exit /b 1
)

set AUTH_TOKEN=
set PATIENT_ID=
set DOCTOR_ID=
for /f "usebackq delims=" %%T in ("%PARAMS_FILE%") do if not defined AUTH_TOKEN set "AUTH_TOKEN=%%T"
for /f "usebackq skip=1 delims=" %%P in ("%PARAMS_FILE%") do if not defined PATIENT_ID set "PATIENT_ID=%%P"
for /f "usebackq skip=2 delims=" %%D in ("%PARAMS_FILE%") do if not defined DOCTOR_ID set "DOCTOR_ID=%%D"

set RESULTS_DIR=target\load-test-results
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

echo Prometheus view (if prometheus.cmd is running): http://localhost:9090/graph
echo Running k6: %VUS% VUs, stages %RAMP_UP%/%STEADY%/%RAMP_DOWN%...
call "%K6_HOME%\k6.exe" run --out experimental-prometheus-rw ^
    --env BASE_URL=%APP_BASE_URL% --env AUTH_TOKEN=%AUTH_TOKEN% ^
    --env PATIENT_ID=%PATIENT_ID% --env DOCTOR_ID=%DOCTOR_ID% --env VUS=%VUS% ^
    --env RAMP_UP=%RAMP_UP% --env STEADY=%STEADY% --env RAMP_DOWN=%RAMP_DOWN% ^
    docs\v5\load-testing\hms-load-test.js
set K6_EXIT=%errorlevel%

echo.
if %K6_EXIT% neq 0 (
    echo k6 exited with code %K6_EXIT% -- this is EXPECTED if a threshold was crossed
    echo ^(e.g. p95 latency ^> 1000ms^), not necessarily a script failure. Check the
    echo THRESHOLDS section printed above.
)
echo See docs\v5\k6-guide.md for how to read this output, and docs\v5\prometheus-guide.md
echo for the Prometheus side.
pause
