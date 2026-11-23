"""Repeat the isolated outbox delivery measurement and save compact results."""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "local"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("label")
    parser.add_argument("--events", type=int, default=500)
    parser.add_argument("--runs", type=int, default=3)
    args = parser.parse_args()
    if args.events < 1 or args.events > 1000 or args.runs < 1:
        parser.error("events must be 1..1000 and runs positive")
    env = os.environ.copy()
    env["COMPOSE_PROJECT_NAME"] = "billim-perf-followup"
    env["PERF_FIXTURE_NAME"] = "fixtures-followup.json"
    results = []
    for run in range(1, args.runs + 1):
        completed = subprocess.run([sys.executable, str(HERE / "outbox.py"), str(args.events)],
                                   env=env, capture_output=True, text=True,
                                   encoding="utf-8", errors="replace")
        if completed.returncode:
            raise SystemExit(f"outbox run {run} failed: {completed.stderr} {completed.stdout}")
        text = completed.stdout.strip()
        match = re.search(r"events=(\d+) notifications=(\d+) elapsed_s=([\d.]+) "
                          r"throughput_per_s=([\d.]+) lag_p95_ms=([\d.]+) lag_max_ms=([\d.]+)", text)
        if not match:
            raise SystemExit(f"Unexpected outbox output: {text}")
        result = {
            "run": run,
            "events": int(match[1]),
            "notifications": int(match[2]),
            "elapsed_s": float(match[3]),
            "throughput_per_s": float(match[4]),
            "lag_p95_ms": float(match[5]),
            "lag_max_ms": float(match[6]),
        }
        results.append(result)
        print(json.dumps(result), flush=True)
    (OUTPUT / f"{args.label}.json").write_text(json.dumps(results, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
