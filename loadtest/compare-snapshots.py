#!/usr/bin/env python3
"""Compare two directories from loadtest/snapshot.sh, allowing one thing only.

"Recent personal records" lists are sorted newest first and cut to a limit.
A single logged set often beats several record types at the same instant, so
records tie on achievedAt. The old code left the order of ties to whatever the
database returned; the new query breaks ties by id. So for those lists this
checks that both sides hold the same records above the cut-off instant, and
the same number of records at it - which is exactly what "same query, ties in
a different order" permits. Every other field must match exactly.

  loadtest/compare-snapshots.py loadtest/results/snapshots/A loadtest/results/snapshots/B
"""
import json
import sys
from pathlib import Path

PR_LIST_KEYS = {"recentPrs"}


def same_up_to_ties(a, b):
    if len(a) != len(b):
        return False, f"length {len(a)} vs {len(b)}"
    if not a:
        return True, ""
    stamps_a = [r["achievedAt"] for r in a]
    stamps_b = [r["achievedAt"] for r in b]
    if stamps_a != stamps_b:
        return False, "achievedAt sequence differs"
    cutoff = stamps_a[-1]
    above_a = sorted(json.dumps(r, sort_keys=True) for r in a if r["achievedAt"] > cutoff)
    above_b = sorted(json.dumps(r, sort_keys=True) for r in b if r["achievedAt"] > cutoff)
    if above_a != above_b:
        return False, "records above the cut-off differ"
    return True, ""


def walk(a, b, path, problems, stats):
    if isinstance(a, dict) and isinstance(b, dict):
        if a.keys() != b.keys():
            problems.append(f"{path}: keys {sorted(a.keys() ^ b.keys())}")
            return
        for k in a:
            if k in PR_LIST_KEYS and isinstance(a[k], list):
                ok, why = same_up_to_ties(a[k], b[k])
                if not ok:
                    problems.append(f"{path}.{k}: {why}")
                elif a[k] != b[k]:
                    stats["tie_reorders"] += 1
            else:
                walk(a[k], b[k], f"{path}.{k}", problems, stats)
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            problems.append(f"{path}: length {len(a)} vs {len(b)}")
            return
        for i, (x, y) in enumerate(zip(a, b)):
            walk(x, y, f"{path}[{i}]", problems, stats)
    elif a != b:
        problems.append(f"{path}: {a!r} vs {b!r}")


def main(left, right):
    left, right = Path(left), Path(right)
    names = sorted(p.name for p in left.glob("*.json"))
    missing = sorted({p.name for p in right.glob("*.json")} ^ set(names))
    problems, stats = [], {"tie_reorders": 0}
    for name in names:
        if name in missing:
            continue
        walk(json.loads((left / name).read_text()), json.loads((right / name).read_text()),
             name, problems, stats)
    print(f"compared {len(names)} responses")
    print(f"recent-PR lists differing only in the order of same-instant ties: {stats['tie_reorders']}")
    if missing:
        print("missing on one side:", missing)
    for p in problems:
        print("DIFFERENT:", p)
    print("RESULT:", "equivalent" if not problems and not missing else "NOT equivalent")
    return 0 if not problems and not missing else 1


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
