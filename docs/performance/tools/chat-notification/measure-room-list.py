"""Measure chat room list response times in the isolated follow-up Compose stack."""

import argparse
import json
import statistics
import subprocess
from pathlib import Path


HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.yaml"
OUTPUT = HERE / "local"
PROJECT = "billim-perf-followup"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("label")
    parser.add_argument("--sizes", default="1,20,50")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--vus", type=int, default=4)
    parser.add_argument("--duration", default="15s")
    args = parser.parse_args()
    if not args.label.replace("-", "").replace("_", "").isalnum():
        parser.error("label must contain only letters, digits, hyphens, and underscores")
    sizes = [int(value) for value in args.sizes.split(",")]
    if not sizes or any(size < 1 or size > 50 for size in sizes):
        parser.error("sizes must be between 1 and 50")
    if args.runs < 1 or args.vus < 1:
        parser.error("runs and vus must be positive")
    if not (OUTPUT / "fixtures-followup.json").exists():
        parser.error("isolated fixtures-followup.json is missing")

    results = []
    for size in sizes:
        for run in range(1, args.runs + 1):
            filename = f"{args.label}-rooms-{size}-run-{run}.json"
            command = [
                "docker", "compose", "-p", PROJECT, "-f", str(COMPOSE), "run", "--rm",
                "-e", "CASE=chat-room-list", "-e", "FIXTURE_NAME=fixtures-followup.json",
                "-e", f"INBOX_PAGE_SIZE={size}", "-e", f"VUS={args.vus}",
                "-e", f"DURATION={args.duration}", "k6", "run", "--summary-export",
                f"/results/{filename}", "/scripts/chat-notification.js",
            ]
            if not (OUTPUT / filename).exists():
                completed = subprocess.run(command, capture_output=True, text=True,
                                           encoding="utf-8", errors="replace")
                if completed.returncode:
                    raise SystemExit(f"k6 failed for size={size} run={run}:\n{completed.stderr}\n{completed.stdout[-1500:]}")
            summary = json.loads((OUTPUT / filename).read_text(encoding="utf-8"))
            metrics = summary["metrics"]
            result = {
                "size": size,
                "run": run,
                "requests": metrics["http_reqs"]["count"],
                "rps": metrics["http_reqs"]["rate"],
                "p50_ms": metrics["http_req_duration"]["med"],
                "p95_ms": metrics["http_req_duration"]["p(95)"],
                "failure_rate": metrics["app_failures"]["value"],
            }
            results.append(result)
            print(json.dumps(result, ensure_ascii=False), flush=True)
    for size in sizes:
        group = [result for result in results if result["size"] == size]
        print(f"size={size} median_p95_ms={statistics.median(row['p95_ms'] for row in group):.2f} "
              f"median_rps={statistics.median(row['rps'] for row in group):.2f}", flush=True)


if __name__ == "__main__":
    main()
