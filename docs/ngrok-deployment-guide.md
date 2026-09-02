# HMS Ngrok Tunnel Guide — Global Access via ngrok

Audience: system administrators and developers who need this HMS deployment reachable
from **any network**, not just the local LAN. This builds directly on top of
[`deployment-guide.md`](deployment-guide.md) — that guide's external-Tomcat deployment
must already be running before anything here applies. Every command and result below was
run for real against this actual deployment, including the problems hit along the way —
nothing here is theoretical.

---

## 1. What this adds, and why it needs one extra piece

`deployment-guide.md` makes the app reachable from other machines on the same Wi-Fi/LAN,
via a Windows Firewall rule on port 8080. **ngrok** goes further: it opens an outbound
tunnel from this machine to ngrok's own cloud, which hands back a public internet URL —
anyone, anywhere, can reach the app through that URL, with no router configuration, no
port forwarding, and no public IP needed on this machine.

That reach is exactly the reason this setup does **not** point ngrok at the app's port
(8080) directly. This codebase has a real, already-known gap: GraphQL resolvers are
never covered by `AuthorizationAspect` at all (see `CLAUDE.md`) — every mutation,
including admin ones, is reachable with zero authorization. Harmless-ish on a trusted
LAN; a real problem the moment the URL is public. So a small local proxy sits between
ngrok and the app, forwarding everything except `/graphql`/`/graphiql`, which it
rejects outright. ngrok tunnels the *proxy's* port, never the app's port directly.

```
Internet ──▶ ngrok cloud ──▶ ngrok agent (this machine)
                                    │
                                    ▼
                     graphql-block-proxy.py :8081  ── blocks /graphql, /graphiql (404)
                                    │
                                    ▼
                         Tomcat :8080 → HMS app → Postgres / Redis
```

---

## 2. Prerequisites

| Requirement | Notes |
|---|---|
| The LAN deployment already running | See `deployment-guide.md` — Tomcat serving the app on port 8080, verified reachable at `http://localhost:8080/`. This guide's one-click script refuses to continue if it isn't. |
| Python 3 on `PATH` | Runs `graphql-block-proxy.py` — standard library only, nothing to `pip install`. |
| A free ngrok account | Sign up at `https://dashboard.ngrok.com/signup` — no payment needed for everything in this guide except the optional custom-domain-name feature (§8). |
| Outbound internet access on port 443 | ngrok's tunnel is an outbound HTTPS connection this machine initiates — no inbound firewall rule is needed for ngrok itself (unlike the LAN setup's port-8080 rule). |

---

## 3. Step 1 — Download and install ngrok

**Use a direct download, not winget.** Winget's own `ngrok.ngrok` package was tried
first during this deployment and turned out to ship a badly outdated build (3.3.1) that
this account's minimum required agent version (3.20.0) rejected outright. `ngrok update`
(ngrok's own self-updater) was attempted next and also failed partway — it renamed the
old binary aside but never successfully wrote the new one in winget's package directory,
almost certainly a directory-permissions restriction specific to how winget lays out its
packages. A direct download avoids both problems entirely and is what actually ended up
working.

1. Download the Windows build directly:
   ```
   curl -L -o ngrok.zip https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip
   ```
   (or use a browser: `https://ngrok.com/download` → Windows).
2. Extract it to a permanent folder, e.g. `C:\Users\User\Downloads\ngrok\`:
   ```
   tar -xf ngrok.zip -C C:\Users\User\Downloads\ngrok
   ```
   (Windows' built-in `tar` handles zip files fine; any unzip tool works too.)

3. **Add a Windows Defender exclusion for that folder before running it.** This is not
   optional caution — it's a real problem this deployment hit twice: Windows Defender
   silently quarantined `ngrok.exe` the instant it was extracted, both the winget copy
   and the freshly-downloaded one, confirmed via `Get-MpThreatDetection`. This is a
   well-known, common false positive — tunneling tools superficially resemble malware
   C2 software to signature-based heuristics even though ngrok itself is legitimate.
   Run this in an **elevated** PowerShell (right-click → Run as Administrator):
   ```powershell
   Add-MpPreference -ExclusionPath "C:\Users\User\Downloads\ngrok"
   ```
   Or via the GUI: **Windows Security** → **Virus & threat protection** → **Manage
   settings** → **Exclusions** → **Add or remove exclusions** → **Add an exclusion** →
   **Folder** → select that folder.

4. Confirm it actually survived and runs:
   ```
   C:\Users\User\Downloads\ngrok\ngrok.exe version
   ```
   Expect a real version string (`ngrok version 3.39.11` at the time of this
   deployment) — if the file is silently gone again, the exclusion from step 3 wasn't
   applied to the right folder, or wasn't applied before this download.

---

## 4. Step 2 — Sign up, get a token, configure it

1. Sign up (free) at `https://dashboard.ngrok.com/signup` if you don't have an account.
2. Copy your personal authtoken from
   `https://dashboard.ngrok.com/get-started/your-authtoken`.
3. Configure it — this writes ngrok's config file (`%LOCALAPPDATA%\ngrok\ngrok.yml`,
   which doesn't exist until this command creates it):
   ```
   C:\Users\User\Downloads\ngrok\ngrok.exe config add-authtoken YOUR_TOKEN_HERE
   ```
4. Verify:
   ```
   C:\Users\User\Downloads\ngrok\ngrok.exe config check
   ```
   Expect `Valid configuration file at ...`.

**This token is a credential** — don't paste it into a shared/committed file. It never
needs to go in `.env`; it lives only in ngrok's own config file, which is outside the
repo (under your Windows user profile).

---

## 5. Step 3 — The GraphQL-blocking proxy

Already built and committed as [`graphql-block-proxy.py`](../graphql-block-proxy.py)
(repo root) — nothing to write yourself, but it's worth understanding what it does and
why, since it's the one piece standing between "LAN-safe" and "internet-safe" here.

**What it does**: a small HTTP reverse proxy, standard-library Python only (no
`pip install`). Every request to it is forwarded unchanged to the real app — *except*
`/graphql` and `/graphiql`, which get an immediate `404` before the app ever sees the
request. It also correctly relays redirects (needed for Swagger UI's own `/` → 
`/swagger-ui/index.html` redirect — `urllib`'s default auto-follow-redirects behavior
was confirmed live to silently swallow that redirect otherwise, which would have broken
the browser's relative-asset loading).

**Two real bugs found and fixed while building it, both confirmed live**:
- `BaseHTTPRequestHandler`'s default HTTP/1.1 keep-alive handling made `curl` hang
  indefinitely on requests the proxy's own log showed had already succeeded — fixed by
  forcing `HTTP/1.0` (closes the connection after every response, sidestepping the
  keep-alive bookkeeping entirely).
- `urllib.request.urlopen` auto-follows redirects by default, which silently turned
  Swagger UI's real `302` into a `200` — fixed with a custom `HTTPRedirectHandler` that
  declines to follow, so the original 3xx and its `Location` header reach the client
  (browser) intact.

**Configurable without editing the file** (see §8 for exactly when you'd need this):
```
set HMS_APP_PORT=8080      REM the real app's port (Tomcat) -- default 8080
set HMS_PROXY_PORT=8081    REM the port this proxy itself listens on -- default 8081
```

Run it manually if you ever need to (the one-click script in §7 does this for you):
```
python graphql-block-proxy.py
```

---

## 6. Step 4 — Start the tunnel manually (understanding what's happening)

Skip to §7 for the one-click way; this section is for understanding each piece, or for
troubleshooting one of them individually.

1. Confirm the app itself is already up: `http://localhost:8080/` should load Swagger UI.
2. Start the blocking proxy, in its own window (leave it open):
   ```
   python graphql-block-proxy.py
   ```
   Confirm: `http://localhost:8081/actuator/health` should return the same `200` as
   port 8080 does; `http://localhost:8081/graphql` should return `404`.
3. Start ngrok, **pointed at the proxy's port (8081), not the app's port (8080)**, in
   its own window:
   ```
   C:\Users\User\Downloads\ngrok\ngrok.exe http 8081
   ```
4. ngrok prints its assigned public URL directly in that window (also viewable anytime
   at its local inspector, `http://127.0.0.1:4040`, which additionally lets you replay
   or inspect every request that's come through the tunnel — genuinely useful for
   debugging what a remote client actually sent).
5. Verify from *outside* your own machine's normal browsing (a phone on mobile data
   works well for this) that the public URL loads Swagger UI, and that
   `<public-url>/graphql` returns `404`.

---

## 7. Starting it — single click

From the repo root, after the LAN deployment (`start-hms-server.cmd`) is already
running, double-click **`start-ngrok-tunnel.cmd`**.

This performs the full sequence, and — like `start-hms-server.cmd` — is safe to run
again at any point; every step checks first and skips itself if there's nothing to do:

1. Confirms the app is already up (port 8080); refuses to continue if not, with a
   pointer back to `start-hms-server.cmd`.
2. Starts `graphql-block-proxy.py` in its own window, if it isn't already running.
3. Starts ngrok (tunneling port 8081) in its own window, if it isn't already running.
4. Queries ngrok's own local API (`http://127.0.0.1:4040/api/tunnels`) via a small
   companion script, [`get-ngrok-url.ps1`](../get-ngrok-url.ps1), and prints the live
   public URL alongside the direct URLs for Swagger UI and login — then opens it in
   your browser.

**Live-verified end to end**, cold start (nothing running beforehand) through to a real
public request succeeding and `/graphql` correctly blocked, immediately before this
guide was written — not assumed to work from the individual pieces working in
isolation.

Two real batch-scripting bugs were found and fixed while building this one, worth
knowing if you ever edit it:
- Bare `timeout /t N` resolved to Git's own coreutils `timeout` instead of Windows'
  native one in one test context (wrong argument syntax entirely) — fixed by calling
  `"%SystemRoot%\System32\timeout.exe"` explicitly, then switched to `ping -n 2
  127.0.0.1 >nul`-style delays instead, since Windows' native `timeout.exe` also
  refuses to run at all when its input is redirected (only relevant to scripted/
  automated invocations, never a real double-click, but `ping`-based delays work
  unconditionally either way).
- A literal `(POST)` inside an `echo` line, itself nested inside a parenthesized
  `if/else (...)` block, broke the batch parser (parentheses inside a parenthesized
  block need escaping as `^(`/`^)`, the same rule already applied everywhere else in
  this script — this one line was missed on the first pass and caught by actually
  running it, not by inspection).

---

## 8. All URLs — and how they change if a port changes

Assuming the currently-assigned public URL is `https://sculpture-decent-playlist.ngrok-free.dev`
(ngrok assigns this; see the note below on how stable it actually is):

| Purpose | URL |
|---|---|
| Swagger UI / API docs | `https://sculpture-decent-playlist.ngrok-free.dev/` |
| Raw OpenAPI spec | `https://sculpture-decent-playlist.ngrok-free.dev/v3/api-docs` |
| Login (POST) | `https://sculpture-decent-playlist.ngrok-free.dev/api/v1/auth/login` |
| Health check | `https://sculpture-decent-playlist.ngrok-free.dev/actuator/health` |
| GraphQL | **blocked by design** — `404` from the proxy, on purpose (§1) |
| ngrok's local inspector (this machine only, not public) | `http://127.0.0.1:4040` |

**CORS**: the public URL above (and the LAN URL from `deployment-guide.md` §8) is only
relevant to *server-to-server* calls or same-origin use (Swagger UI itself, opened at
that URL, never crosses an origin). If a separate frontend hosted somewhere else calls
this API's `/api/**` cross-origin via `fetch`/XHR, its real origin — and this tunnel's
public origin, if a browser page is ever served *through* the tunnel and needs to call
back to it — must be in `APP_CORS_ALLOWED_ORIGINS` (`.env` for local dev,
`setenv.bat` for this external-Tomcat deployment — see `deployment-guide.md` §5 step 9)
or the browser blocks the response. `https://sculpture-decent-playlist.ngrok-free.dev`
and `http://192.168.1.46:8080` are both already in that allow-list as of this deployment
(verified live via `curl -X OPTIONS` preflights against both, each correctly echoing back
in `Access-Control-Allow-Origin`); a *different* ngrok subdomain (a new free-tier
reassignment, or a paid custom domain) would need adding the same way — see
`docs/README.md`'s CORS/CSRF section for the syntax rule (scheme + host + port, never a
path).

**Is the public URL stable across restarts?** Mostly, but not guaranteed. This
deployment's free-tier account was reassigned the *exact same* subdomain
(`sculpture-decent-playlist.ngrok-free.dev`) across multiple separate tunnel restarts —
free ngrok accounts get one persistent, ngrok-assigned random subdomain rather than a
fresh random one every time, which was better than expected. **Choosing your own custom
name is a separate, paid-only feature** — confirmed live on this account: requesting
`ngrok http --url https://amalitech.hms.ngrok-free.dev 8081` failed outright with
`ERR_NGROK_313: Only paid plans may create endpoints with custom subdomains`. If a
stable, memorable, custom name genuinely matters, that requires upgrading at
`https://dashboard.ngrok.com/billing/choose-a-plan`; the free tier's own
auto-persisted-but-unchosen subdomain is the practical alternative otherwise. Whatever
the current URL actually is, `start-ngrok-tunnel.cmd` (§7) always prints the real,
live one — don't hard-code the one in this table into anything.

**If the app's own port changes** (Tomcat reconfigured off 8080): set
`HMS_APP_PORT` before starting the proxy (§5) — nothing else needs to change, since
ngrok only ever talks to the proxy, never the app directly.

**If the proxy's own port needs to change** (8081 conflicts with something else on this
machine): set `HMS_PROXY_PORT` to a free port before starting the proxy, **and** update
the port number in the `ngrok http 8081` command (§6 step 3, or `start-ngrok-tunnel.cmd`
if you're editing the script) to match — the two have to agree, since ngrok is tunneling
whatever port the proxy is actually listening on.

**If you ever want to tunnel the app directly, skipping the proxy** (`ngrok http 8080`
instead of `8081`): technically possible, but this reopens the exact GraphQL
zero-authorization gap §1 exists to close. Not recommended for anything reachable from
the public internet.

---

## 9. Stopping it

Close the ngrok window, then the proxy window (or `Ctrl+C` in each if you started them
in a terminal rather than via the one-click script's own new windows). This only stops
the *tunnel* — the underlying app (Tomcat) and the LAN deployment keep running
independently; stop those separately per `deployment-guide.md` if needed.

---

## 10. Troubleshooting

**`start-ngrok-tunnel.cmd` refuses to start, "App is not running yet."** Run
`start-hms-server.cmd` first and wait for it to confirm the app answers on port 8080.

**ngrok's window shows `ERR_NGROK_...` about authentication/agent version.** Either the
authtoken isn't configured (§4) or the binary is outdated (§3's winget/self-update
issues) — run `ngrok.exe config check` and `ngrok.exe version` to tell which.

**ngrok.exe silently disappears after downloading/extracting it.** Windows Defender
quarantined it — this happened twice during this exact deployment. Add the folder
exclusion from §3 step 3 *before* downloading, not after (the exclusion doesn't retroactively
restore an already-quarantined file — you'd need to redownload once the exclusion is in
place).

**Public URL loads, but `/graphql` also loads instead of `404`.** ngrok is tunneling
port 8080 (the app) directly instead of 8081 (the proxy) — check the actual `ngrok http`
command that was run.

**Everything works locally but the public URL times out.** Outbound port 443 is
blocked on this network (rare, but possible on some corporate/restricted networks) —
ngrok's tunnel is an outbound HTTPS connection and needs that port open outbound.
