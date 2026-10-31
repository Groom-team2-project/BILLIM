"""Compare current chat-send latency with one hot room and distributed rooms."""

import argparse
import json
import subprocess
from pathlib import Path


HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "local"
COMPOSE = HERE / "compose.yaml"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("label")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--vus", type=int, default=16)
    parser.add_argument("--duration", default="15s")
    args = parser.parse_args()
    if args.runs < 1 or args.vus < 1 or args.vus > 128:
        parser.error("runs must be positive and vus 1..128")
    for run in range(1, args.runs + 1):
        for variant, hot in (("distributed", "false"), ("hot", "true")):
            name = f"{args.label}-{variant}-run-{run}.json"
            command = [
                "docker", "compose", "-p", "billim-perf-followup", "-f", str(COMPOSE),
                "run", "--rm", "-e", "CASE=chat-send", "-e", "FIXTURE_NAME=fixtures-followup.json",
                "-e", f"HOT_ROOM={hot}", "-e", f"VUS={args.vus}",
                "-e", f"DURATION={args.duration}", "k6", "run", "--summary-export",
                f"/results/{name}", "/scripts/chat-notification.js",
            ]
            result = subprocess.run(command, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace")
            if result.returncode:
                raise SystemExit(f"k6 failed: {result.stdout[-1200:]} {result.stderr[-500:]}")
            metrics = json.loads((OUTPUT / name).read_text(encoding="utf-8"))["metrics"]
            print(json.dumps({
                "run": run, "variant": variant,
                "requests": metrics["http_reqs"]["count"],
                "rps": metrics["http_reqs"]["rate"],
                "p95_ms": metrics["http_req_duration"]["p(95)"],
                "failure_rate": metrics["app_failures"]["value"],
            }), flush=True)


if __name__ == "__main__":
    main()
