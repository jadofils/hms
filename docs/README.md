# Docs

One file per user story from the project's `ReadMe.md` (the delivery-criteria doc),
explaining what the story asked for, how it was achieved, and exactly where in the
codebase — plus a credentials reference for the seeded demo accounts. This is the
original/base pass (Epics 1–5 below); each later pass has its own requirements doc and
implementation report, grouped by version:

| Pass | Requirements | Implementation report | Focus |
|---|---|---|---|
| v2 | [`v2/Readme-v2.md`](v2/Readme-v2.md) | [`v2/v2-report.md`](v2/v2-report.md) | Spring Data JPA, transactions, query optimization, caching |
| v4 | [`v4/README-V4.MD`](v4/README-V4.MD) | [`v4/v4-report.md`](v4/v4-report.md) | Spring Security, JWT, Google OAuth2, RBAC |
| v5 | [`v5/ReadMe-v5.md`](v5/ReadMe-v5.md) | [`v5/v5-report.md`](v5/v5-report.md) | Async/`CompletableFuture`, events, connection pooling, `@EntityGraph` |

Each report maps every Epic/User Story in its own requirements doc to what's actually
implemented, where, and what was verified live — not just what the code happens to
contain. (Note: "v2"/"v4"/"v5" name sequential passes over this project, each with their
own internal Epic/Story numbering — unrelated to the *base* pass's own Epic 4 = GraphQL
below, which predates and has nothing to do with the "v4" *security* pass.)

| Story | Epic | Status |
|---|---|---|
| [1.1 — Application Setup](story-1.1-setup.md) | 1: Setup | ✅ Done (DI: constructor-based, with field/setter alternatives compared) |
| [2.1 — Admin CRUD](story-2.1-admin-crud.md) | 2: REST API | ✅ Done |
| [2.2 — Receptionist view/sort/filter](story-2.2-receptionist-filtering.md) | 2: REST API | ✅ Done — algorithms + pagination-strategy detail, performance report: [performance-report.md](performance-report.md) |
| [3.1 — Validation, exceptions, docs](story-3.1-validation-docs.md) | 3: Validation & Docs | ✅ Done |
| [4.1 — GraphQL](story-4.1-graphql.md) | 4: GraphQL | ✅ Done |
| [5.1 — AOP logging/monitoring](story-5.1-aop-logging.md) | 5: Cross-Cutting (AOP) | ✅ Done — blanket service-layer coverage confirmed |

## CORS and CSRF — how they interact in this codebase

(HMS v4, User Story 3.2 — full detail, live-verified evidence, and the Postman/browser
artifacts below live in [`v4/v4-report.md`](v4/v4-report.md); this section is the
required-in-README summary.) CORS and CSRF solve different problems and don't substitute
for each other:

- **CORS** governs *which origins a browser lets JavaScript read a cross-origin response
  from* — it protects the *caller's* browser from a page silently exfiltrating data via
  `fetch`/XHR to a different origin. It says nothing about whether a request is
  authenticated or forged.
- **CSRF** governs *which requests a server should trust as intentional* — it protects
  the *server* from a browser automatically riding a victim's own session cookie into a
  forged request from another site. It's irrelevant to CORS's own concern.

Concretely, in this app:

| Surface | CORS | CSRF | Why |
|---|---|---|---|
| `/api/**`, `/graphql` | On — explicit allow-list (`SecurityConfig.corsConfigurationSource`, `app.cors-*`) | Off (`.csrf(csrf -> csrf.disable())`) | Real frontends on other origins need to call these; bearer-JWT auth isn't cookie-based, so a forged cross-site request has no ambient credential to ride in the first place |
| `/docs/csrf-demo/**` | Irrelevant (same-origin, server-rendered form, not an API call) | On — Spring Security's session-backed default, simply never disabled for this one path (`CsrfDemoSecurityConfig`) | Demonstrates the token mechanism on the one style of endpoint (cookie/session-backed forms) where it actually matters |
| `/oauth2/**`, `/login/oauth2/**` | N/A (Google's own redirect handshake, not a fetch/XHR call) | N/A | Needs `SessionCreationPolicy.IF_REQUIRED` to stash `state`/PKCE across the redirect round-trip — unrelated to either mechanism above |

A CORS allow-list and CSRF protection are independent axes: an allow-list restricts what
browser JS can read back, but does nothing to stop a plain `<form>` POST (no `fetch`, no
CORS preflight involved at all) from riding a session cookie if one existed — that's
specifically CSRF's job, which is exactly why disabling it on `/api/**` is safe only
*because* there's no cookie-based session to ride there.

**Practical demonstration, Postman + browser** (real artifacts, not curl standing in):
- [`v4/postman/HMS-CORS-CSRF-Demo.postman_collection.json`](v4/postman/HMS-CORS-CSRF-Demo.postman_collection.json)
  — import into Postman: a CORS folder (preflight against an allowed vs. disallowed
  origin) and a CSRF folder (get a real token via the demo page, submit with it, submit
  without it).
- [`v4/cors-browser-test.html`](v4/cors-browser-test.html) — a standalone page you serve
  from a different origin/port so a real browser's own same-origin policy is what's being
  exercised, not just header inspection (which is all Postman itself can do — it doesn't
  enforce CORS the way a browser does).
- `/docs/csrf-demo` (the running app itself) is the real browser client for the CSRF half
  — two forms, one with a valid token (succeeds), one without (rejected with `403` by
  `CsrfFilter` before any controller runs).

Also in this folder:
- [`credentials.md`](credentials.md) — the 5 seeded demo accounts (`DataSeeder`), their
  passwords, and what each role is granted.
- [`annotations-reference.md`](annotations-reference.md) — all seven custom annotations
  (`@RequirePermission`, `@ApplyAlgorithm`, `@FindUserData`, `@SqlQueryBuilder`,
  `@SendTemplatedEmail`, `@ScheduledMaintenance`, `@Subscribe`), what each does, which
  aspect/processor intercepts it, and — as of this pass — every real caller for every
  one of them; none is left as documented-but-uncalled infrastructure any more.
- [`clean-code-testing-report.md`](clean-code-testing-report.md) — a real local SonarQube +
  JaCoCo analysis run against this codebase: test-suite health (808 tests), coverage (93.8%
  lines, 100% classes), quality gate, code smells triaged by actual severity, and the AOP
  layer's own coverage story.
- [`performance-report.md`](performance-report.md) — covers both performance-relevant
  deliverables in the README's Technical Requirements table: **DSA Integration**
  (merge sort/binary search — complexity, implementation, every real caller, and why a
  hand-rolled version instead of `Collections.sort`/`Arrays.binarySearch`), **pagination
  strategy** (offset/page-number, compared head-to-head against cursor/keyset
  pagination), and the **REST vs GraphQL analysis** named in `ReadMe.md`'s Deliverables
  table — real HTTP-round-trip latency/throughput measurements (not a database-indexing
  study) for 8 operations, each hitting the same service layer and the same PostgreSQL
  data via both styles, generated by `RestVsGraphQlBenchmarkTest` (disabled by default —
  see that class's Javadoc to regenerate it).
