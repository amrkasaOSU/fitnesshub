// Read-heavy mix modelled on what the app actually does on page load.
//
//   k6 run loadtest/k6/read-mix.js -e VUS=100 -e DURATION=60s
//
// Each virtual user is one signed-in person: they load a page, wait a second,
// load another. 80% are clients, 20% are the coach, which is roughly how a
// coaching business is shaped. Sessions are created once in setup() and shared,
// so the numbers measure the API rather than bcrypt.
import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:18080";
const VUS = parseInt(__ENV.VUS || "50", 10);
const DURATION = __ENV.DURATION || "60s";
// Seconds a user pauses between requests. 1 models real use; 0 turns the run
// into a closed-loop stress test that finds the throughput ceiling.
const THINK = parseFloat(__ENV.THINK === undefined ? "1" : __ENV.THINK);
const PASSWORD = "FitnessHub!2024"; // published demo password, seed data only

export const options = {
  scenarios: {
    steady: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "15s", target: VUS },
        { duration: DURATION, target: VUS },
        { duration: "5s", target: 0 },
      ],
      gracefulRampDown: "5s",
    },
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
  // Declared so each endpoint gets its own latency series in the summary.
  thresholds: {
    // Everything the scenario sent, excluding the handful of logins in setup().
    "http_req_duration{scenario:steady}": ["max>=0"],
    "http_reqs{scenario:steady}": ["count>=0"],
    "http_req_duration{endpoint:client_dashboard}": ["max>=0"],
    "http_req_duration{endpoint:workout_today}": ["max>=0"],
    "http_req_duration{endpoint:workout_week}": ["max>=0"],
    "http_req_duration{endpoint:workout_history}": ["max>=0"],
    "http_req_duration{endpoint:weight_dashboard}": ["max>=0"],
    "http_req_duration{endpoint:exercises}": ["max>=0"],
    "http_req_duration{endpoint:coach_dashboard}": ["max>=0"],
    "http_req_duration{endpoint:coach_clients}": ["max>=0"],
    "http_req_duration{endpoint:coach_client_workouts}": ["max>=0"],
  },
};

const errors = new Rate("app_errors");

function login(email) {
  // Start from an empty jar: a login that carries an existing session cookie
  // re-uses that session instead of issuing a new one.
  http.cookieJar().clear(BASE);
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { endpoint: "setup" } }
  );
  if (res.status !== 200) {
    throw new Error(`login failed for ${email}: ${res.status} ${res.body}`);
  }
  const cookie = res.cookies.SESSION && res.cookies.SESSION[0].value;
  if (!cookie) throw new Error(`no SESSION cookie for ${email}`);
  return { cookie, id: res.json("data.id") };
}

export function setup() {
  const coach = login("coach@fitnesshub.local");
  const clients = [1, 2, 3, 4, 5].map((n) => login(`client${n}@fitnesshub.local`));
  // Touch /today once per client so the "create today's session" write happens
  // here and the measured window is read-only.
  for (const c of clients) {
    http.get(`${BASE}/api/workouts/today`, {
      cookies: { SESSION: c.cookie },
      tags: { endpoint: "setup" },
    });
  }
  return { coach, clients };
}

function get(path, session, endpoint) {
  const res = http.get(`${BASE}${path}`, {
    cookies: { SESSION: session.cookie },
    tags: { endpoint },
  });
  const ok = check(res, { "status 200": (r) => r.status === 200 });
  errors.add(!ok);
  if (THINK > 0) sleep(THINK);
}

export default function (data) {
  // Don't let k6's per-VU cookie jar override the session we pass explicitly.
  http.cookieJar().clear(BASE);

  const isCoach = __VU % 5 === 0;
  if (isCoach) {
    const client = data.clients[__ITER % data.clients.length];
    get("/api/coach/dashboard", data.coach, "coach_dashboard");
    get("/api/clients", data.coach, "coach_clients");
    get(`/api/clients/${client.id}/workouts`, data.coach, "coach_client_workouts");
  } else {
    const me = data.clients[__VU % data.clients.length];
    get("/api/dashboard", me, "client_dashboard");
    get("/api/workouts/today", me, "workout_today");
    get("/api/workouts/week", me, "workout_week");
    get("/api/workouts", me, "workout_history");
    get("/api/weight/dashboard", me, "weight_dashboard");
    get("/api/exercises?pageSize=100", me, "exercises");
  }
}
