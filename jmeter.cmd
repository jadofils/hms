@echo off
REM Runs the HMS JMeter load test (docs\v5\load-testing\hms-load-test.jmx) against a
REM locally running instance. Double-click this file, or run it from a terminal.
REM Requires:
REM   - The app already running (./mvnw spring-boot:run) and reachable on localhost:8080.
REM   - JMeter installed locally (JMETER_HOME in .env pointing at the installation root
REM     -- the folder containing bin\jmeter.bat).
REM
REM Reads JMETER_HOME from .env (same file the Spring Boot app reads its own secrets
REM from) -- edit that value there, not in this script, if JMeter is installed
REM somewhere else. Fetches a fresh admin JWT and a real patient/doctor id itself on
REM every run (see docs\v5\load-testing\fetch-test-params.ps1) -- a JWT expires, so
REM this can't be a static .env value the way JMETER_HOME is.
REM
REM Also starts docs\v5\load-testing\jmeter_prometheus_exporter.py in its own window
REM (needs Python, already on PATH -- see docs\v5\prometheus-guide.md) so JMeter's
REM results are visible in Prometheus (start prometheus.cmd first) as they come in,
REM not just after the run finishes. Skipped automatically if python isn't found, or
REM if an exporter is already running on port 9270 (no duplicate windows piling up).
REM
REM Optional overrides (set as env vars before running):
REM   THREADS (default 50), RAMPUP (default 10 seconds), DURATION (default 60 seconds)

set JMETER_HOME=
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "JMETER_HOME=" ".env" 2^>nul`) do set "JMETER_HOME=%%B"
)

if "%JMETER_HOME%"=="" (
    echo JMETER_HOME not found in .env.
    echo Add this line to .env:
    echo   JMETER_HOME=C:\path\to\your\apache-jmeter-5.6.3
    pause
    exit /b 1
)

if not exist "%JMETER_HOME%\bin\jmeter.bat" (
    echo Could not find jmeter.bat under:
    echo   %JMETER_HOME%\bin
    echo Check the JMETER_HOME value in .env.
    pause
    exit /b 1
)

if "%THREADS%"=="" set THREADS=50
if "%RAMPUP%"=="" set RAMPUP=10
if "%DURATION%"=="" set DURATION=60
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
set TIMESTAMP=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

powershell -NoProfile -Command "exit (Test-NetConnection -ComputerName localhost -Port 9270 -InformationLevel Quiet -WarningAction SilentlyContinue)" >nul 2>nul
if errorlevel 1 (
    echo Prometheus exporter already running on localhost:9270 -- not starting another.
) else (
    where python >nul 2>nul
    if errorlevel 1 (
        echo python not found on PATH -- skipping the Prometheus exporter. JMeter results
        echo will still be written to %RESULTS_DIR% normally.
    ) else (
        echo Starting the JMeter-to-Prometheus exporter in a new window ^(localhost:9270/metrics^)...
        start "JMeter Prometheus Exporter" python docs\v5\load-testing\jmeter_prometheus_exporter.py
    )
)

echo Running JMeter: %THREADS% threads, %RAMPUP%s ramp-up, %DURATION%s duration...
call "%JMETER_HOME%\bin\jmeter.bat" -n -t docs\v5\load-testing\hms-load-test.jmx ^
    -l "%RESULTS_DIR%\results_%TIMESTAMP%.jtl" ^
    -Jthreads=%THREADS% -Jrampup=%RAMPUP% -Jduration=%DURATION% ^
    -JauthToken=%AUTH_TOKEN% -JpatientId=%PATIENT_ID% -JdoctorId=%DOCTOR_ID%
if errorlevel 1 (
    echo JMeter run failed -- see the output above.
    pause
    exit /b 1
)

echo.
echo Done. Raw results: %RESULTS_DIR%\results_%TIMESTAMP%.jtl
echo Prometheus view (if prometheus.cmd is running): http://localhost:9090/graph
echo   -- try the query: jmeter_elapsed_ms_sum / jmeter_requests_total
echo See docs\v5\load-testing-report.md for how to read the raw results, docs\v5\k6-guide.md
echo for the k6 side of the same load test, and docs\v5\prometheus-guide.md for the
echo Prometheus setup.
pause
