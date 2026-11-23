"""pool-bench.sh 결과 요약. 연결 수는 표집기 자신의 연결 1개 포함. 구성별 유휴·부하 연결 수와 메모리, 처리량·p95·p99 중위값.

사용: python3 docs/performance/tools/auth/pool_report.py backend/build/perf/pool-<시각>
"""
import csv
import json
import statistics
import sys
from pathlib import Path


def mib(value):
    """docker stats 표기(예: 512.3MiB, 1.02GiB) 또는 바이트 → MiB"""
    value = value.strip()
    for unit, factor in (("GiB", 1024), ("MiB", 1), ("KiB", 1 / 1024)):
        if value.endswith(unit):
            return float(value[: -len(unit)]) * factor
    return float(value) / 1024 / 1024


def samples(path):
    rows = []
    with open(path) as f:
        for row in csv.reader(f):
            if len(row) == 4 and row[1] and row[2]:
                rows.append((int(row[1]), mib(row[2]), mib(row[3])))
    return rows


def main(out):
    out = Path(out)
    configs = sorted({p.name.split("-")[0] for p in out.glob("*-idle.csv")},
                     key=lambda c: (int(c.split("x")[0]), int(c.split("x")[1])))
    print("| 구성 | 유휴 연결 | 유휴 P_S 메모리 | 부하 최대 연결 | 부하 최대 P_S 메모리 | 부하 최대 컨테이너 메모리 | 처리량 /s | p95 ms | p99 ms | 실패율 |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for cfg in configs:
        idle = samples(out / f"{cfg}-idle.csv")
        runs = sorted(out.glob(f"{cfg}-run*.json"))
        load = [s for r in sorted(out.glob(f"{cfg}-run*.csv")) for s in samples(r)]
        rates, p95s, p99s, fails = [], [], [], []
        for r in runs:
            m = json.loads(r.read_text())["metrics"]
            rates.append(m["http_reqs"]["rate"])
            p95s.append(m["http_req_duration"]["p(95)"])
            p99s.append(m["http_req_duration"]["p(99)"])
            fails.append(m["http_req_failed"]["value"])
        print(f"| {cfg} | {max(s[0] for s in idle)} | {statistics.median(s[1] for s in idle):.0f} MiB"
              f" | {max(s[0] for s in load)} | {max(s[1] for s in load):.0f} MiB | {max(s[2] for s in load):.0f} MiB"
              f" | {statistics.median(rates):.0f} (min {min(rates):.0f} · max {max(rates):.0f})"
              f" | {statistics.median(p95s):.1f} | {statistics.median(p99s):.1f} | {max(fails):.2%} |")


if __name__ == "__main__":
    main(sys.argv[1])
