#!/usr/bin/env node
// Lighthouse scores for the main pages, signed-in pages included.
//
//   (cd loadtest/lighthouse && npm ci) && node loadtest/lighthouse/run.mjs <label>
//
// Audits the production build served by the load-test stack, never the dev
// server. Each page is audited RUNS times and the median run kept.
//
// Signed-in pages: the session cookie is placed in Chrome's own cookie jar
// (exactly where a real login puts it) and Lighthouse is told not to clear
// storage. An earlier version injected it as an extra Cookie header instead;
// Chrome dropped that header as soon as the app received its first cookie,
// every later API call came back 401, and the "signed-in" scores were of a
// page that never loaded. So every audit is now rejected unless all of the
// page's API calls succeeded.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import lighthouse from "lighthouse";
import * as chromeLauncher from "chrome-launcher";
import puppeteer from "puppeteer-core";

const label = process.argv[2];
if (!label) {
  console.error("usage: run.mjs <label>");
  process.exit(1);
}
const FRONTEND = process.env.FRONTEND_URL ?? "http://localhost:13000";
const BACKEND = process.env.BACKEND_URL ?? "http://localhost:18080";
const RUNS = Number(process.env.RUNS ?? 3);
const PASSWORD = "FitnessHub!2024"; // published demo password, seed data only
const only = process.env.PAGES ? process.env.PAGES.split(",") : null;
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

async function sessionCookie(email) {
  const res = await fetch(`${BACKEND}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Connection: "close" },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (!res.ok) throw new Error(`login failed for ${email}: ${res.status}`);
  return res.headers.getSetCookie().find((c) => c.startsWith("SESSION=")).split(";")[0].slice("SESSION=".length);
}

async function auditOnce(page, sessionValue) {
  const chrome = await chromeLauncher.launch({ chromeFlags: ["--headless=new", "--no-sandbox"] });
  try {
    if (sessionValue) {
      // Chrome's debugging endpoint can lag a moment behind the process start.
      let browser;
      for (let attempt = 0; !browser; attempt++) {
        try {
          browser = await puppeteer.connect({ browserURL: `http://127.0.0.1:${chrome.port}` });
        } catch (e) {
          if (attempt >= 20) throw e;
          await new Promise((r) => setTimeout(r, 250));
        }
      }
      await browser.setCookie({
        name: "SESSION", value: sessionValue, domain: "localhost", path: "/", httpOnly: true, sameSite: "Lax",
      });
      await browser.disconnect();
    }
    const result = await lighthouse(`${FRONTEND}${page.path}`, {
      port: chrome.port,
      output: "json",
      logLevel: "error",
      onlyCategories: ["performance", "accessibility", "best-practices", "seo"],
      disableStorageReset: !!sessionValue,
    });
    return result.lhr;
  } finally {
    await chrome.kill();
  }
}

function validate(page, lhr) {
  if (lhr.runtimeError) return `runtime error ${lhr.runtimeError.code}`;
  if (page.as && !lhr.finalDisplayedUrl.includes(page.path)) return `ended on ${lhr.finalDisplayedUrl}`;
  const failed = lhr.audits["network-requests"].details.items
    .filter((r) => r.url.startsWith(BACKEND) && (r.statusCode ?? 0) >= 400)
    .map((r) => `${r.statusCode} ${r.url.slice(BACKEND.length)}`);
  if (failed.length) return `API errors: ${failed.join(", ")}`;
  return null;
}

const median = (xs) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];

const summary = [];
for (const page of pages.filter((p) => !only || only.includes(p.name))) {
  const runs = [];
  while (runs.length < RUNS) {
    let lhr, problem;
    for (let attempt = 0; attempt < 3; attempt++) {
      const session = page.as ? await sessionCookie(page.as) : null;
      lhr = await auditOnce(page, session);
      problem = validate(page, lhr);
      if (!problem) break;
      console.error(`  ${page.name}: rejected run (${problem}), retrying`);
    }
    if (problem) throw new Error(`${page.name}: ${problem}`);
    writeFileSync(join(outDir, `${page.name}-${runs.length + 1}.json`), JSON.stringify(lhr));
    const a11yRefs = lhr.categories.accessibility.auditRefs.filter((r) => r.weight > 0).map((r) => r.id);
    runs.push({
      performance: Math.round(lhr.categories.performance.score * 100),
      accessibility: Math.round(lhr.categories.accessibility.score * 100),
      bestPractices: Math.round(lhr.categories["best-practices"].score * 100),
      seo: Math.round(lhr.categories.seo.score * 100),
      lcpMs: Math.round(lhr.audits["largest-contentful-paint"].numericValue),
      tbtMs: Math.round(lhr.audits["total-blocking-time"].numericValue),
      cls: Number(lhr.audits["cumulative-layout-shift"].numericValue.toFixed(3)),
      failedA11y: a11yRefs.filter((id) => lhr.audits[id]?.score === 0),
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
