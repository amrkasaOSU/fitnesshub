#!/usr/bin/env python3
"""Summarise a JaCoCo CSV report as overall line / branch / instruction coverage.

Usage: scripts/coverage-summary.py backend/target/site/jacoco/jacoco.csv
"""
import csv
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "backend/target/site/jacoco/jacoco.csv"
totals = {k: [0, 0] for k in ("INSTRUCTION", "BRANCH", "LINE", "METHOD")}
classes = 0
with open(path, newline="") as f:
    for row in csv.DictReader(f):
        classes += 1
        for kind in totals:
            totals[kind][0] += int(row[f"{kind}_COVERED"])
            totals[kind][1] += int(row[f"{kind}_MISSED"])

print(f"classes analysed: {classes}")
for kind, (covered, missed) in totals.items():
    total = covered + missed
    pct = 100.0 * covered / total if total else 0.0
    print(f"{kind.lower():12s} {covered:6d} / {total:6d}  = {pct:5.1f}%")
