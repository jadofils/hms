@echo off
REM Runs the HMS k6 full-user-journey load test
REM (docs\v5\load-testing\hms-user-journey-test.js) against a locally running instance --
REM see that file's own header comment for how this differs from k6.cmd's test (that one
REM shares a single pre-fetched admin token across every VU to isolate post-auth API
REM throughput; this one has every VU log in, act, and log out for itself every
REM iteration, using DataSeeder's 5 seeded demo accounts round-robined by VU number, so
REM no new User rows are ever created).
REM
REM Double-click this file, or run it from a terminal. Requires:
REM   - The app already running and reachable on localhost:8080.
REM   - k6 installed locally (K6_HOME in .env pointing at the folder containing k6.exe).
REM
REM Reads K6_HOME from .env (same convention as k6.cmd).
REM
REM Optional overrides (set as env vars before running): VUS (default 100),
REM RAMP_UP/STEADY/RAMP_DOWN (default 10s/40s/10s).
REM
REM RateLimitFilter enforces a global per-client-IP request limit ahead of every
REM endpoint (100 req/60s by default) -- every VU here shares this one machine's IP, and
REM every iteration touches /auth/login too, so this blows through that budget almost
REM immediately unless APP_RATE_LIMIT_ENABLED=false (or a much higher
REM APP_RATE_LIMIT_MAX_REQUESTS) is set in .env before running. Restore it afterward.

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

if "%VUS%"=="" set VUS=100
if "%RAMP_UP%"=="" set RAMP_UP=10s
if "%STEADY%"=="" set STEADY=40s
if "%RAMP_DOWN%"=="" set RAMP_DOWN=10s
set APP_BASE_URL=http://localhost:8080

echo ============================================================
echo  HMS k6 User Journey Test
echo  (every VU logs in for itself, acts, logs out -- see the .js
echo   file's header comment for why this is a separate script)
echo ============================================================
echo Reminder: set APP_RATE_LIMIT_ENABLED=false in .env before this run, or every VU's
echo login will start 429ing almost immediately -- see this script's own header comment.
echo.
echo Running k6: %VUS% VUs, stages %RAMP_UP%/%STEADY%/%RAMP_DOWN%...
call "%K6_HOME%\k6.exe" run ^
    --env BASE_URL=%APP_BASE_URL% --env VUS=%VUS% ^
    --env RAMP_UP=%RAMP_UP% --env STEADY=%STEADY% --env RAMP_DOWN=%RAMP_DOWN% ^
    docs\v5\load-testing\hms-user-journey-test.js
set K6_EXIT=%errorlevel%

echo.
if %K6_EXIT% neq 0 (
    echo k6 exited with code %K6_EXIT% -- this is EXPECTED if a threshold was crossed
    echo ^(e.g. p95 latency ^> 1000ms^), not necessarily a script failure. Check the
    echo THRESHOLDS section printed above.
)
echo See docs\v5\k6-guide.md for how to read this output.
pause
