#!/usr/bin/env python3
"""Before/after table from two loadtest/explain-scale.sh outputs.

  loadtest/explain-compare.py before.txt after.txt
"""
import re
import sys


def parse(path):
    out, current = {}, None
    for line in open(path):
        m = re.match(r"^-- (\d+)\. (.*)$", line)
        if m:
            current = (int(m.group(1)), m.group(2).strip())
            out[current] = {"time": None, "scans": []}
            continue
        if current is None:
            continue
        t = re.search(r"Execution Time: ([\d.]+) ms", line)
        if t:
            out[current]["time"] = float(t.group(1))
        s = re.search(r"((?:Parallel )?(?:Seq|Index Only|Index|Bitmap Heap|Bitmap Index) Scan(?: Backward)?) (?:using (\S+) )?on (\S+)", line)
        if s:
            scan = s.group(1) + (f" ({s.group(2)})" if s.group(2) else "") + f" on {s.group(3)}"
            if "Bitmap Index Scan" not in scan:
                out[current]["scans"].append(scan)
    return out


before, after = parse(sys.argv[1]), parse(sys.argv[2])
print("| Query | Before ms | After ms | Change | Plan before | Plan after |")
print("|---|---:|---:|---:|---|---|")
for key in sorted(before):
    b, a = before[key], after.get(key)
    if not a or b["time"] is None or a["time"] is None:
        continue
    change = (a["time"] - b["time"]) / b["time"] * 100
    print(f"| {key[0]}. {key[1]} | {b['time']:.3f} | {a['time']:.3f} | {change:+.1f}% | "
          f"{'; '.join(dict.fromkeys(b['scans']))} | {'; '.join(dict.fromkeys(a['scans']))} |")
