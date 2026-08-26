# Prometheus Guide — How to Start It, and How JMeter/k6 Feed It

## What Prometheus is doing in this setup

Prometheus is a time-series database + query engine, not a load-testing tool itself —
it just collects and stores numbers over time so you can graph them. JMeter and k6 get
their metrics into it two completely different ways, because only one of them has any
native Prometheus support:

- **k6 pushes directly.** `k6.exe run --out experimental-prometheus-rw ...` (already
  built into `k6.cmd`) sends metrics straight to Prometheus's remote-write endpoint as
  the test runs. Nothing else needed on k6's side.
- **JMeter has no Prometheus output at all**, so `docs/v5/load-testing/jmeter_prometheus_exporter.py`
  bridges the gap: it re-reads JMeter's `.jtl` results file on every request and serves
  the totals as a real `/metrics` endpoint, which Prometheus then *scrapes* (pulls from,
  on a timer) the normal way. `jmeter.cmd` starts this exporter automatically.

Both end up in the same Prometheus, queryable together, which is the whole point —
`load-testing-report.md` already compares the two tools' results side by side; this
lets you *watch* both live instead of only reading the final summary.

## Starting everything, in order

**1. Start Prometheus first** — double-click `prometheus.cmd` (repo root), or run it
from a terminal. Leave the window open; it runs in the foreground. Wait for a line
like `Server is ready to receive web requests` before moving on. Open
`http://localhost:9090` to confirm — you should see Prometheus's own UI.

**2. Make sure the app itself is running** — `./mvnw spring-boot:run`, same as always.

**3. Run `jmeter.cmd` and/or `k6.cmd`** (repo root) — either one, or both, in either
order. Each fetches its own fresh JWT and starts on its own; you don't need to do
anything else first. `jmeter.cmd` additionally opens a *second* console window running
the Python exporter — leave that open too, for as long as you want JMeter's data
visible in Prometheus (k6 needs no equivalent window; it pushes directly while it
runs, and there's nothing left to serve once the run ends).

**4. Watch it happen live** — go back to `http://localhost:9090/graph`, and while the
test is running (or after — the data stays), enter a query and hit Execute. See below
for what to actually type.

## Querying the data — concrete examples

Prometheus's UI has a query box at the top of `/graph`. Type a query, hit Execute, and
switch to the "Graph" tab to see it plotted over time instead of just a single number.

**k6** (pushed metrics, prefixed `k6_`):
```
k6_http_reqs_total                              # running total of requests
rate(k6_http_reqs_total[10s])                    # requests/sec, smoothed over 10s
k6_http_req_duration_p99                         # p99 latency
k6_vus                                           # active virtual users right now
k6_listing_duration_p99                          # this project's own custom metric
k6_single_item_duration_p99                      # (see hms-load-test.js / k6-guide.md)
```

**JMeter** (via the exporter, prefixed `jmeter_`):
```
jmeter_requests_total                            # running total, per endpoint (the
                                                  # "label" tag is the sampler name,
                                                  # e.g. "GET doctors (list)")
rate(jmeter_requests_total[10s])                 # requests/sec, per endpoint
jmeter_elapsed_ms_sum / jmeter_requests_total     # live average response time, per
                                                  # endpoint (divide the two counters
                                                  # yourself -- there's no separate
                                                  # "average" metric)
jmeter_errors_total                              # running error count, per endpoint
jmeter_exporter_up                                # 1 if the exporter found a .jtl
                                                  # file at all, 0 if not (sanity check)
```

Add `{label="GET doctors (list)"}` after any `jmeter_*` metric name to filter to just
one endpoint instead of seeing all six lines at once — e.g.
`jmeter_elapsed_ms_sum{label="GET doctors (list)"} / jmeter_requests_total{label="GET doctors (list)"}`.

## Troubleshooting (things that actually happened while setting this up)

**Query returns nothing / empty graph.**
- Is Prometheus actually running? Check `http://localhost:9090/-/ready` returns `200`.
- For k6: did the run finish? A currently-in-progress run has already pushed *some*
  data, but if you started Prometheus *after* k6, anything pushed before Prometheus
  came up is gone (there's no re-send).
- For JMeter: is the exporter window still open? `jmeter.cmd` starts it in a new
  console window titled "JMeter Prometheus Exporter" — if that window got closed,
  Prometheus has nothing to scrape anymore. Just re-run `jmeter.cmd` (it'll start a
  fresh one), or run `python docs\v5\load-testing\jmeter_prometheus_exporter.py`
  yourself directly.
- Check Prometheus's own **Status → Targets** page (`http://localhost:9090/targets`)
  — the "jmeter" target should show state `UP`. If it shows `DOWN`, the exporter isn't
  reachable at `localhost:9270` at all.

**The JMeter exporter window shows a Python traceback and stops responding to some
requests.** This happened once for real while building this: Prometheus scrapes the
exporter every 2 seconds, which means it's very likely to read JMeter's `.jtl` file
*while JMeter is still writing to it* — occasionally catching a half-written last
line. The exporter now skips any row it can't parse instead of crashing (fixed in
`jmeter_prometheus_exporter.py`) — if you see this again, it's the same class of
issue and the fix is the same: make row-parsing tolerant of a torn last line, don't
trust every field to be present.

**"Only one usage of each socket address ... is normally permitted" / a script exits
immediately with 0 requests made.** This is a port already in use. `jmeter.cmd` now
checks port 9270 first and skips starting a second exporter if one's already running
(you'll see "Prometheus exporter already running... not starting another" instead of
an error) — but if you ever add another `-o`/output tied to a fixed port yourself,
check `netstat -ano | findstr :<port>` first. (k6's own optional live web-dashboard
output hit exactly this problem on port 9092 during setup — badly enough that it
aborted the *entire* test run, not just the dashboard — which is why `k6.cmd`
deliberately doesn't use it; Prometheus is the live-view mechanism here instead.)

**Multiple exporter windows/processes piling up.** Each `jmeter.cmd` run used to start
a brand new exporter without checking for an existing one. Fixed the same way as the
port-conflict case above — it now checks first.

## Where this fits with the rest of the v5 docs

- [`load-testing-report.md`](load-testing-report.md) — the actual numbers from a real
  run of both tools, analyzed and compared. This guide is about the *tooling*, not the
  results.
- [`k6-guide.md`](k6-guide.md) — the k6 side specifically: how the script itself works,
  how to run it standalone, how to read its own console summary.
- `docs/v5/load-testing/hms-load-test.jmx` / `hms-load-test.js` — the actual test
  definitions.
- `docs/v5/load-testing/prometheus.yml` — this project's own Prometheus config
  (`prometheus.cmd` points Prometheus at this file, not the install's default one).
