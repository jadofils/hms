@echo off
setlocal enabledelayedexpansion
REM Single-click startup for the public ngrok tunnel on top of the already-running LAN
REM deployment (see docs/deployment-guide.md -- run start-hms-server.cmd first, this
REM script refuses to continue if the app isn't already up). See
REM docs/ngrok-deployment-guide.md for the full explanation of what this does and why.
REM
REM Starts, in order: graphql-block-proxy.py (blocks /graphql, /graphiql from the
REM public tunnel -- GraphQL resolvers have zero authorization, see CLAUDE.md) -> ngrok
REM itself, pointed at the proxy, never at the app's own port directly -> prints the
REM live public URL by querying ngrok's own local API.
REM
REM Reads NGROK_HOME from .env (same convention as TOMCAT_HOME/K6_HOME/etc.).

set NGROK_HOME=
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "NGROK_HOME=" ".env" 2^>nul`) do set "NGROK_HOME=%%B"
)
if "%NGROK_HOME%"=="" (
    echo NGROK_HOME not found in .env.
    echo Add this line to .env:
    echo   NGROK_HOME=C:\path\to\the\folder\containing\ngrok.exe
    pause
    exit /b 1
)
if not exist "%NGROK_HOME%\ngrok.exe" (
    echo Could not find ngrok.exe under:
    echo   %NGROK_HOME%
    echo Check the NGROK_HOME value in .env, or see docs/ngrok-deployment-guide.md's
    echo Step 1 to download it.
    pause
    exit /b 1
)

echo ============================================================
echo  HMS Public Tunnel Startup
echo ============================================================

REM ---- Step 1: the app itself must already be running -------------------------
echo.
echo [1/3] Checking the app is already up (port 8080)...
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul
if errorlevel 1 (
    echo The app is not running yet. Run start-hms-server.cmd first, then re-run this
    echo script once http://localhost:8080/ answers.
    pause
    exit /b 1
)
echo App is up.

REM ---- Step 2: the blocking proxy ----------------------------------------------
echo.
echo [2/3] Checking the GraphQL-blocking proxy (port 8081)...
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo Proxy is already running.
    goto proxy_done
)
where python >nul 2>nul
if errorlevel 1 (
    echo python was not found on PATH -- it's required to run graphql-block-proxy.py.
    echo Install Python, or run the proxy some other way, then re-run this script.
    pause
    exit /b 1
)
echo Starting the proxy in its own window...
start "HMS GraphQL-Block Proxy" cmd /k python graphql-block-proxy.py
echo Waiting for it to come up...
set /a PROXY_TRIES=0
:proxy_wait_loop
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if not errorlevel 1 goto proxy_done
set /a PROXY_TRIES+=1
if !PROXY_TRIES! GEQ 10 (
    echo Proxy did not start within 10 seconds -- check its window for errors.
    pause
    exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto proxy_wait_loop
:proxy_done

REM ---- Step 3: ngrok itself -----------------------------------------------------
echo.
echo [3/3] Checking ngrok...
netstat -ano | findstr ":4040" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo ngrok is already running.
    goto ngrok_done
)
echo Starting ngrok in its own window (tunneling port 8081, the proxy -- not 8080
echo directly, so the GraphQL block stays in effect)...
start "HMS ngrok Tunnel" cmd /k "%NGROK_HOME%\ngrok.exe" http 8081
echo Waiting for the tunnel to establish...
set /a NGROK_TRIES=0
:ngrok_wait_loop
curl -s -m 2 http://127.0.0.1:4040/api/tunnels 2>nul | findstr "public_url" >nul
if not errorlevel 1 goto ngrok_done
set /a NGROK_TRIES+=1
if !NGROK_TRIES! GEQ 15 (
    echo ngrok did not establish a tunnel within 15 seconds -- check its window for
    echo errors ^(a common one: authtoken not configured -- see
    echo docs/ngrok-deployment-guide.md Step 2^).
    pause
    exit /b 1
)
ping -n 2 127.0.0.1 >nul
goto ngrok_wait_loop
:ngrok_done

echo.
echo ============================================================
echo  Fetching the live public URL...
echo ============================================================
set "PUBLIC_URL="
for /f "usebackq delims=" %%U in (`powershell -NoProfile -ExecutionPolicy Bypass -File "get-ngrok-url.ps1"`) do set "PUBLIC_URL=%%U"

if "%PUBLIC_URL%"=="" (
    echo Could not read the public URL automatically -- open
    echo http://127.0.0.1:4040 in a browser to see it ^(ngrok's own local inspector^).
) else (
    echo.
    echo  Public URL:        %PUBLIC_URL%
    echo  Swagger UI:        %PUBLIC_URL%/
    echo  Login ^(POST^):      %PUBLIC_URL%/api/v1/auth/login
    echo  GraphQL:           blocked ^(404^) by design -- see docs/ngrok-deployment-guide.md
    echo  Local inspector:   http://127.0.0.1:4040  ^(replay/inspect every tunneled request^)
    echo.
    start "" "%PUBLIC_URL%/"
)
echo ============================================================
pause
