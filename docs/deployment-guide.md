# HMS Deployment Guide — External Tomcat, LAN Access

Audience: system administrators standing this server up, and developers who need to
rebuild/redeploy it after a code change. This documents an actual deployment performed
and verified live on this machine — every command below was run for real, not inferred.

---

## 1. What this deployment is

Until now, HMS ran only via Spring Boot's own **embedded** Tomcat (`./mvnw spring-boot:run`
or `java -jar`) — fine for development, but not something a separate server process
survives independently of a terminal window, and not something you'd hand to ops as a
"deploy this WAR" artifact. This pass makes the exact same application deployable to a
standalone **external Apache Tomcat 11** instance, reachable from other machines on the
same network, while leaving the existing embedded-Tomcat dev workflow completely intact —
`./mvnw spring-boot:run` and `java -jar target/hms-0.0.1-SNAPSHOT.war` both still work
exactly as before.

---

## 2. What changed in the codebase

| File | Change | Why |
|---|---|---|
| `pom.xml` | Added `<packaging>war</packaging>` | A WAR is the artifact format an external servlet container deploys; a plain executable JAR is not. |
| `pom.xml` | Added explicit `spring-boot-starter-tomcat` dependency at `<scope>provided</scope>` | `spring-boot-starter-webmvc` already pulls this in transitively at the default `compile` scope; declaring it explicitly at `provided` overrides that (Maven dependency mediation) so the external container's *own* Tomcat is what actually serves the app there, instead of two copies of Tomcat's classes conflicting on the classpath. `provided` only affects the WAR's own `WEB-INF/lib` — Spring Boot's Maven plugin re-adds `provided`-scope dependencies back in when repackaging the *executable* artifact, which is exactly why `mvn spring-boot:run`/`java -jar` are unaffected. |
| `src/main/java/.../HmsApplication.java` | Now `extends SpringBootServletInitializer`, overriding `configure(SpringApplicationBuilder)` | The Servlet 3.0+ SPI mechanism an external container uses to discover and bootstrap a Spring Boot app that isn't being launched via its own `main` method. `main` itself is untouched — embedded-Tomcat runs still go through it exactly as before. |
| `.env` | Added `TOMCAT_HOME`, `DOCKER_DESKTOP_EXE` | Read by the new `start-hms-server.cmd` (below) — never read by the Spring Boot app itself. |
| `start-hms-server.cmd` (new, repo root) | One-click startup script | See §6. |
| `<TOMCAT_HOME>\bin\setenv.bat` (new, lives in the Tomcat install, not the repo) | Environment variables mirroring `.env` | See §5, step 9 — Tomcat needs these set some other way since it doesn't run from this project's working directory. |

No changes were made to `application.yaml`, security config, or any business logic —
this is purely a packaging/deployment change.

---

## 3. Prerequisites

Install/confirm these **before** starting anything else. Version numbers are what this
deployment was actually built and verified against.

| Requirement | Version used | Notes |
|---|---|---|
| JDK | 26 (`C:\Program Files\Java\jdk-26.0.2`) | Must match (or be compatible with) what built the WAR. |
| Apache Maven | 3.9.16 | Via the repo's own `./mvnw` wrapper — a separate Maven install isn't strictly required. |
| Docker Desktop | Running, with its Linux engine fully started | Hosts Postgres. **Docker Desktop's process being launched is not the same as its engine being ready** — the engine can take 30 seconds to 2 minutes to finish starting after Docker Desktop's own window opens. `docker info` returning without error is the actual readiness signal; `start-hms-server.cmd` waits on exactly that, not just on the process existing. |
| Redis | A container named `my-redis`, restart policy `unless-stopped` | **Not** part of this project's `compose.yaml` — a separately-run container. Because its restart policy is `unless-stopped`, it comes back on its own the moment Docker's engine finishes starting, with no manual action needed, *provided it was created at some point with that restart policy*. If `my-redis` doesn't exist at all on a machine, create it first: <br>`docker run -d --name my-redis --restart unless-stopped -p 6379:6379 -e REDIS_PASSWORD=<value from .env's REDIS_PASSWORD> redis:latest` |
| Apache Tomcat | 11.0.25, extracted to `C:\Users\User\Downloads\apache-tomcat-11.0.25\apache-tomcat-11.0.25` | **Note the doubled path** — the downloaded zip's internal root folder has the same name as the folder it was extracted into, so it nested one level deeper than expected. Confirm with `dir` that `bin\startup.bat` exists at whatever path you record as `TOMCAT_HOME` before proceeding. |
| `.env` | Present at the repo root, gitignored | Holds every secret this section's steps reference (`DB_PASSWORD`, `REDIS_PASSWORD`, `ENCRYPTION_KEY`, etc.) — never committed, ask a current team member for a copy rather than reconstructing it from scratch. |

**Does starting Tomcat alone start everything else?** No. Tomcat only starts Tomcat —
it has no awareness of Docker, Postgres, or Redis. Starting the app before Postgres is
reachable makes it fail to deploy (Hibernate can't open a JDBC connection at startup, and
that failure aborts the whole context — the app won't limp along in a half-working
state). Bring up **Docker Desktop → Postgres (`docker compose up -d`) → confirm Redis is
up → then** Tomcat, in that order. `start-hms-server.cmd` does exactly this sequence for
you (§6); it isn't optional ordering, it's a hard dependency.

---

## 4. Architecture at a glance

```
                     ┌─────────────────────────────┐
  LAN device  ─────▶ │  Windows machine             │
  (browser)          │  192.168.1.46                │
                      │                              │
                      │  ┌────────────────────────┐  │
                      │  │ Apache Tomcat 11        │  │
                      │  │  :8080 (0.0.0.0)        │  │
                      │  │  webapps/ROOT.war ──┐   │  │
                      │  │                     │   │  │
                      │  │   HMS Spring Boot   │   │  │
                      │  │   app (this repo)   │   │  │
                      │  └─────────────────────┼───┘  │
                      │            │            │      │
                      │            ▼            ▼      │
                      │   ┌────────────┐  ┌──────────┐ │
                      │   │ Postgres    │  │ Redis    │ │
                      │   │ (Docker,    │  │ (Docker, │ │
                      │   │ :5433→5432) │  │ :6379)   │ │
                      │   └────────────┘  └──────────┘ │
                      └─────────────────────────────┘
```

The app, Postgres, and Redis all run on the **same** machine — only Tomcat's HTTP port
(8080) is exposed to the rest of the network. Nothing about the database or cache is
reachable from other machines, by design; that was never changed.

---

## 5. From-scratch setup — every step

Follow in order. Steps 1–7 you likely already have done if you're redeploying rather
than deploying for the first time — they're included so this works as a true from-zero
guide too.

1. **Clone the repository** to the machine that will run the server:
   ```
   git clone https://github.com/jadofils/hms.git
   cd hms
   ```

2. **Obtain `.env`** from a current team member (it's gitignored — never in the repo
   itself) and place it at the repo root, alongside `pom.xml`.

3. **Confirm `compose.yaml`'s Postgres port matches `.env`'s `DB_URL`** — this project
   publishes Postgres on host port **5433**, not the Postgres default 5432 (5432 may
   already be in use by an unrelated local install). If you changed either, keep them in
   sync.

4. **Start Docker Desktop** and wait for it to report itself ready (its own tray
   icon/window stops showing a "starting" state). Confirm with:
   ```
   docker info
   ```
   A clean summary output (not a connection error) means the engine is ready.

5. **Start Postgres**:
   ```
   docker compose up -d
   ```
   Confirm it's actually up:
   ```
   docker ps
   ```
   You should see `hms-postgres-1`, `Up ...`, port mapping `0.0.0.0:5433->5432/tcp`.

6. **Confirm (or create) Redis** — see the Prerequisites table above if `my-redis`
   doesn't already exist on this machine. Confirm it's running the same way, via
   `docker ps`.

7. **Build the WAR**:
   ```
   ./mvnw.cmd clean package
   ```
   This also runs the full test suite (971 tests as of this writing) — it will fail the
   build if Postgres/Redis from steps 4–6 aren't actually reachable, since a real number
   of tests hit the real database. To build without running tests (only do this once
   you've already verified the test suite passes on this code elsewhere):
   ```
   ./mvnw.cmd clean package -DskipTests
   ```
   On success, the artifact is at `target\hms-0.0.1-SNAPSHOT.war`.

8. **Extract Apache Tomcat 11** (if not already done) to a permanent location, and
   verify the real path — see the doubled-folder note in the Prerequisites table.
   Record this path; it's referred to as `TOMCAT_HOME` throughout the rest of this guide.

9. **Create `<TOMCAT_HOME>\bin\setenv.bat`** — this is the one setup step it's easiest
   to accidentally skip, and the app will start but fail every request that touches the
   database/cache/JWT/email if it's missing, since none of `.env`'s values reach the app
   any other way once it's running under Tomcat (Tomcat's working directory is its own
   `bin` folder, not this project's — the app's usual `spring.config.import:
   optional:file:.env[.properties]` can't find a `.env` that isn't there). Create the
   file with this content, filling in the real values from your own `.env`:
   ```bat
   @echo off
   set DB_URL=jdbc:postgresql://localhost:5433/hms
   set DB_USER=postgres
   set DB_PASSWORD=<from .env>
   set DB_POOL_MAX_SIZE=20
   set DB_POOL_MIN_IDLE=10
   set SPRING_PROFILES_ACTIVE=dev
   set REDIS_HOST=localhost
   set REDIS_PORT=6379
   set REDIS_USERNAME=redis
   set REDIS_PASSWORD=<from .env>
   set BCRYPT_ROUNDS=12
   set JWT_EXPIRY_HOURS=8
   set ENCRYPTION_KEY=<from .env>
   set GOOGLE_CLIENT_ID=<from .env>
   set GOOGLE_CLIENT_SECRET=<from .env>
   set GMAIL_HOST=smtp.gmail.com
   set GMAIL_PORT=587
   set GMAIL_USERNAME=<from .env>
   set GMAIL_PASSWORD=<from .env>
   set GMAIL_FROM_NAME=Hospital Management System
   set CLOUDINARY_CLOUD_NAME=<from .env>
   set CLOUDINARY_API_KEY=<from .env>
   set CLOUDINARY_API_SECRET=<from .env>
   set APP_PAGE_SIZE=20
   set APP_MAX_UPLOAD_SIZE_MB=10
   set APP_INVITE_TTL_DAYS=7
   set APP_FRONTEND_BASE_URL=http://localhost:3000
   set APP_SUPPORT_EMAIL=support@hms-hospital.com
   set APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,http://localhost:5000,http://localhost:8080,http://192.168.1.46:8080,https://sculpture-decent-playlist.ngrok-free.dev
   set CATALINA_OPTS=%CATALINA_OPTS% -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8
   ```
   The last two origins above (`http://192.168.1.46:8080`, the LAN IP this deployment
   actually ended up on, and `https://sculpture-decent-playlist.ngrok-free.dev`, this
   account's persistent free-tier ngrok subdomain — see §8 below and
   `ngrok-deployment-guide.md`) are only correct for *this* deployment; a fresh setup on a
   different machine/network will have a different LAN IP and, unless the same ngrok
   account/plan is reused, a different tunnel subdomain — re-derive both rather than
   copying these literal values.

   **This file now holds the same secrets `.env` does — treat it with the same care**:
   don't email it unencrypted, don't leave it world-readable, restrict its file
   permissions the same way you would `.env` itself. If you add a LAN-hosted frontend
   later that needs to call this API cross-origin, add its real origin — scheme + host +
   port only, never a path (see `docs/README.md`'s CORS/CSRF section for why) — to
   `APP_CORS_ALLOWED_ORIGINS` here too.

10. **Deploy the WAR**:
    ```
    rmdir /s /q "<TOMCAT_HOME>\webapps\ROOT"
    copy target\hms-0.0.1-SNAPSHOT.war "<TOMCAT_HOME>\webapps\ROOT.war"
    ```
    Deploying as `ROOT.war` (not `hms.war`) makes the app answer at `http://host:8080/`
    directly — matching the same URL shape the embedded-Tomcat dev setup already used —
    rather than under a `/hms` (or whatever the WAR is named) sub-path. Removing the
    existing `webapps\ROOT` folder first matters: Tomcat's default landing-page app
    already occupies that context, and leaving both present is ambiguous.

11. **Add the firewall rule** (one-time, needs an elevated/Administrator PowerShell):
    ```powershell
    New-NetFirewallRule -DisplayName "HMS Tomcat 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any
    ```

12. **Start it** — see §6 for the one-click way, or §7 for doing each piece by hand.

13. **Verify**: open `http://localhost:8080/` on the server itself, then
    `http://<server's LAN IP>:8080/` from a second device on the same network. Both
    should show the Swagger UI.

---

## 6. Starting the server — single click

From the repo root, double-click **`start-hms-server.cmd`**.

This one script performs the entire startup sequence in the correct order, and is safe
to run again at any point — every step checks the current state first and skips itself
if there's nothing to do:

1. Checks whether Docker's engine is responding (`docker info`). If not, launches Docker
   Desktop (path from `.env`'s `DOCKER_DESKTOP_EXE`) and polls for readiness for up to 3
   minutes before giving up with a clear message.
2. Runs `docker compose up -d` to bring up Postgres.
3. Confirms the `my-redis` container is running, starting it if it exists but happens to
   be stopped.
4. Checks whether anything is already listening on port 8080; if not, starts Tomcat
   (`<TOMCAT_HOME>\bin\startup.bat`) and polls `http://localhost:8080/actuator/health`
   for up to 2 minutes.
5. On success, opens `http://localhost:8080/` in the default browser and prints both the
   local and LAN URLs.

If any step fails, the script prints exactly which one and why, then pauses so the
window doesn't close before you can read it.

**Only the "everything already running" path has been live-verified** end to end on
this machine (Docker, Postgres, Redis, and Tomcat were all already up when this script
was tested — every check correctly detected that and skipped its own action). The
cold-start branches (Docker Desktop not yet running, Tomcat not yet started) use the
same individual commands verified working in isolation elsewhere in this guide, but
haven't been exercised together from a genuinely cold machine state. The first time you
run this from a full cold boot, watch its output rather than assuming success.

---

## 7. Starting the server — manual, step by step

Use this when you want to watch the server start (troubleshooting a fresh deployment,
or just being able to see the logs live), or don't want to rely on the script in §6.
Docker/Postgres/Redis (§5 steps 4–6) must already be up before you do any of this —
Tomcat has no idea those exist and won't wait for them.

### Option A — `startup.bat` (the normal way)

1. Open File Explorer (or an already-open terminal window — either is fine) and go to:
   ```
   <TOMCAT_HOME>\bin
   ```
   e.g. `C:\Users\User\Downloads\apache-tomcat-11.0.25\apache-tomcat-11.0.25\bin`.
2. Double-click **`startup.bat`** (or run `startup.bat` if you're already in that folder
   in a terminal).
3. A **new console window** titled "Tomcat" opens and starts printing startup log lines
   directly into it — `Server initialization in [...] milliseconds`, the WAR being
   deployed, and finally `Server startup in [...] milliseconds`. That whole sequence
   takes roughly 30–45 seconds for this app (most of it is Spring's own context
   startup, not Tomcat itself).
4. **Leave that window open.** It's not just a progress log — Tomcat is actually
   running inside it. Closing it (or the terminal it was launched from, if you didn't
   use a fresh double-click) stops the server the same way closing any console window
   ends the program running in it. Minimizing it is fine.
5. Confirm it's really up from any other window:
   ```
   curl http://localhost:8080/actuator/health
   ```
   Expect `{"status":"UP"}` (or similar). If you'd rather not leave a console window
   sitting on the desktop long-term, look into installing Tomcat as a genuine Windows
   service (`<TOMCAT_HOME>\bin\service.bat install`) — out of scope for this guide, but
   the standard next step for a server meant to run unattended and survive a logoff.

### Option B — `catalina.bat run` (foreground, logs in your own window)

Functionally identical to Option A, except Tomcat runs directly in the terminal window
you launch it from instead of opening a new one — useful over a remote/SSH-style
session, or when you specifically don't want a second window appearing. **This is the
exact method actually used to verify this deployment** (see §11's troubleshooting entry
for why `startup.bat` needs a genuinely interactive session and `catalina.bat run`
doesn't).

1. Open a terminal, `cd` to `<TOMCAT_HOME>\bin`.
2. Run:
   ```
   catalina.bat run
   ```
3. The same startup log lines print directly into this terminal. Wait for
   `Server startup in [...] milliseconds`.
4. This terminal is now attached to the running server — **don't close it**. Stop the
   server with **Ctrl+C** in this same window when you want it down (equivalent to
   `shutdown.bat` in Option A).
5. Verify the same way: `curl http://localhost:8080/actuator/health` from a second
   window.

### Which one should you actually use?

Option A (`startup.bat`) for routine day-to-day starts — it's the standard, and it
frees up your terminal immediately. Option B (`catalina.bat run`) when you're
specifically troubleshooting startup itself and want the logs in a window you already
control, or when scripting this non-interactively (see §11 — `startup.bat` is the wrong
choice for that case specifically).

---

## 8. Network access

- **On this machine**: `http://localhost:8080/`
- **From any other device on the same network**: `http://192.168.1.46:8080/` (this
  machine's Wi-Fi LAN address at the time of this deployment — confirm it hasn't
  changed with `ipconfig` if it's been a while, especially after a router reboot or DHCP
  lease renewal; consider a DHCP reservation for this machine if this deployment is
  meant to be long-lived, so the IP doesn't drift).
- **Requires**: the firewall rule from §5 step 11, and the requesting device being on
  the same network/subnet (this is a LAN deployment, not internet-facing — nothing here
  makes it reachable from outside the local network, which was not asked for and would
  need a separate, deliberate decision plus real hardening before doing it).
- **Known limitation**: Google OAuth2 login (`/oauth2/authorization/google`) only works
  when accessed as `localhost:8080` — that's the exact redirect URI registered with
  Google (`http://localhost:8080/login/oauth2/code/google`). A device on the LAN hitting
  the app via its IP address instead can still use regular username/password login
  (`POST /api/v1/auth/login`) and every other endpoint normally; only the Google login
  button specifically won't complete from a non-`localhost` origin.

---

## 9. Primary endpoints / where to find documentation

| Purpose | URL |
|---|---|
| **Swagger UI** (interactive REST API docs, "Try it out") | `http://<host>:8080/` |
| Raw OpenAPI spec (JSON) | `http://<host>:8080/v3/api-docs` |
| **GraphQL** endpoint | `http://<host>:8080/graphql` |
| **GraphiQL** (in-browser GraphQL explorer) | `http://<host>:8080/graphiql` |
| Health check | `http://<host>:8080/actuator/health` |
| Other actuator metrics | `http://<host>:8080/actuator/metrics` |
| CSRF mechanism demo page | `http://<host>:8080/docs/csrf-demo` |
| Get a token (POST) | `http://<host>:8080/api/v1/auth/login` |
| Google sign-in (browser navigation only — not from a REST client) | `http://<host>:8080/oauth2/authorization/google` |

Replace `<host>` with `localhost` on the server itself, or the LAN IP from §8 for any
other device. Every REST endpoint lives under `/api/v1/{resource}` — Swagger UI is the
authoritative, always-current list of every one of them, including request/response
schemas.

---

## 10. Stopping / redeploying

**Stop Tomcat only** (leaves Postgres/Redis running):
```
"<TOMCAT_HOME>\bin\shutdown.bat"
```

**Redeploy after a code change**:
```
./mvnw.cmd clean package
rmdir /s /q "<TOMCAT_HOME>\webapps\ROOT"
copy target\hms-0.0.1-SNAPSHOT.war "<TOMCAT_HOME>\webapps\ROOT.war"
"<TOMCAT_HOME>\bin\shutdown.bat"
```
then start again via `start-hms-server.cmd` (or `startup.bat` directly). Tomcat does
support hot-redeployment (dropping a new WAR in while it's running), but a clean
stop/replace/start is the more predictable choice for anything beyond a trivial change,
and is what's documented here.

---

## 11. Troubleshooting

**Every request hangs forever, no errors in any log.** This happened once during this
very deployment and is worth documenting precisely: `startup.bat` is written to run from
a real interactive console session (double-clicked from the desktop, or run from an
already-open terminal window). If it's ever launched from a context with no real
console — a scheduled task, or an automated script that captures/redirects its output
via a pipe — the spawned Tomcat JVM's `stdout` can end up wired to that same pipe. Once
enough log output accumulates and nobody is reading the other end, the JVM blocks
indefinitely on its very next `System.out` write, which freezes request handling
entirely (confirmed via a Java thread dump during this deployment — the request-handling
thread was stuck inside a logging call, not inside application code). **Fix**: only
launch `startup.bat` from a real double-click or an interactive terminal — which is the
normal way an administrator would run it anyway. If you ever need to start Tomcat from
an automated/non-interactive script, use `catalina.bat run` with its output explicitly
redirected to a real log file instead (`catalina.bat run > start.log 2>&1`), never a
pipe.

**App fails to start, `PSQLException: Connection to localhost:5433 refused`.** Postgres
isn't up yet. Run `docker compose up -d` and confirm with `docker ps` before starting
Tomcat again.

**App starts but every DB/Redis/email/JWT-related request fails.** `setenv.bat` (§5,
step 9) is missing, in the wrong location, or has a typo'd variable name — this is the
#1 way this specific deployment breaks, since nothing about a missing `.env` produces an
obvious startup error the way a missing database connection does; Spring just resolves
those `${VAR}` placeholders to blank/default and fails later, on first real use.

**Port 8080 already in use when starting Tomcat.** Something else is bound to it —
commonly, an embedded-Tomcat dev instance (`spring-boot:run`) left running from earlier
testing. Find and stop it: `netstat -ano | findstr :8080`, then `taskkill /F /PID <pid>`.

**Firewall rule command fails with "Access is denied".** It needs an elevated
PowerShell — right-click PowerShell → "Run as Administrator" — not a normal one.
