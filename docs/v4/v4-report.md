# HMS v4 Report — Spring Security, JWT, OAuth2 & RBAC

This maps every Epic/User Story in [`README-V4.MD`](README-V4.MD) to what's actually
implemented in this codebase, where, and — for each acceptance criterion this pass
touched — what was verified live (curl output pasted below, not "should work"
hand-waving), the same evidence-first convention [`v2-report.md`](../v2/v2-report.md) followed.

Most of the JWT/BCrypt foundation already existed from earlier passes. What's genuinely
new this pass: Google OAuth2 login end-to-end, global CORS configuration,
`@PreAuthorize`/`@EnableMethodSecurity` as a second, independently-implemented
permission check, a dedicated auth-attempt security-event log, and a live
CSRF-token-mechanism demo endpoint. Each is called out explicitly below, alongside a few
deliberate, honestly-flagged deviations from the README's literal wording where this
codebase's existing domain model made a different choice more correct than following
the checklist verbatim.

**A note on "RBAC" vs. this project's actual model.** The README's own Epic 4.2 title
and evaluation-criteria row both say "RBAC" — but per explicit direction partway through
this pass, `@PreAuthorize` here checks a **granted permission** (resource:action), never
a role name. A role in this codebase is just a named, admin-editable bundle of
permissions (`RoleService.grantPermission`/`revokePermission`); nothing is ever gated on
which bundle a caller happens to hold rather than what's actually been granted to it.
The first version of this pass's `@PreAuthorize` additions used
`@PreAuthorize("hasRole('ADMIN')")` — replaced before this report's evidence was
collected with `@PreAuthorize("@permissionCheck.has(resource, action)")`
(`PermissionExpressions`) once that distinction was made explicit, so every curl trace
below reflects the permission-based version, not the role-based one it briefly was.

---

## Epic 1: Security Configuration and Access Policies

**User Story 1.1** — *As a developer, I want to configure Spring Security filters so that
I can protect all endpoints and define which resources require authentication.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| `SecurityFilterChain` configured with custom access rules | ✅ Already done, extended | `SecurityConfig.securityFilterChain` — CORS, session policy, OAuth2 login, JWT filter all wired through it; see below for the new `@Order(1)` second chain |
| Public vs. restricted endpoints defined | ⚠️ Deliberately not at the URL-matcher level | See below |
| Passwords stored using `BCryptPasswordEncoder` | ✅ Already done | `PasswordEncoderConfig.passwordEncoder` (moved out of `SecurityConfig` this pass — see "Circular bean dependency" below) |

**"Public vs. restricted endpoints defined" — real, but one level down, not at
`authorizeHttpRequests`.** `SecurityConfig` still leaves the URL matcher itself at
`anyRequest().permitAll()`, unchanged from before this pass — deliberately, per that
class's own Javadoc: Swagger's "Try it out" needs to reach every route and get a real
`401`/`403` body back, not a generic Spring Security block page. The actual restriction
happens at the method level, two mechanisms deep:
1. `@RequirePermission(resource, action)` on essentially every mutating/most read
   controller methods (`aop.AuthorizationAspect`, checked against the real
   `Role`/`Permission`/`RolePermission` tables) — this is what makes
   `receptionist1`/`Reception@123` unable to do anything a receptionist's seeded
   permission set doesn't cover.
2. **New this pass** — `@PreAuthorize("@permissionCheck.has(resource, action)")`
   (`@EnableMethodSecurity`, added to `SecurityConfig`; `PermissionExpressions` is the
   `permissionCheck` bean the SpEL expression calls) as a second, independently-
   implemented check of the *same* permission `@RequirePermission` on that method already
   declares — not a coarser role check, and not a replacement — on a small set of
   endpoints: `RoleController.createRole`/`updateRole`/`deleteRole`/`grantPermission`/
   `revokePermission`, `SystemLogController.getSystemLogs`/`getSystemLog`. Confirmed via
   `AskUserQuestion` that layering `@PreAuthorize` onto the existing `@RequirePermission`
   mechanism (not replacing it) was the right call; the permission-vs-role distinction
   itself came from explicit direction shortly after that.

Live-verified, real tokens, real HTTP:
```
$ curl -i -X POST /api/v1/roles -H "Authorization: Bearer <receptionist1's token>" -d '{"roleName":"TestRoleABC",...}'
HTTP/1.1 403        # @PreAuthorize("@permissionCheck.has('roles','create')") — receptionist was never granted roles:create

$ curl -i -X POST /api/v1/roles -H "Authorization: Bearer <admin's token>" -d '{"roleName":"TestRoleABC",...}'
HTTP/1.1 201         # same request, admin token — DataSeeder grants Admin every permission, creates the role
```
`PermissionExpressions.has(resource, action)` reads the same `AuthenticatedUser`
principal `JwtAuthenticationFilter` already puts on `SecurityContextHolder`, then calls
the exact same `RolePermissionRepository.hasGrantedPermission(roleName, resource, action)`
query `AuthorizationAspect` already runs for `@RequirePermission` — same source of truth,
reachable from `@PreAuthorize`'s SpEL via a named bean reference (`@permissionCheck`)
instead of a custom `@Aspect`, since Spring Security 7's `DefaultMethodSecurityExpressionHandler`
no longer exposes a straightforward way to wire a custom `PermissionEvaluator` into its
built-in `hasPermission(...)` SpEL function — see `PermissionExpressions`' own Javadoc.

**Honest gap vs. the README's literal role list.** Epic 1.1 and 4.2 both name
`ADMIN, DOCTOR, NURSE, RECEPTIONIST`. This codebase's `RoleName` enum (predates this pass)
is `ADMIN, DOCTOR, RECEPTIONIST, ANALYST, PHARMACIST` — there is no `NURSE` role, and
never has been; the domain already covers pharmacy/lab/finance verticals that a generic
"nurse" role doesn't map onto directly. Adding a `NURSE` enum value with no real
permission set or endpoint behind it purely to match the README's wording would be exactly
the kind of demo-only addition `CLAUDE.md` argues against — not done. `ADMIN`,
`DOCTOR`, and `RECEPTIONIST` (three of the README's four) are real, seeded, and exercised
above; `ANALYST`/`PHARMACIST` cover the roles this domain actually needed instead of a
generic nurse.

**User Story 1.2** — *As a frontend developer, I want to enable secure cross-origin
requests so that external clients can interact with the backend.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Global CORS config — specific origins/methods/headers | ✅ New this pass | `SecurityConfig.corsConfigurationSource` |
| Tested with Postman and a web frontend | ✅ Live-verified (curl stands in for Postman) | See below |
| Unauthorized origins rejected | ✅ Live-verified | See below |

`app.cors-allowed-origins` (`.env`-driven, comma-separated — never a bare `"*"`, which
credentialed requests can't use anyway) feeds `CorsConfigurationSource`, applied
uniformly via `.cors(cors -> cors.configurationSource(...))` in the filter chain. Live
preflight requests against the real running app:
```
$ curl -i -X OPTIONS /api/v1/roles -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET"
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000

$ curl -i -X OPTIONS /api/v1/roles -H "Origin: http://evil.com" -H "Access-Control-Request-Method: GET"
HTTP/1.1 403
```
The allow-listed origins (`http://localhost:3000`, `http://127.0.0.1:3000`,
`http://localhost:5000`, `http://localhost:8080`) are exactly the ones already registered
against the real Google OAuth2 client (`redirect_uris`/`javascript_origins` in
`docs/client_secret_*.json` — gitignored, never committed, see below), so the same origin
list that already had to exist for OAuth2 is reused here instead of inventing a second,
possibly-inconsistent list.

**Circular bean dependency, found and fixed this pass.** Wiring `OAuth2LoginSuccessHandler`/
`OAuth2LoginFailureHandler` as constructor dependencies of `SecurityConfig` (needed for
`.oauth2Login(...)`) created a genuine `BeanCurrentlyInCreationException` at startup:
`SecurityConfig` → `OAuth2LoginSuccessHandler` → `AuthService` → `PasswordEncoder`, and
`PasswordEncoder` used to be a `@Bean` method *on* `SecurityConfig` itself — which can't
run until `SecurityConfig`'s own instance already exists, which is exactly what the chain
above was still waiting on. Caught by actually booting the app (not just compiling) after
wiring this in; fixed by extracting `PasswordEncoder` into its own dependency-free
`PasswordEncoderConfig` class (see that class's own Javadoc for the full trace). Re-booted
clean afterward — `Started HmsApplication in 16.134 seconds`, full 852-test suite still
green.

---

## Epic 2: JWT-Based Authentication

**User Story 2.1** — *As a user, I want to log in with valid credentials and receive a
JWT token so that I can access restricted endpoints securely.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| `/auth/login` generates signed JWTs with claims | ✅ Already done | `AuthService.login` → `JwtService.generateToken` |
| Tokens validated on each protected request | ✅ Already done | `JwtAuthenticationFilter` |
| Tampered/expired tokens rejected with 401 | ✅ Already done | `JwtService` — `TokenExpiredException`/decrypt-failure both surface as 401 via `GlobalExceptionHandler` |

Nothing new here functionally — this was solid from an earlier pass. Real login,
live-verified this pass as part of testing the permission-check/logging additions above:
```
$ curl -X POST /api/v1/auth/login -d '{"username":"admin","password":"Admin@123"}'
{"status":"success","message":"Authenticated","data":{"token":"eyJhbGciOiJIUzI1NiIs...","userId":"8c0d2af9-...","username":"admin","role":"Admin"}}
```

**User Story 2.2** — *As a system analyst, I want to verify token structure and claims so
that I can validate security implementation.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Subject, issued time, expiration claims | ✅ Already done | See decoded payload below |
| HMAC SHA-256 or RSA signature | ✅ Already done | `Algorithm.HMAC256(...)` in `JwtService` |
| Payload viewable decoded in Postman | ✅ Inherently true of any JWT | See below |

Decoded, from the real token above (base64url header/payload, no verification needed to
just read it — exactly what pasting it into Postman's own JWT decoder or jwt.io does):
```
header:  {"alg":"HS256","typ":"JWT"}
payload: {"jti":"6e8a1b49-580a-4a72-b55e-8d7460c4d757",
          "userId":"z2nQQ5M2qjOenbvgGD9PdCgbdowXRXOtS6bcQUY0z3vNwAAQtHrLCbqPLRa37PohsBE0oCTyX1pdaAfcUsttUg",
          "username":"-wZe5elmdf6iy4R-RCoAjQhH8tQUHJY4QNWaF2BOz0Ed",
          "role":"J3aSMgIyq89YBrDvSuITp6uhLEtK1AumOZnAgfjfWyOJ",
          "iat":1787393133,"exp":1787421933}
```
`jti`/`iat`/`exp` are plaintext (needed for the Redis blocklist lookup and expiry check
without decrypting anything first); `userId`/`username`/`role` are visibly opaque
ciphertext, not base64-of-plaintext — each is individually AES-256-GCM-encrypted before
embedding (`JwtService`), since `java-jwt` itself only signs, it has no encryption of its
own. A caller inspecting this in Postman sees real structure and claims present, but can't
read the actual username/role without the server's own encryption key — a deliberately
stronger guarantee than the README's bare acceptance criterion asks for.

---

## Epic 3: CSRF and Session Security

**User Story 3.1** — *As a security engineer, I want to configure CSRF protection
properly so that the system is secure against cross-site request forgery.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| CSRF disabled for the stateless JWT API | ✅ Already done | `SecurityConfig` — `.csrf(csrf -> csrf.disable())` |
| Explanation for when CSRF should be enabled | ✅ New this pass | See below |
| CSRF token mechanism demonstrated on one form endpoint | ✅ New this pass, live-verified | See below |

**Why CSRF is off for the rest of this API.** CSRF exploits a browser *automatically*
attaching credentials (a session cookie) to a request forged by another site the victim
happens to have open. This API authenticates via a bearer JWT in an `Authorization`
header — nothing a browser ever attaches on its own — so there is no ambient credential
for a forged cross-site request to ride on. Disabling CSRF here isn't skipping a
protection this app needs; it's recognizing the attack's precondition (cookie/session-based
auth) doesn't exist. **CSRF should be enabled** whenever an app *does* authenticate via a
cookie/session that the browser sends automatically — classic server-rendered form logins,
or any stateful session-cookie-based auth — which is exactly why the one demo path below
deliberately keeps it on.

**The demo — `GET /docs/csrf-demo`, new this pass.** `CsrfDemoSecurityConfig` adds a
second `SecurityFilterChain`, `@Order(1)` (evaluated before `SecurityConfig`'s own,
`@Order(2)`), matched only to `/docs/csrf-demo/**`, which simply never calls `.csrf(...)`
at all — Spring Security's CSRF protection is on by default, so the *absence* of a
disable call is the whole mechanism. `CsrfDemoController` (`docs` package, same
Thymeleaf-page pattern as the pre-existing `ApiComparisonController`) renders two forms
posting to the same endpoint: one carries the real, session-bound token; the other omits
it, standing in for a forged submission. Live-verified against the real running app, real
session cookie:
```
$ curl -c cookies.txt /docs/csrf-demo          # fetch the real token for this session
$ curl -b cookies.txt -X POST /docs/csrf-demo -d "note=forged"                       # no token
< HTTP/1.1 403                                  # CsrfFilter itself rejects it, never reaches the controller

$ curl -b cookies.txt -X POST /docs/csrf-demo --data-urlencode "note=hello" --data-urlencode "_csrf=<real token>"
< HTTP/1.1 200                                  # valid token — reaches CsrfDemoController.submit, banner renders
```
The session this needs to store the token in is exactly what `SecurityConfig`'s existing
`SessionCreationPolicy.IF_REQUIRED` (added for the OAuth2 handshake) already allows —
this demo needed no further session-policy change.

**User Story 3.2** — *As a developer, I want to understand and document CSRF and CORS
differences so that I can configure them correctly.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| CORS/CSRF interaction documented | ✅ New this pass — this section | See below |
| Practical demonstration, Postman + browser | ✅ Live-verified (curl stands in for both) | Epic 1.2 and Epic 3.1 sections above |

**CORS and CSRF solve different problems and don't substitute for each other.** CORS is
about *which origins a browser lets read a cross-origin response* — it protects the
*caller's* browser from a page silently exfiltrating data via `fetch`/`XHR` to a
different origin, entirely independent of whether the request itself is authenticated.
CSRF is about *which requests a server should trust as intentional* — it protects the
*server* from a browser automatically riding a victim's own cookie into a forged request
from another site, regardless of what CORS says. Concretely for this codebase:

- Every REST/GraphQL endpoint under `/api/**` and `/graphql`: **CORS on** (an allow-list,
  Epic 1.2) because real frontends on other origins do need to call it; **CSRF off**
  because bearer-JWT auth isn't cookie-based, so there's no ambient credential to forge.
  Note CORS and a forged same-origin-cookie request are actually independent axes — a
  CORS allow-list restricts what a *browser JS* can read back, but does nothing to stop a
  plain `<form>` POST (no `fetch`, no CORS preflight involved at all) from riding a
  cookie if one existed; that's specifically CSRF's job, and specifically why disabling
  it is safe here only *because* there's no cookie to ride.
- `/docs/csrf-demo/**`: CSRF **on** (Epic 3.1) because this one path is intentionally
  built to demonstrate cookie/session-backed form submission; CORS is irrelevant to it
  since it's a same-origin server-rendered form, not a cross-origin API call.
- `/oauth2/**`, `/login/oauth2/**` (Google's own handshake): needs a session
  (`SessionCreationPolicy.IF_REQUIRED`) to stash `state`/PKCE across the redirect
  round-trip — the same session mechanism the CSRF demo borrows, for an unrelated reason.

---

## Epic 4: OAuth2 and Role-Based Access Control (RBAC)

**User Story 4.1** — *As a user, I want to log in with my Google account so that I can
access the system without manual signup.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| OAuth2 login integrated using Google | ✅ New this pass | `spring-boot-starter-oauth2-client`, `SecurityConfig.securityFilterChain`'s `.oauth2Login(...)` |
| User details fetched from Google, persisted | ✅ New this pass | `AuthService.loginWithGoogle`/`createGoogleProvisionedUser` |
| Roles assigned after OAuth2 authentication | ⚠️ Deliberately no *default* role — see below | `AuthService.loginWithGoogle` |

`GET /oauth2/authorization/google` is Spring Security's own standard entry point — live
against the real registered client:
```
$ curl -i /oauth2/authorization/google
HTTP/1.1 302
Location: https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=134115004463-...&scope=openid%20profile%20email&state=...&redirect_uri=http://localhost:8080/login/oauth2/code/google&nonce=...&code_challenge=...&code_challenge_method=S256
```
Real client id, real PKCE challenge, real state/nonce — this redirects to Google for real
if followed in a browser. On the way back, `OAuth2LoginSuccessHandler` extracts
`email`/`name` from Google's own `OAuth2User` and calls `AuthService.loginWithGoogle`,
which finds the matching account by email or provisions one on the fly
(`createGoogleProvisionedUser` — random, never-usable BCrypt password hash, since the
column is `NOT NULL` but this account can only ever authenticate via Google unless it
later runs `/auth/forgot-password`; `emailVerifiedAt` set immediately, since Google's own
handshake is a *strictly stronger* proof of email ownership than this app's own
click-the-link flow). On success, redirects the browser to
`{frontendBaseUrl}/oauth2/callback?token=...`; on failure,
`?error=...` — a frontend single-page app reads either off the query string.

**"Roles assigned after successful OAuth2 authentication" — same no-default-role rule as
password self-registration, not an oversight.** A brand-new Google identity gets
provisioned with no role at all, and `completeLogin` throws the exact same
`"This account has no assigned role"` `UnauthorizedException` a brand-new
`POST /auth/register` account would. An administrator still grants a role via the
existing `POST /api/v1/users/{userId}/roles/{roleId}`, same as every other new account —
consistent with this codebase's existing "no self-service role" rule (see `CLAUDE.md`),
not a gap introduced by OAuth2. A pre-existing password account that logs in via Google
does get its `emailVerifiedAt` retroactively set if it was never verified, and otherwise
just completes login with whatever role it already holds.

**User Story 4.2** — *As an administrator, I want to restrict access to certain endpoints
based on user roles so that I can enforce role-based permissions.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Roles defined (`ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`) | ⚠️ Three of four — see Epic 1.1's honest gap note | `RoleName` enum |
| Endpoints annotated with `@PreAuthorize`/`@Secured` | ✅ New this pass | See Epic 1.1 above for the full list |
| Role-based access verified via Postman test cases | ✅ Live-verified (curl) | See Epic 1.1's 403/201 pair above |

---

## Epic 5: DSA and Security Optimization

**User Story 5.1** — *As a developer, I want to apply data structure and algorithm
principles to improve security and performance.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Hashing for password security and token validation | ✅ Already done | BCrypt (`PasswordEncoderConfig`); HMAC-SHA256 JWT signature (`JwtService`) |
| Caching/lookup map for temporary token storage or blacklisting | ✅ Already done | Redis-backed JTI blocklist |
| Optional in-memory map for revoked tokens | ⚠️ Redis, not a bare in-process `HashMap` — deliberately | See below |

`JwtService`'s logout flow adds a token's `jti` to a Redis set/key with a TTL equal to the
token's own remaining lifetime — an O(1) hash-map-style membership lookup
(`JwtAuthenticationFilter` checks it on every request) that self-expires instead of
needing a cleanup sweep. This satisfies the README's DSA ask (hash-based lookup for
revocation) using Redis instead of a literal in-process `HashMap` — deliberately: a bare
in-memory map is invisible to every other instance the moment this app runs behind more
than one replica (a token revoked via instance A would still validate against instance
B's own separate map), which defeats the point of revocation. Redis gives the same O(1)
lookup semantics the README is really asking for, correctly shared across replicas — a
strictly better answer to the same requirement, not a shortcut around it. This predates
this pass; documented here since v4 explicitly asks for it and no prior report called it
out this directly.

**User Story 5.2** — *As an auditor, I want to view and analyze security event logs so
that I can track login attempts and access patterns.*

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Auth success/failure logging | ✅ New this pass | `AuthService.logSecurityEvent`, `SystemLogWriter` |
| Token usage / endpoint access frequency reports | ✅ Already existed, now documented against this criterion | `@Timed("hms.rest.requests"/"hms.graphql.requests")` on every controller/resolver, `/actuator/metrics/...` |
| Logs reviewed to detect brute-force/unusual access | ✅ Live-verified | See below |

**Why a dedicated log, alongside the pre-existing generic one.** `LoggingAspect`'s own
failure-branch log deliberately never records argument values (to avoid ever leaking a
password from `LoginRequest`) — which means it structurally can't answer "which account
was *targeted*," exactly the signal brute-force detection needs. `AuthService.login`/
`loginWithGoogle` now wrap their whole body in try/catch and call
`SystemLogWriter.record(...)` directly on both branches — `"WARNING"` with the attempted
username/email and source IP on failure, `"INFO"` on success — coexisting with (not
replacing) `LoggingAspect`'s own generic log. `SystemLogWriter`'s own Javadoc documents
both callers now.

Live-verified — a real failed login, a real successful one, both queryable by an admin
(and *only* an admin — `SystemLogController` picked up the same `@PreAuthorize` treatment
as `RoleController`, since the log trail itself is sensitive):
```
$ curl -X POST /api/v1/auth/login -d '{"username":"admin","password":"WrongPassword"}'
< 401

$ curl /api/v1/system-logs?source=AuthService.login.security-event&sort=createdAt,desc&size=5 -H "Authorization: Bearer <admin token>"
{"logLevel":"WARNING","message":"Failed login: Invalid username or password for 'admin' from 0:0:0:0:0:0:0:1", ...}
{"logLevel":"INFO","message":"Successful login for 'receptionist1' from 0:0:0:0:0:0:0:1", ...}
{"logLevel":"INFO","message":"Successful login for 'admin' from 0:0:0:0:0:0:0:1", ...}

$ curl /api/v1/system-logs -H "Authorization: Bearer <receptionist token>"
< 403   # log trail is admin-only, confirmed
```
"Endpoint access frequency" specifically is answered by the pre-existing `@Timed` metrics
(`extraTags = {"layer","rest"}` on every REST controller, `"layer","graphql"` on every
resolver) rather than a bespoke new report — `/actuator/metrics/hms.rest.requests` already
gives per-tag hit counts and latency percentiles live; building a second, parallel
counting mechanism inside `SystemLogWriter` would just duplicate what Micrometer already
does correctly.

---

## What changed this pass — file index

| File | What |
|---|---|
| `pom.xml` | `spring-boot-starter-oauth2-client` |
| `.env` (gitignored) | `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, `APP_CORS_ALLOWED_ORIGINS` |
| `.gitignore` | `docs/client_secret_*.json` — the downloaded Google credentials file itself, never committed |
| `application.yaml` | `spring.security.oauth2.client.registration.google.*`, `app.cors-allowed-origins` |
| `config/security/SecurityConfig.java` | `@EnableMethodSecurity`, `CorsConfigurationSource` bean, `SessionCreationPolicy.IF_REQUIRED`, `.oauth2Login(...)`, `@Order(2)`; `PasswordEncoder` bean moved out |
| `config/security/PasswordEncoderConfig.java` | New — `PasswordEncoder` bean, extracted to break a circular bean dependency |
| `config/security/CsrfDemoSecurityConfig.java` | New — second `SecurityFilterChain`, `@Order(1)`, CSRF left on for `/docs/csrf-demo/**` |
| `config/security/OAuth2LoginSuccessHandler.java`, `OAuth2LoginFailureHandler.java` | New — bridge Google's handshake result into this app's own JWT issuance |
| `docs/CsrfDemoController.java` | New — the CSRF demo page's controller |
| `resources/templates/csrf-demo.html` | New — the CSRF demo page itself |
| `service/AuthService.java` | `loginWithGoogle`, `createGoogleProvisionedUser`, `uniqueUsernameFrom`, `completeLogin` (extracted, shared), `logSecurityEvent` |
| `aop/SystemLogWriter.java` | Javadoc updated — documents `AuthService` as a second caller alongside `LoggingAspect` |
| `config/security/PermissionExpressions.java` | New — the `permissionCheck` bean `@PreAuthorize`'s SpEL calls |
| `controller/RoleController.java`, `controller/SystemLogController.java` | `@PreAuthorize("@permissionCheck.has(resource, action)")` added alongside each method's existing `@RequirePermission` |
| `test/.../service/AuthServiceTest.java` | New tests: `loginWithGoogle` (new user, existing user, retroactive email verification, deactivated account, username-collision suffixing) and security-event logging on both `login`/`loginWithGoogle` |

**Full suite after every change in this pass: 852 tests, 0 failures, 0 errors, 2 skipped**
(re-run clean after both the circular-bean-dependency fix and the CSRF demo addition).
App boot itself was verified live (`Started HmsApplication in 16.134 seconds`) after the
security-bean wiring changed, not just compiled — the circular-dependency bug above was
only caught this way, since `mvn compile`/`mvn test` alone never construct the full
`ApplicationContext` the same way `spring-boot:run` does.
