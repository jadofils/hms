// HMS load test — full user journey (login -> role-appropriate actions -> logout).
//
// Different test design from hms-load-test.js/hms-load-test.jmx on purpose: those two
// pre-fetch ONE shared admin JWT up front and never touch /auth/login in the loop, so
// they isolate pure post-auth API throughput (comparable to each other, see
// load-testing-report.md). This script instead has EVERY virtual user log in for
// itself, act, and log out again every iteration -- it's the login/session/logout path
// itself under concurrency that's being measured here, not just the endpoints behind it.
//
// Run:
//   k6 run --env VUS=100 hms-user-journey-test.js
// or via the launcher: k6-journey.cmd (reads K6_HOME from .env, same as k6.cmd)
//
// Optional env vars: BASE_URL (default http://localhost:8080), VUS (default 100),
// RAMP_UP/STEADY/RAMP_DOWN (default 10s/40s/10s).
//
// IMPORTANT before running this against a live app:
// - RateLimitFilter enforces a global per-CLIENT-IP limit (100 requests/60s by default)
//   ahead of every endpoint, login included. Every VU here shares this one test
//   machine's IP, so 100 VUs each doing login+me+actions+logout blows through that
//   budget in well under a second -- near-universal 429s, not a real measurement,
//   unless APP_RATE_LIMIT_ENABLED=false (or a much higher
//   APP_RATE_LIMIT_MAX_REQUESTS) is set in .env before running. Restore it afterward.
// - No new User rows are ever created -- every VU logs in as one of the 5 accounts
//   DataSeeder already seeds (see docs/credentials.md), round-robined by VU number.
//   This sidesteps the load-test-data cleanup problem entirely: nothing this script
//   does needs deleting afterward except any patients the write action below creates
//   (same loadtest###@example.com fingerprint as hms-load-test.js, so the same cleanup
//   approach applies if you ever need it).
// - Write actions only fire for roles actually permitted to write (Admin, Receptionist
//   -- see docs/credentials.md's permission grants) and only some of the time (30%),
//   so repeated presentation runs don't pile up thousands of patient rows per run.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 100);

const loginDuration = new Trend('login_duration', true);
const actionDuration = new Trend('action_duration', true);
const logoutDuration = new Trend('logout_duration', true);

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

// The 5 accounts DataSeeder always seeds (docs/credentials.md) -- reusing these instead
// of registering fresh throwaway users is deliberate: it means this script never adds a
// single row to the users table, so there's nothing login-related to clean up after a
// run, ever (see the header comment above).
const CREDENTIALS = [
  { username: 'admin', password: 'Admin@123', role: 'Admin' },
  { username: 'doctorjohn', password: 'Doctor@123', role: 'Doctor' },
  { username: 'receptionist1', password: 'Reception@123', role: 'Receptionist' },
  { username: 'analyst1', password: 'Analyst@123', role: 'Analyst' },
  { username: 'pharmacist1', password: 'Pharmacist@123', role: 'Pharmacist' },
];

// Same generator hms-load-test.js uses for its write path, and for the same reason:
// PatientRequest.phone/email are unique-constrained columns.
function uniqueDigits(len) {
  const raw = `${Date.now()}${__VU}${__ITER}`;
  return raw.slice(-len).padStart(len, '0');
}

// One read-heavy action per role, matched to what docs/credentials.md says that role
// can actually read -- keeps this a realistic journey instead of a 403-generating one.
const READ_ACTIONS = {
  Admin: [`${BASE_URL}/api/v1/users?page=0&size=20`, `${BASE_URL}/api/v1/patients?page=0&size=20`],
  Doctor: [`${BASE_URL}/api/v1/patients?page=0&size=20`, `${BASE_URL}/api/v1/departments?page=0&size=20`],
  Receptionist: [`${BASE_URL}/api/v1/patients?page=0&size=20`, `${BASE_URL}/api/v1/doctors?page=0&size=20`],
  Analyst: [`${BASE_URL}/api/v1/appointments?page=0&size=20`, `${BASE_URL}/api/v1/doctors?page=0&size=20`],
  Pharmacist: [`${BASE_URL}/api/v1/patients?page=0&size=20`, `${BASE_URL}/api/v1/doctors?page=0&size=20`],
};

// Only these two roles hold patients:create per docs/credentials.md's grants.
const CAN_WRITE = new Set(['Admin', 'Receptionist']);

export default function () {
  const cred = CREDENTIALS[__VU % CREDENTIALS.length];

  // ---- Login --------------------------------------------------------------------
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: cred.username, password: cred.password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const loginOk = check(loginRes, { 'login status is 200': (r) => r.status === 200 });
  loginDuration.add(loginRes.timings.duration);
  if (!loginOk) {
    sleep(1);
    return;
  }
  const token = loginRes.json('data.token');
  const authHeaders = { headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } };
  const jsonAuthHeaders = { headers: { ...authHeaders.headers, 'Content-Type': 'application/json' } };

  // ---- Act as this role -----------------------------------------------------------
  const meRes = http.get(`${BASE_URL}/api/v1/auth/me`, authHeaders);
  check(meRes, { '/me status is 200': (r) => r.status === 200 });
  actionDuration.add(meRes.timings.duration);

  for (const url of READ_ACTIONS[cred.role]) {
    const res = http.get(url, authHeaders);
    check(res, { 'read action status is 200': (r) => r.status === 200 });
    actionDuration.add(res.timings.duration);
  }

  // 30% of iterations, and only for roles actually permitted to write -- keeps
  // repeated presentation runs from piling up thousands of rows every time, while
  // still exercising the write path under concurrent, independently-authenticated load.
  if (CAN_WRITE.has(cred.role) && Math.random() < 0.3) {
    const patientPayload = JSON.stringify({
      firstName: 'LoadTest',
      lastName: 'Patient',
      dob: '1990-01-01',
      gender: 'F',
      phone: `07${uniqueDigits(7)}`,
      email: `loadtest${uniqueDigits(6)}@example.com`,
      address: '123 Load Test St',
    });
    const patientRes = http.post(`${BASE_URL}/api/v1/patients`, patientPayload, jsonAuthHeaders);
    check(patientRes, { 'patient create status is 201': (r) => r.status === 201 });
    actionDuration.add(patientRes.timings.duration);
  }

  // ---- Logout -----------------------------------------------------------------
  const logoutRes = http.post(`${BASE_URL}/api/v1/auth/logout`, null, authHeaders);
  check(logoutRes, { 'logout status is 204': (r) => r.status === 204 });
  logoutDuration.add(logoutRes.timings.duration);

  sleep(0.5 + Math.random());
}
