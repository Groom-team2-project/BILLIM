"""Count DB SELECTs while chat sockets stay connected without sending messages."""

import argparse
import subprocess
import time
from pathlib import Path


HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.yaml"
PROJECT = "billim-perf-followup"


def select_count():
    command = [
        "docker", "compose", "-p", PROJECT, "-f", str(COMPOSE), "exec", "-T", "mysql",
        "sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -N -uroot billim_perf -e \"SHOW GLOBAL STATUS LIKE 'Com_select'\"",
    ]
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return int(result.stdout.split()[-1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("label")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--vus", type=int, default=40)
    args = parser.parse_args()
    if args.vus < 2 or args.vus > 100 or args.vus % 2:
        parser.error("vus must be an even number from 2 to 100")
    if not (HERE / "local" / "fixtures-followup.json").exists():
        parser.error("isolated fixtures are missing")

    for run in range(1, args.runs + 1):
        command = [
            "docker", "compose", "-p", PROJECT, "-f", str(COMPOSE), "run", "--rm",
            "-e", "CASE=chat-idle", "-e", "FIXTURE_NAME=fixtures-followup.json",
            "-e", f"VUS={args.vus}", "k6", "run", "--summary-export",
            f"/results/{args.label}-idle-run-{run}.json", "/scripts/chat-notification.js",
        ]
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace")
        time.sleep(6)
        if process.poll() is not None:
            output = process.communicate()[0]
            raise SystemExit(f"k6 exited before sample: {output[-1500:]}")
        before = select_count()
        time.sleep(10)
        after = select_count()
        output = process.communicate(timeout=30)[0]
        if process.returncode:
            raise SystemExit(f"k6 failed: {output[-1500:]}")
        print(f"{args.label} run={run} sockets={args.vus} SELECT/10s={after - before}", flush=True)


if __name__ == "__main__":
    main()
