#!/usr/bin/env python3
"""JMeter -> Prometheus bridge.

JMeter has no built-in Prometheus output (unlike k6, which pushes metrics to
Prometheus directly via remote-write). This exposes a real Prometheus /metrics
endpoint by re-parsing the newest .jtl results file under target/load-test-results
on every scrape -- so a run already in progress (jmeter.cmd writes rows to the .jtl
file continuously as it runs, not just at the end) shows up live, not just after the
run finishes.

Run this BEFORE or DURING a jmeter.cmd run, and leave it running -- Prometheus (see
docs/v5/load-testing/prometheus.yml's "jmeter" scrape job) polls it every 2s.

Usage:
    python jmeter_prometheus_exporter.py [--port 9270] [--results-dir ../../../target/load-test-results]

Stdlib only, no dependencies -- matches this project's other Python-free tooling
convention as closely as a Python script can (no pip install needed to run it).
"""

import argparse
import csv
import glob
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "..", "target", "load-test-results")


def latest_jtl(results_dir):
    files = glob.glob(os.path.join(results_dir, "*.jtl"))
    if not files:
        return None
    return max(files, key=os.path.getmtime)


def parse_jtl(path):
    """Returns {label: {"count": n, "errors": n, "elapsed_sum_ms": n}}."""
    stats = {}
    try:
        with open(path, newline="", encoding="utf-8", errors="replace") as f:
            reader = csv.DictReader(f)
            for row in reader:
                # JMeter is often still appending to this exact file while a scrape
                # happens (that's the whole point -- live results, not just after the
                # run finishes), so the last line read can be a torn/partial write:
                # csv.DictReader fills any column the line is short on with None (the
                # key is still present, so row.get(key, default) does NOT fall back --
                # `or` handles both "key missing" and "key present but None/empty").
                # Skip the row entirely if it's too mangled to make sense of at all.
                try:
                    label = row.get("label") or "unknown"
                    elapsed = row.get("elapsed") or "0"
                    success = row.get("success") or "true"
                    entry = stats.setdefault(label, {"count": 0, "errors": 0, "elapsed_sum_ms": 0})
                    entry["count"] += 1
                    entry["elapsed_sum_ms"] += int(elapsed) if elapsed.isdigit() else 0
                    if success.strip().lower() != "true":
                        entry["errors"] += 1
                except (ValueError, AttributeError):
                    continue
    except (FileNotFoundError, OSError):
        pass
    return stats


def render_prometheus_text(stats, source_file):
    lines = []
    lines.append("# HELP jmeter_requests_total Total requests seen so far in the current/latest .jtl file")
    lines.append("# TYPE jmeter_requests_total counter")
    for label, s in stats.items():
        lines.append('jmeter_requests_total{label="%s"} %d' % (label.replace('"', "'"), s["count"]))

    lines.append("# HELP jmeter_errors_total Failed requests so far in the current/latest .jtl file")
    lines.append("# TYPE jmeter_errors_total counter")
    for label, s in stats.items():
        lines.append('jmeter_errors_total{label="%s"} %d' % (label.replace('"', "'"), s["errors"]))

    lines.append("# HELP jmeter_elapsed_ms_sum Sum of response times (ms) so far -- divide by jmeter_requests_total for a live average")
    lines.append("# TYPE jmeter_elapsed_ms_sum counter")
    for label, s in stats.items():
        lines.append('jmeter_elapsed_ms_sum{label="%s"} %d' % (label.replace('"', "'"), s["elapsed_sum_ms"]))

    lines.append("# HELP jmeter_exporter_up 1 if a .jtl results file was found, 0 otherwise")
    lines.append("# TYPE jmeter_exporter_up gauge")
    lines.append("jmeter_exporter_up %d" % (1 if source_file else 0))

    return "\n".join(lines) + "\n"


def make_handler(results_dir):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/metrics":
                self.send_response(404)
                self.end_headers()
                return
            source = latest_jtl(results_dir)
            stats = parse_jtl(source) if source else {}
            body = render_prometheus_text(stats, source).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            pass  # quiet -- Prometheus scrapes every 2s, would otherwise flood the console

    return Handler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=9270)
    parser.add_argument("--results-dir", default=RESULTS_DIR)
    args = parser.parse_args()

    results_dir = os.path.abspath(args.results_dir)
    print(f"Serving Prometheus metrics on http://localhost:{args.port}/metrics")
    print(f"Watching for the newest *.jtl file under: {results_dir}")
    server = HTTPServer(("localhost", args.port), make_handler(results_dir))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopped.")
        sys.exit(0)


if __name__ == "__main__":
    main()
