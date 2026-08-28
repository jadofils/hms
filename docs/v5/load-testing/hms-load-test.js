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
// threads=50/rampup=10/duration=60 as closely as k6's stage model allows),
// CREATE_APPOINTMENTS (default false — see the write-path section below).
//
// IMPORTANT before running this against a live app:
// - RateLimitFilter (see config/RateLimitFilterConfig.java) enforces a global
//   per-client-IP request limit (100 requests/60s by default) ahead of every endpoint.
//   This test deliberately drives far more than that from one machine — set
//   APP_RATE_LIMIT_ENABLED=false (or raise APP_RATE_LIMIT_MAX_REQUESTS way up) in .env
//   before running, then restore it afterward, or every VU will start seeing 429s
//   partway through the run instead of the real latency this test is trying to measure.
// - CREATE_APPOINTMENTS=true makes every iteration create a real appointment, which
//   publishes AppointmentCreatedEvent and dispatches a real confirmation email via
//   MailEventListener/mailTaskExecutor — at 50 VUs that's a real burst of outbound Gmail
//   SMTP traffic against whatever GMAIL_USERNAME is configured. Leave it off (default)
//   unless you specifically want to load-test that async mail path too.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.AUTH_TOKEN;
const PATIENT_ID = __ENV.PATIENT_ID;
const DOCTOR_ID = __ENV.DOCTOR_ID;
const VUS = Number(__ENV.VUS || 50);
const CREATE_APPOINTMENTS = (__ENV.CREATE_APPOINTMENTS || 'false').toLowerCase() === 'true';

if (!TOKEN) {
  throw new Error('AUTH_TOKEN env var is required — obtain one via POST /api/v1/auth/login first');
}

// Per-endpoint latency, broken out separately from k6's built-in aggregate
// http_req_duration — lets the report compare listing vs. single-item endpoints the
// same way jprofiler-report.md's HTTP-probe table does.
const listingDuration = new Trend('listing_duration', true);
const singleItemDuration = new Trend('single_item_duration', true);
const writeDuration = new Trend('write_duration', true);

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

const AUTH_HEADERS = { Authorization: `Bearer ${TOKEN}`, Accept: 'application/json' };
const headers = { headers: AUTH_HEADERS };
const jsonHeaders = { headers: { ...AUTH_HEADERS, 'Content-Type': 'application/json' } };

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

// Digits-only, unique per (VU, iteration) — same idea as AbstractControllerTest's
// uniqueDigits helper on the Java side (JUnit controller tests), for the same reason:
// PatientRequest.phone/email are unique-constrained columns, so every write this test
// generates needs its own non-colliding value. __VU/__ITER are k6 globals (current
// virtual-user number / this VU's iteration count); putting the timestamp first and
// __VU/__ITER last means the trailing digits — the ones `slice` keeps — always include
// per-VU-per-iteration entropy, not just wall-clock time shared by every VU that
// iterates in the same millisecond.
function uniqueDigits(len) {
  const raw = `${Date.now()}${__VU}${__ITER}`;
  return raw.slice(-len).padStart(len, '0');
}

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

  // Write path — generates fresh data every iteration instead of only ever reading the
  // same two fixed rows above, so this test also exercises DB inserts, HikariCP
  // checkout, and cache eviction under the same concurrent load, not just cache hits.
  const patientPayload = JSON.stringify({
    firstName: 'LoadTest',
    lastName: 'Patient',
    dob: '1990-01-01',
    gender: 'F',
    phone: `07${uniqueDigits(7)}`,
    email: `loadtest${uniqueDigits(6)}@example.com`,
    address: '123 Load Test St',
  });
  const patientRes = http.post(`${BASE_URL}/api/v1/patients`, patientPayload, jsonHeaders);
  check(patientRes, { 'patient create status is 201': (r) => r.status === 201 });
  writeDuration.add(patientRes.timings.duration);

  // Opt-in only — see the header comment above for why (real outbound email per
  // appointment created). Reuses the freshly-created patient above and the DOCTOR_ID
  // this run was given.
  if (CREATE_APPOINTMENTS && patientRes.status === 201) {
    const patientId = patientRes.json('data.patientId');
    const appointmentPayload = JSON.stringify({
      patientId,
      doctorId: DOCTOR_ID,
      appointmentDate: '2099-01-01T10:00:00',
      reason: 'Load test checkup',
    });
    const appointmentRes = http.post(`${BASE_URL}/api/v1/appointments`, appointmentPayload, jsonHeaders);
    check(appointmentRes, { 'appointment create status is 201': (r) => r.status === 201 });
    writeDuration.add(appointmentRes.timings.duration);
  }

  sleep(0.5);
}
