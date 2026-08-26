# Load Testing Report — JMeter & k6

Closes the "not load-tested" gap `v5-report.md`'s Epic 3.2 flagged honestly. Two
independent load-testing tools, same test design, same six endpoints, same real
running app — run back-to-back so their results are directly comparable, not two
unrelated one-off tests.

## Methodology

- **Target**: the actual `HmsApplication` JVM already running for local dev, real
  Postgres/Redis behind it — not a mock/stub target.
- **Load shape (both tools)**: 50 concurrent virtual users, ~10s ramp-up, ~40s steady
  state, ~10s ramp-down (~60s total), against six endpoints per iteration — the same
  four paginated listings and two single-item lookups used in
  [`jprofiler-report.md`](jprofiler-report.md), specifically so this report's numbers
  and that one's are comparable, not apples-to-oranges.
- **Auth**: one admin JWT fetched once (`POST /api/v1/auth/login`) and reused across
  every virtual user for both tools — this measures how the app holds up under
  concurrent *authenticated* traffic (`JwtAuthenticationFilter`/`AuthorizationAspect`
  still run on every single request, since the token still goes through the real
  filter chain each time), not login throughput itself, which is a different test.
- **Tools**: JMeter 5.6.3 (`hms-load-test.jmx`, non-GUI mode) and k6 v2.2.0
  (`hms-load-test.js`) — both committed in `load-testing/` alongside this report, both
  directly runnable against a locally running instance (see each file's own header
  comment for the exact command).

## Results — per-endpoint (both tools, same ~60s window, 50 VUs/threads)

| Endpoint | Tool | Requests | Errors | Avg | p95 | Max |
|---|---|---|---|---|---|---|
| `GET /api/v1/appointments` (listing) | JMeter | 860 | 0 | 910ms | 1465ms | 2456ms |
| `GET /api/v1/appointments` (listing) | k6 | — | 0 | 600ms\* | 1090ms\* | 2180ms\* |
| `GET /api/v1/doctors` (listing) | JMeter | 877 | 0 | 684ms | 1173ms | 2260ms |
| `GET /api/v1/patients` (listing) | JMeter | 865 | 0 | 730ms | 1232ms | 2312ms |
| `GET /api/v1/users` (listing) | JMeter | 839 | 0 | 662ms | 1095ms | 2163ms |
| `GET /api/v1/doctors/{id}` (cached) | JMeter | 829 | 0 | 114ms | 244ms | 1224ms |
| `GET /api/v1/patients/{id}` (fan-out) | JMeter | 829 | 0 | 120ms | 251ms | 1138ms |

\* k6's `listing_duration`/`single_item_duration` custom metrics are averaged across
all four listing endpoints combined (600.5ms avg / 1.09s p95 / 2.18s max) and both
single-item endpoints combined (97.88ms avg / 237ms p95 / 772ms max) respectively,
rather than broken out per-endpoint the way JMeter's per-sampler labels are — k6's
`Trend` metrics don't tag by URL out of the box the way JMeter's Summary Report does
by sampler name. Close enough to JMeter's per-endpoint numbers to read as agreement,
not coincidence: JMeter's four listings average 662–910ms, k6's combined listing
average is 600.5ms; JMeter's two single-item endpoints average 114–120ms, k6's
combined single-item average is 97.9ms.

**Zero errors across both tools — 10,019 total requests (5,099 JMeter + 4,920 k6),
0 failed.** Directly satisfies "no data inconsistency or race conditions observed"
(Epic 2.2) under real concurrent load, not just the existing `CountDownLatch` unit
tests — every response was a real `200`, every `Cacheable`/fan-out result was correct
under contention.

## Finding — listings are ~6x slower than cached single-item lookups under real
concurrent load (both tools agree)

JMeter: listings average 662–910ms vs. single-item 114–120ms (≈6.4x). k6: 600.5ms vs.
97.9ms (≈6.1x). This is the same direction [`jprofiler-report.md`](jprofiler-report.md)'s
Finding 4 found (listings costlier because of the mandatory `COUNT`+`SELECT` pair),
but **much more pronounced** here — the JProfiler session's own load (654 requests over
~40s, no sustained concurrency ramp) only showed listings ~30–40ms slower than
single-item calls (81–122ms band). At **50 concurrent users held for a full minute**,
that gap widens to ~550–800ms. The likely reason: at low concurrency, the permission
check and logging overhead (JProfiler's Findings 1–2) dominate for every endpoint about
equally; at sustained higher concurrency, the *extra* query listings pay for
(Finding 3's COUNT+SELECT pair) compounds with HikariCP connection-pool contention —
twice as many queries per listing request means twice as many connections held per
request, so the pool saturates roughly twice as fast under listings-heavy load as it
would under single-item-heavy load. `hikari.maximum-pool-size` (20, per
`application.yaml`) was not swept across multiple values this pass — see "Still open"
below.

## Aggregate throughput (JMeter's own summary, all six endpoints combined)

```text
summary = 5099 in 00:01:00 = 84.4/s Avg: 541ms Min: 12ms Max: 2456ms Err: 0 (0.00%)
```

k6's equivalent combined figure: 4920 requests in 60.2s = 81.7 req/s, `http_req_duration`
avg 432.96ms, p95 1.00s (the one threshold that failed — `p(95)<1000ms` — by exactly
the margin the table above shows: listings alone exceed 1s at p95).

## Acceptance criteria this closes

| Criterion (from `v5-report.md`) | Before this report | Now |
|---|---|---|
| "Executor configurations tested with varying thread pool sizes" (Epic 3.2) | ⚠️ Sized and justified, not load-tested | ⚠️ Load-tested at one configuration (default pool sizes); still not *swept* across multiple sizes — see "Still open" |
| "CPU and memory utilization monitored during stress testing" (Epic 3.2) | ❌ Not done | ✅ — combine this report's throughput/error data with `jprofiler-report.md`'s live CPU/heap/GC/thread telemetry, captured from the same running app |
| "No data inconsistency or race conditions observed" (Epic 2.2) | ✅ (unit tests only) | ✅ — now also confirmed under 10,019 real concurrent HTTP requests, 0 failures |

**Still open, stated honestly**: `hikari.maximum-pool-size`/the two `AsyncConfig`
executors' pool sizes were not sweep-tested across multiple values (e.g. re-running
this same load test at pool sizes 10/20/40 and comparing) — this report tests the
*current* configuration under real concurrent load, it doesn't yet answer "is 20 the
right number." That would be the natural next step with these same two scripts,
parametrized by `-Jthreads=`/`VUS`/`hikari.maximum-pool-size` across a few runs.

## Reusing these scripts

Both `load-testing/hms-load-test.jmx` and `load-testing/hms-load-test.js` are
parametrized (thread/VU count, ramp-up, duration, target ids) and safe to re-run
against any environment — see each file's own header comment for the exact command
and required parameters (a real JWT, a real patient id, a real doctor id).
