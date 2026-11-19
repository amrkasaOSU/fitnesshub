// Isolates the exercise library endpoint for the cache A/B in RESULTS.md:
// closed loop (no pause), every request the same, so the only variable is
// whether the catalog comes from Redis or PostgreSQL.
//
//   EXERCISE_CACHE=false docker compose -f loadtest/docker-compose.loadtest.yml up -d --no-deps backend
//   k6 run -e VUS=50 -e DURATION=30s loadtest/k6/exercise-library.js
import http from "k6/http";
import { check } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:18080";
const VUS = parseInt(__ENV.VUS || "50", 10);

export const options = {
  scenarios: {
    steady: {
      executor: "constant-vus",
      vus: VUS,
      duration: __ENV.DURATION || "30s",
    },
  },
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
  thresholds: { "http_req_duration{scenario:steady}": ["max>=0"], "http_reqs{scenario:steady}": ["count>=0"] },
};

export function setup() {
  http.cookieJar().clear(BASE);
  const res = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ email: "client1@fitnesshub.local", password: "FitnessHub!2024" }),
    { headers: { "Content-Type": "application/json" } });
  if (res.status !== 200) throw new Error(`login failed: ${res.status}`);
  return { cookie: res.cookies.SESSION[0].value };
}

export default function (data) {
  http.cookieJar().clear(BASE);
  const res = http.get(`${BASE}/api/exercises?pageSize=100`, { cookies: { SESSION: data.cookie } });
  check(res, { "status 200": (r) => r.status === 200 });
}
