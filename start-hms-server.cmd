@echo off
setlocal enabledelayedexpansion
REM Single-click startup for the externally-deployed HMS server: brings up Docker
REM Desktop (if not already running) -> Postgres, via docker compose -> confirms Redis
REM is up -> starts the external Tomcat instance the app is deployed to (see
REM docs/deployment-guide.md) -> waits for the app itself to answer -> opens Swagger UI.
REM Safe to double-click again while everything is already running -- every step below
REM checks first and skips itself if there's nothing to do.
REM
REM Reads TOMCAT_HOME and DOCKER_DESKTOP_EXE from .env (same convention as
REM k6.cmd/jmeter.cmd/prometheus.cmd).

set TOMCAT_HOME=
set DOCKER_DESKTOP_EXE=
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "TOMCAT_HOME=" ".env" 2^>nul`) do set "TOMCAT_HOME=%%B"
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "DOCKER_DESKTOP_EXE=" ".env" 2^>nul`) do set "DOCKER_DESKTOP_EXE=%%B"
)
if "%TOMCAT_HOME%"=="" (
    echo TOMCAT_HOME not found in .env.
    echo Add this line to .env:
    echo   TOMCAT_HOME=C:\path\to\your\tomcat\install
    pause
    exit /b 1
)
if not exist "%TOMCAT_HOME%\bin\startup.bat" (
    echo Could not find startup.bat under:
    echo   %TOMCAT_HOME%\bin
    echo Check the TOMCAT_HOME value in .env.
    pause
    exit /b 1
)

echo ============================================================
echo  HMS Server Startup
echo ============================================================

REM ---- Step 1: Docker Desktop ------------------------------------------------
echo.
echo [1/4] Checking Docker...
docker info >nul 2>nul
if not errorlevel 1 goto docker_already_up

if "%DOCKER_DESKTOP_EXE%"=="" (
    echo Docker daemon is not responding, and DOCKER_DESKTOP_EXE is not set in .env
    echo to launch it automatically. Start Docker Desktop yourself, wait for it to
    echo finish loading, then re-run this script.
    pause
    exit /b 1
)
echo Docker daemon not responding yet -- launching Docker Desktop...
start "" "%DOCKER_DESKTOP_EXE%"
echo Waiting for the Docker engine to become ready (this can take a minute or two)...
set /a DOCKER_TRIES=0

:docker_wait_loop
docker info >nul 2>nul
if not errorlevel 1 goto docker_ready
set /a DOCKER_TRIES+=1
if !DOCKER_TRIES! GEQ 36 goto docker_timeout
REM timeout.exe refuses to run ("Input redirection is not supported") under several
REM common terminal hosts when stdin isn't a real console -- ping against localhost is
REM this repo's established stand-in for a plain ~5s wait (see start-ngrok-tunnel.cmd).
ping -n 6 127.0.0.1 >nul
goto docker_wait_loop

:docker_timeout
echo Docker still isn't responding after 3 minutes. Check Docker Desktop's own
echo window for errors, then re-run this script once it says it's running.
pause
exit /b 1

:docker_ready
echo Docker is ready.
goto docker_done

:docker_already_up
echo Docker is already running.

:docker_done

REM ---- Step 2: Postgres (docker compose) -------------------------------------
echo.
echo [2/4] Starting Postgres (docker compose up -d)...
docker compose up -d
if errorlevel 1 (
    echo docker compose up -d failed -- see the output above.
    pause
    exit /b 1
)

REM ---- Step 3: Redis --------------------------------------------------------
REM Not part of this project's compose.yaml -- a separately-managed container with
REM its own "unless-stopped" restart policy, so it normally comes back on its own the
REM moment the Docker engine itself starts. This just confirms that actually happened
REM and nudges it if not, rather than assuming.
echo.
echo [3/4] Checking Redis...
docker ps --filter "name=my-redis" --filter "status=running" --format "{{.Names}}" | findstr /b "my-redis" >nul
if not errorlevel 1 goto redis_done
echo Redis container not running -- attempting to start it...
docker start my-redis >nul 2>nul
if errorlevel 1 (
    echo Could not start a "my-redis" container automatically -- it may not exist
    echo on this machine yet. See docs/deployment-guide.md's Redis section.
    pause
    exit /b 1
)
:redis_done
echo Redis is running.

REM ---- Step 4: Tomcat ---------------------------------------------------------
echo.
echo [4/4] Starting Tomcat...
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if errorlevel 1 goto start_tomcat
echo Something is already listening on port 8080 -- assuming HMS is already up.
goto tomcat_done

:start_tomcat
REM catalina.bat's own CATALINA_HOME auto-detection is based on the current working
REM directory, not on where the script itself lives -- since our CWD here is the repo
REM root, not "%TOMCAT_HOME%\bin", that auto-detection fails ("The CATALINA_HOME
REM environment variable is not defined correctly") unless we set it explicitly first.
set "CATALINA_HOME=%TOMCAT_HOME%"
set "CATALINA_BASE=%TOMCAT_HOME%"
call "%TOMCAT_HOME%\bin\startup.bat"
echo Waiting for the application to finish deploying inside Tomcat...
set /a APP_TRIES=0

:app_wait_loop
curl -s -o nul -w "%%{http_code}" http://localhost:8080/actuator/health 2>nul | findstr "200" >nul
if not errorlevel 1 goto tomcat_done
set /a APP_TRIES+=1
if !APP_TRIES! GEQ 24 goto app_timeout
REM timeout.exe refuses to run ("Input redirection is not supported") under several
REM common terminal hosts when stdin isn't a real console -- ping against localhost is
REM this repo's established stand-in for a plain ~5s wait (see start-ngrok-tunnel.cmd).
ping -n 6 127.0.0.1 >nul
goto app_wait_loop

:app_timeout
echo HMS did not answer within 2 minutes -- check
echo %TOMCAT_HOME%\logs\catalina.*.log for errors.
pause
exit /b 1

:tomcat_done

echo.
echo ============================================================
echo  HMS is up.
echo    Swagger UI / API docs:  http://localhost:8080/
echo    On this network:        http://192.168.1.46:8080/
echo ============================================================
start "" "http://localhost:8080/"
pause