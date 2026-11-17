#!/usr/bin/env python3
"""Turn k6 --summary-export files into Markdown tables.

  loadtest/summarize.py baseline                 # one run
  loadtest/summarize.py baseline after-nplus1    # before/after with % change
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).parent / "results"
ENDPOINTS = [
    "client_dashboard", "workout_today", "workout_week", "workout_history",
    "weight_dashboard", "exercises", "coach_dashboard", "coach_clients",
    "coach_client_workouts",
]


def load(label):
    runs = {}
    for f in sorted((ROOT / label).glob("vus-*.json"), key=lambda p: int(p.stem.split("-")[1])):
        runs[int(f.stem.split("-")[1])] = json.loads(f.read_text())["metrics"]
    return runs


def overall(m):
    d = m.get("http_req_duration{scenario:steady}", m["http_req_duration"])
    reqs = m.get("http_reqs{scenario:steady}", m["http_reqs"])
    failed = m.get("http_req_failed", {}).get("value", 0.0)
    app_err = m.get("app_errors", {}).get("value", 0.0)
    return {
        "p50": d["med"], "p95": d["p(95)"], "p99": d["p(99)"], "max": d["max"],
        "rps": reqs["rate"], "reqs": reqs["count"],
        "err": max(failed, app_err) * 100,
    }


def pct(before, after):
    if before == 0:
        return "n/a"
    return f"{(after - before) / before * 100:+.1f}%"


def single(label):
    runs = load(label)
    print(f"### {label}: overall\n")
    print("| VUs | requests | req/s | p50 ms | p95 ms | p99 ms | max ms | error % |")
    print("|---:|---:|---:|---:|---:|---:|---:|---:|")
    for vus, m in runs.items():
        o = overall(m)
        print(f"| {vus} | {o['reqs']} | {o['rps']:.1f} | {o['p50']:.1f} | {o['p95']:.1f} | "
              f"{o['p99']:.1f} | {o['max']:.0f} | {o['err']:.2f} |")
    print(f"\n### {label}: p95 ms per endpoint\n")
    print("| endpoint | " + " | ".join(f"{v} VUs" for v in runs) + " |")
    print("|---|" + "---:|" * len(runs))
    for ep in ENDPOINTS:
        cells = []
        for m in runs.values():
            d = m.get(f"http_req_duration{{endpoint:{ep}}}")
            cells.append(f"{d['p(95)']:.1f}" if d else "-")
        print(f"| {ep} | " + " | ".join(cells) + " |")


def compare(a, b):
    ra, rb = load(a), load(b)
    print(f"### {a} -> {b}: overall\n")
    print("| VUs | metric | before | after | change |")
    print("|---:|---|---:|---:|---:|")
    for vus in ra:
        if vus not in rb:
            continue
        oa, ob = overall(ra[vus]), overall(rb[vus])
        for key, name in (("rps", "req/s"), ("p50", "p50 ms"), ("p95", "p95 ms"), ("p99", "p99 ms"), ("err", "error %")):
            print(f"| {vus} | {name} | {oa[key]:.1f} | {ob[key]:.1f} | {pct(oa[key], ob[key])} |")
    print(f"\n### {a} -> {b}: p95 ms per endpoint\n")
    for vus in ra:
        if vus not in rb:
            continue
        print(f"\n**{vus} VUs**\n")
        print("| endpoint | before | after | change |")
        print("|---|---:|---:|---:|")
        for ep in ENDPOINTS:
            da = ra[vus].get(f"http_req_duration{{endpoint:{ep}}}")
            db = rb[vus].get(f"http_req_duration{{endpoint:{ep}}}")
            if not da or not db:
                continue
            print(f"| {ep} | {da['p(95)']:.1f} | {db['p(95)']:.1f} | {pct(da['p(95)'], db['p(95)'])} |")


if __name__ == "__main__":
    if len(sys.argv) == 2:
        single(sys.argv[1])
    elif len(sys.argv) == 3:
        compare(sys.argv[1], sys.argv[2])
    else:
        sys.exit(__doc__)
