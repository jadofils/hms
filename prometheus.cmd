@echo off
REM Starts a local Prometheus server (Windows), configured for HMS load testing.
REM Double-click this file, or run it from a terminal. Leave the window open --
REM Prometheus runs in the foreground and streams its own log here; wait for a line
REM like "Server is ready to receive web requests" before running jmeter.cmd/k6.cmd
REM against it. Close this window (or Ctrl+C) to stop the server.
REM
REM Reads PROMETHEUS_HOME from .env (same file the Spring Boot app reads its own
REM secrets from) -- edit that value there, not in this script, if Prometheus is
REM installed somewhere else. Uses docs\v5\load-testing\prometheus.yml (this
REM project's own scrape config), not the install's default prometheus.yml.
REM
REM --web.enable-remote-write-receiver is what lets k6 (hms-load-test.js, via k6.cmd)
REM push metrics directly -- without it Prometheus rejects k6's writes with a 404.
REM JMeter has no such push support, so its metrics reach Prometheus a different way:
REM start docs\v5\load-testing\jmeter_prometheus_exporter.py (needs Python) before or
REM during a jmeter.cmd run, and this config's own "jmeter" scrape job polls it.
REM
REM Once running: http://localhost:9090 is Prometheus's own graphing UI --
REM see docs\v5\prometheus-guide.md for what to actually look at there.

set PROMETHEUS_HOME=
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "PROMETHEUS_HOME=" ".env" 2^>nul`) do set "PROMETHEUS_HOME=%%B"
)

if "%PROMETHEUS_HOME%"=="" (
    echo PROMETHEUS_HOME not found in .env.
    echo Add this line to .env:
    echo   PROMETHEUS_HOME=C:\path\to\your\prometheus-x.x.x.windows-amd64
    pause
    exit /b 1
)

if not exist "%PROMETHEUS_HOME%\prometheus.exe" (
    echo Could not find prometheus.exe under:
    echo   %PROMETHEUS_HOME%
    echo Check the PROMETHEUS_HOME value in .env.
    pause
    exit /b 1
)

echo Starting Prometheus from %PROMETHEUS_HOME% ...
echo Web UI will be at http://localhost:9090 once ready.
echo.
call "%PROMETHEUS_HOME%\prometheus.exe" ^
    --config.file=docs\v5\load-testing\prometheus.yml ^
    --web.enable-remote-write-receiver

echo.
echo Prometheus process ended.
pause
