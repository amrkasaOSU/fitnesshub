#!/usr/bin/env node
// Lighthouse scores for the main pages, signed-in pages included.
//
//   node loadtest/lighthouse/run.mjs baseline
//
// Runs against the production build served by the load-test stack
// (`docker compose -f loadtest/docker-compose.loadtest.yml --profile ui up -d`),
// never the dev server - dev mode ships unminified bundles and would understate
// every score. Each page is audited RUNS times and the median run is kept,
// because a single Lighthouse performance score swings by several points.
//
// Signed-in pages work by logging in over the API first and handing the session
// cookie to Chrome as an extra header, so the app's own requests authenticate.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const label = process.argv[2];
if (!label) {
  console.error("usage: run.mjs <label>");
  process.exit(1);
}
const FRONTEND = process.env.FRONTEND_URL ?? "http://localhost:13000";
const BACKEND = process.env.BACKEND_URL ?? "http://localhost:18080";
const RUNS = Number(process.env.RUNS ?? 3);
const PASSWORD = "FitnessHub!2024"; // published demo password, seed data only
const outDir = join(dirname(fileURLToPath(import.meta.url)), "..", "results", "lighthouse", label);
mkdirSync(outDir, { recursive: true });

const pages = [
  { name: "landing", path: "/" },
  { name: "login", path: "/login" },
  { name: "pricing", path: "/pricing" },
  { name: "client-dashboard", path: "/dashboard", as: "client1@fitnesshub.local" },
  { name: "workout-today", path: "/workout/today", as: "client1@fitnesshub.local" },
  { name: "progress", path: "/progress", as: "client1@fitnesshub.local" },
  { name: "coach-dashboard", path: "/coach/dashboard", as: "coach@fitnesshub.local" },
  { name: "coach-clients", path: "/coach/clients", as: "coach@fitnesshub.local" },
];

// PAGES=landing,client-dashboard limits the run to a subset.
const only = process.env.PAGES ? process.env.PAGES.split(",") : null;

async function sessionCookie(email) {
  // Each page takes long enough that Node's pooled keep-alive socket to the
  // backend has usually been closed by the next login, so ask for a fresh
  // connection and retry once or twice rather than fail a 10-minute run.
  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(`${BACKEND}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Connection: "close" },
        body: JSON.stringify({ email, password: PASSWORD }),
      });
      if (!res.ok) throw new Error(`login failed for ${email}: ${res.status}`);
      const cookie = res.headers.getSetCookie().find((c) => c.startsWith("SESSION="));
      return cookie.split(";")[0];
    } catch (e) {
      lastError = e;
      await new Promise((r) => setTimeout(r, 500));
    }
  }
  throw lastError;
}

// Headless Chrome occasionally fails to paint (NO_FCP) for no reason related
// to the page, so a failed run is retried rather than aborting the whole
// matrix. A report already on disk from an interrupted run is reused, which
// makes the script resumable.
function audit(args, out) {
  const load = () => {
    if (!existsSync(out)) return null;
    try {
      const report = JSON.parse(readFileSync(out, "utf8"));
      return report.runtimeError || !report.categories?.performance?.score ? null : report;
    } catch {
      return null;
    }
  };
  let report = load();
  for (let attempt = 0; !report && attempt < 3; attempt++) {
    try {
      execFileSync("npx", args, { stdio: ["ignore", "ignore", "inherit"] });
    } catch {
      // fall through to the validity check and retry
    }
    report = load();
  }
  if (!report) throw new Error(`lighthouse failed 3 times for ${out}`);
  return report;
}

const median = (xs) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];

const summary = [];
for (const page of pages.filter((p) => !only || only.includes(p.name))) {
  const cookie = page.as ? await sessionCookie(page.as) : null;
  const runs = [];
  for (let i = 0; i < RUNS; i++) {
    const out = join(outDir, `${page.name}-${i + 1}.json`);
    const args = [
      "--yes", "lighthouse@12", `${FRONTEND}${page.path}`,
      "--quiet", "--output=json", `--output-path=${out}`,
      "--only-categories=performance,accessibility,best-practices,seo",
      "--chrome-flags=--headless=new --no-sandbox",
    ];
    if (cookie) args.push(`--extra-headers=${JSON.stringify({ Cookie: cookie })}`);
    const report = audit(args, out);
    // A signed-in audit that got bounced to /login would silently score the login page.
    if (page.as && !report.finalDisplayedUrl.includes(page.path)) {
      throw new Error(`${page.name} ended on ${report.finalDisplayedUrl}, not ${page.path}`);
    }
    runs.push({
      performance: Math.round(report.categories.performance.score * 100),
      accessibility: Math.round(report.categories.accessibility.score * 100),
      bestPractices: Math.round(report.categories["best-practices"].score * 100),
      seo: Math.round(report.categories.seo.score * 100),
      lcpMs: Math.round(report.audits["largest-contentful-paint"].numericValue),
      tbtMs: Math.round(report.audits["total-blocking-time"].numericValue),
      cls: Number(report.audits["cumulative-layout-shift"].numericValue.toFixed(3)),
      failedA11y: Object.values(report.audits)
        .filter((a) => report.categories.accessibility.auditRefs.some((r) => r.id === a.id) && a.score === 0)
        .map((a) => a.id),
    });
  }
  const row = { page: page.name, path: page.path };
  for (const key of ["performance", "accessibility", "bestPractices", "seo", "lcpMs", "tbtMs", "cls"]) {
    row[key] = median(runs.map((r) => r[key]));
  }
  row.failedA11y = [...new Set(runs.flatMap((r) => r.failedA11y))];
  summary.push(row);
  console.error(`${page.name}: perf ${row.performance} a11y ${row.accessibility} bp ${row.bestPractices} seo ${row.seo}`);
}

writeFileSync(join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
console.log("| page | performance | accessibility | best practices | SEO | LCP ms | TBT ms | CLS | failing a11y audits |");
console.log("|---|---:|---:|---:|---:|---:|---:|---:|---|");
for (const r of summary) {
  console.log(`| ${r.page} | ${r.performance} | ${r.accessibility} | ${r.bestPractices} | ${r.seo} | ${r.lcpMs} | ${r.tbtMs} | ${r.cls} | ${r.failedA11y.join(", ") || "none"} |`);
}
