# k6 Guide — How It Works and How to Use It

## What k6 actually is

k6 is a load-testing tool where the test itself is a small JavaScript program, not a
GUI-built test plan (that's JMeter's model — see `hms-load-test.jmx` for the same test
built that way). You write a script with a `default function` that represents "one
iteration of one virtual user," and k6 runs many copies of that function concurrently
according to a `stages` schedule you define. It's a single self-contained binary
(`k6.exe`, no JVM, no separate install of a scripting language) that prints a real-time
progress bar while running and a metrics summary at the end.

**Why both k6 and JMeter exist in this project**: they're deliberately built to the
*same* test design (see [`load-testing-report.md`](load-testing-report.md)'s
Methodology) — same six endpoints, same 50-VU/50-thread load shape, same pre-fetched
JWT — so their results can be cross-checked against each other, not because one
replaces the other. JMeter's GUI-oriented `.jmx` format is the more traditional/
enterprise-standard tool named explicitly in this project's own Technical Requirements
table; k6 is a newer, code-first alternative increasingly common in real teams because
the test itself is a readable, version-controllable script instead of a large XML file.

## How `hms-load-test.js` actually works

The whole file is at `docs/v5/load-testing/hms-load-test.js`. Walking through it:

```js
export const options = {
  stages: [
    { duration: '10s', target: 50 },  // ramp up to 50 VUs over 10s
    { duration: '40s', target: 50 },  // hold 50 VUs for 40s
    { duration: '10s', target: 0 },   // ramp down to 0 over 10s
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],      // fail the run if >1% of requests error
    http_req_duration: ['p(95)<1000'],   // fail the run if 95th-percentile latency > 1s
  },
};
```
`stages` is k6's schedule for how many concurrent "virtual users" (VUs) are active at
each point in time — this is the direct equivalent of JMeter's thread group
`num_threads`/`ramp_time`/`duration`, just expressed as a timeline instead of three
separate numbers. `thresholds` are pass/fail conditions checked automatically at the
end — this is why a k6 run can exit with a **non-zero exit code even though every
single request succeeded**: it did, right up until the `p(95)<1000` threshold got
breached (see the actual results in `load-testing-report.md` — this happened for real).
That's k6 telling you "the test itself completed, but the performance you asked for
wasn't met," not a script failure.

```js
export default function () {
  for (const url of LISTINGS) {
    const res = http.get(url, headers);
    check(res, { 'listing status is 200': (r) => r.status === 200 });
    listingDuration.add(res.timings.duration);
  }
  for (const url of SINGLE_ITEM) {
    const res = http.get(url, headers);
    check(res, { 'single-item status is 200': (r) => r.status === 200 });
    singleItemDuration.add(res.timings.duration);
  }

  // Write path — a fresh patient every iteration (uniqueDigits keeps phone/email
  // non-colliding), so the run also exercises DB inserts/HikariCP checkout, not just
  // cache-hit reads.
  const patientRes = http.post(`${BASE_URL}/api/v1/patients`, patientPayload, jsonHeaders);
  check(patientRes, { 'patient create status is 201': (r) => r.status === 201 });
  writeDuration.add(patientRes.timings.duration);

  // Opt-in only (CREATE_APPOINTMENTS=true) — creates an appointment for that new
  // patient too, which additionally exercises the AppointmentCreatedEvent → async mail
  // path (mailTaskExecutor). Off by default since it sends a real email per iteration.
  if (CREATE_APPOINTMENTS) { /* ... */ }

  sleep(0.5);
}
```
This function is **one iteration of one virtual user**. Every active VU runs this
function in a loop for the whole test duration: hit all four listing endpoints, hit
both single-item endpoints, create a new patient, optionally create an appointment for
it, sleep half a second, repeat. `check(...)` records a pass/fail assertion (shows up
in the summary as `checks_succeeded`/`checks_failed`) — it doesn't stop the test on
failure, it just counts it. `listingDuration`/`singleItemDuration`/`writeDuration` are
custom metrics (k6's `Trend` type) added specifically so this project's report can
compare listing vs. single-item vs. write latency directly, the same distinction
`jprofiler-report.md`'s HTTP-probe table draws — a write (DB insert, possibly a cache
eviction) is a meaningfully different cost profile than either kind of read.

Auth: the script takes a pre-fetched JWT via `--env AUTH_TOKEN=...` rather than logging
in itself inside the VU loop — see the file's own header comment for why (this measures
concurrent *authenticated* traffic, not login throughput, which would be a different
test with a different bottleneck profile entirely).

**Before running this against a live app**, two things the script's own header comment
also calls out:
- `RateLimitFilter` enforces a global per-client-IP request limit (100 req/60s by
  default) ahead of every endpoint — this test deliberately drives far more than that
  from one machine. Set `APP_RATE_LIMIT_ENABLED=false` (or raise
  `APP_RATE_LIMIT_MAX_REQUESTS`) in `.env` first, then restore it afterward, or the run
  starts seeing `429`s partway through instead of the real latency being measured.
- `CREATE_APPOINTMENTS=true` dispatches a real confirmation email per iteration via
  `MailEventListener`/`mailTaskExecutor` — a real burst of outbound Gmail SMTP traffic
  at 50 VUs. Leave it at the default (`false`) unless you specifically want to load-test
  that async mail path too.

## How to run it

### The easy way — `k6.cmd`

From the repo root, double-click `k6.cmd` (or run it from a terminal). It:
1. Confirms the app is reachable and `k6.exe` is where `.env`'s `K6_HOME` says it is.
2. Fetches a fresh admin JWT and a real patient/doctor id automatically (via
   `docs/v5/load-testing/fetch-test-params.ps1` — a JWT expires, so this can't be a
   static value in `.env` the way `K6_HOME` is).
3. Runs `hms-load-test.js` with k6's **live web dashboard** open at
   `http://localhost:9092` — open that URL in a browser *while the script is running*
   to watch requests/sec, latency, and error rate update in real time, instead of only
   seeing the final summary after it's done.
4. Saves a static HTML snapshot of that dashboard to
   `target\load-test-results\k6-dashboard_<timestamp>.html` when the run finishes, and
   prints the summary to the console.

Optional overrides (set as environment variables before running `k6.cmd`):
`VUS` (default 50), `RAMP_UP`/`STEADY`/`RAMP_DOWN` (default `10s`/`40s`/`10s`),
`DASHBOARD_PORT` (default `9092`), `CREATE_APPOINTMENTS` (default `false`).

### Running it directly yourself

```
"C:\Program Files\k6\k6.exe" run ^
  --env AUTH_TOKEN=<a real JWT from POST /api/v1/auth/login> ^
  --env PATIENT_ID=<a real patient id> ^
  --env DOCTOR_ID=<a real doctor id> ^
  docs\v5\load-testing\hms-load-test.js
```
Add `--env VUS=20` (or any of the other env vars above) to change the load shape
without editing the script. Add `--out web-dashboard` yourself if you want the live
dashboard without going through `k6.cmd` (set `K6_WEB_DASHBOARD_PORT` first if you
don't want the default port 5665).

## Reading the output

k6 prints a `THRESHOLDS` section first (pass `✓`/fail `✗` per threshold), then
`TOTAL RESULTS`:

```
checks_total.......: 4920    81.684669/s
checks_succeeded...: 100.00% 4920 out of 4920

CUSTOM
listing_duration...............: avg=600.5ms  ... p(95)=1.09s
single_item_duration...........: avg=97.88ms  ... p(95)=237.15ms

HTTP
http_req_duration..............: avg=432.96ms ... p(95)=1s
http_req_failed................: 0.00%  0 out of 4920

EXECUTION
iterations.....................: 820    13.614111/s
vus............................: 3      min=3         max=50
```
- **`checks_*`** — your `check()` assertions (correctness, not performance): 100% here
  means every response was a real `200`, not a partial failure.
- **`http_req_duration`** — the built-in latency metric across every request k6 made,
  with `avg`/`min`/`med`/`max`/percentiles. `p(95)` is the number the `thresholds`
  block judges.
- **`http_req_failed`** — network/HTTP-level failures (connection refused, 5xx, etc.) —
  separate from `checks`, which only fires when your script explicitly checks something.
- **`iterations`** — how many full passes through the `default function` completed
  (each iteration = 7 requests with `CREATE_APPOINTMENTS=false` — the default — or 8
  with it `true`: 4 listings + 2 single-item reads + 1 patient create [+ 1 appointment
  create]).
- **`vus`** — concurrent virtual users at the moment the summary was taken; `vus_max`
  is the ceiling `options.stages` reached.

## Full results and analysis

The actual numbers from a real run against this app, compared side-by-side with
JMeter's results and interpreted, live in [`load-testing-report.md`](load-testing-report.md) —
this guide is about the tool and the script; that report is about what the numbers
mean.
