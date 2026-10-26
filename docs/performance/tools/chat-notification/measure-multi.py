"""Run one isolated two-backend chat load and sample local resource usage."""

import argparse
import json
import os
import subprocess
import time
from pathlib import Path

try:
    import psutil
except ImportError:
    psutil = None


HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.multi.yaml"
LOCAL = HERE / "local"
CONTAINERS = [
    "billim-perf-multi-backend-a-1",
    "billim-perf-multi-backend-b-1",
    "billim-perf-multi-mysql-1",
    "billim-perf-multi-redis-1",
]


def mysql_status():
    query = (
        "SHOW GLOBAL STATUS WHERE Variable_name IN "
        "('Com_select','Com_insert','Innodb_rows_read',"
        "'Innodb_row_lock_waits','Innodb_row_lock_time','Questions')"
    )
    command = [
        "docker", "exec", CONTAINERS[2], "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N billim_perf -e "' + query + '"',
    ]
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    return {name: int(value) for name, value in (line.split("\t") for line in result.stdout.splitlines())}


def docker_stats():
    result = subprocess.run(
        ["docker", "stats", "--no-stream", "--format", "{{json .}}", *CONTAINERS],
        capture_output=True, text=True, check=True,
    )
    return [json.loads(line) for line in result.stdout.splitlines() if line]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", choices=["db", "redis", "redis-optimized", "redis-diagnostics", "redis-listener-pool"])
    parser.add_argument("vus", type=int)
    parser.add_argument("run_name")
    parser.add_argument("--case", choices=["chat-paired", "chat-paired-jitter"], default="chat-paired")
    parser.add_argument("--paired-interval-ms", type=int, default=2000)
    args = parser.parse_args()
    if args.vus < 2 or args.vus % 2 or not args.run_name.replace("-", "").isalnum():
        parser.error("VUS must be a positive even number and run_name must be alphanumeric/kebab")
    if not 1000 <= args.paired_interval_ms <= 2500:
        parser.error("paired interval must be 1000..2500ms")

    LOCAL.mkdir(exist_ok=True)
    environment = os.environ.copy()
    environment["PERF_IMAGE"] = f"billim-perf-{args.image}"
    command = [
        "docker", "compose", "-f", str(COMPOSE), "run", "--rm",
        "-e", f"CASE={args.case}", "-e", f"VUS={args.vus}",
        "-e", f"PAIRED_INTERVAL_MS={args.paired_interval_ms}",
        "-e", "K6_NO_COLOR=true", "k6", "run", "--summary-trend-stats",
        "avg,min,med,max,p(90),p(95),p(99)", "/scripts/chat-multi-instance.js",
    ]
    before = mysql_status()
    samples = []
    start = time.monotonic()
    if psutil:
        psutil.cpu_percent(interval=None)
    with (LOCAL / f"{args.run_name}.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, env=environment, stdout=output, stderr=subprocess.STDOUT)
        while process.poll() is None:
            sample = {"elapsed_s": round(time.monotonic() - start, 2)}
            if psutil:
                sample["host_cpu_percent"] = psutil.cpu_percent(interval=None)
                sample["host_ram_percent"] = psutil.virtual_memory().percent
            try:
                sample["docker"] = docker_stats()
            except subprocess.CalledProcessError as error:
                sample["docker_error"] = str(error)
            samples.append(sample)
            time.sleep(2)
        exit_code = process.wait()
    after = mysql_status()
    result = {
        "image": environment["PERF_IMAGE"],
        "case": args.case,
        "paired_interval_ms": args.paired_interval_ms,
        "vus": args.vus,
        "exit_code": exit_code,
        "elapsed_s": round(time.monotonic() - start, 2),
        "mysql_status_delta": {key: after[key] - before[key] for key in before},
        "samples": samples,
    }
    (LOCAL / f"{args.run_name}.metrics.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"{args.run_name}: exit={exit_code}, elapsed={result['elapsed_s']}s")
    print(f"mysql={result['mysql_status_delta']}")
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
