// HMS load test — k6.
//
// Same endpoints and same "pre-fetch the JWT once, reuse across virtual users" design
// as hms-load-test.jmx (see that file's header comment for why) — this and the JMeter
// plan are meant to be run against the same load pattern so their results are directly
// comparable, not two unrelated tests.
//
// Run:
//   k6 run --env AUTH_TOKEN=<jwt> --env PATIENT_ID=<uuid> --env DOCTOR_ID=<uuid> hms-load-test.js
//
// Optional env vars: BASE_URL (default http://localhost:8080), VUS (default 50),
// RAMP_UP/STEADY/RAMP_DOWN (default 10s/40s/10s, matching the JMeter plan's default
// threads=50/rampup=10/duration=60 as closely as k6's stage model allows).

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.AUTH_TOKEN;
const PATIENT_ID = __ENV.PATIENT_ID;
const DOCTOR_ID = __ENV.DOCTOR_ID;
const VUS = Number(__ENV.VUS || 50);

if (!TOKEN) {
  throw new Error('AUTH_TOKEN env var is required — obtain one via POST /api/v1/auth/login first');
}

// Per-endpoint latency, broken out separately from k6's built-in aggregate
// http_req_duration — lets the report compare listing vs. single-item endpoints the
// same way jprofiler-report.md's HTTP-probe table does.
const listingDuration = new Trend('listing_duration', true);
const singleItemDuration = new Trend('single_item_duration', true);

export const options = {
  stages: [
    { duration: `${__ENV.RAMP_UP || '10s'}`, target: VUS },
    { duration: `${__ENV.STEADY || '40s'}`, target: VUS },
    { duration: `${__ENV.RAMP_DOWN || '10s'}`, target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const headers = { headers: { Authorization: `Bearer ${TOKEN}`, Accept: 'application/json' } };

const LISTINGS = [
  `${BASE_URL}/api/v1/doctors?page=0&size=20`,
  `${BASE_URL}/api/v1/patients?page=0&size=20`,
  `${BASE_URL}/api/v1/appointments?page=0&size=20`,
  `${BASE_URL}/api/v1/users?page=0&size=20`,
];

const SINGLE_ITEM = [
  `${BASE_URL}/api/v1/patients/${PATIENT_ID}`, // CompletableFuture 9-lookup fan-out
  `${BASE_URL}/api/v1/doctors/${DOCTOR_ID}`,   // @Cacheable("doctors")
];

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
  sleep(0.5);
}
