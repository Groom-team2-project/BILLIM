"""cleanup-bench.sh 결과 요약. 회차별 배경·정리 구간의 p99와 초당 처리량, 정리 소요 시간 → 구성별 중위값(범위).

정리 구간: cron 발화(k6 시작 +80s)부터 앱 로그의 "만료 정리" 기록 초까지.
배경: 유지 구간(+35s ~ +135s) 중 정리 구간 앞뒤 2초를 뺀 나머지.

사용: python3 docs/performance/tools/auth/cleanup_report.py backend/build/perf/cleanup-<시각>
"""
import csv
import re
import statistics
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

LOG_LINE = re.compile(r"^(\S+)\s.*만료 정리 — .*auth_sessions (\d+)건")


def p99(values):
    values = sorted(values)
    return values[max(0, int(len(values) * 0.99) - 1)] if values else float("nan")


def cleanup_ends(log_path):
    ends = []
    for line in log_path.read_text(errors="ignore").splitlines():
        m = LOG_LINE.match(line)
        if m and int(m.group(2)) > 0:
            ends.append(datetime.fromisoformat(m.group(1)).timestamp())
    return ends


def requests(csv_path):
    per_sec = defaultdict(list)
    with open(csv_path) as f:
        for row in csv.DictReader(f):
            if row["metric_name"] == "http_req_duration":
                per_sec[int(row["timestamp"])].append(float(row["metric_value"]))
    return per_sec


def run_result(out, chunk, r, start):
    cs = start + 80
    end = next((e for e in cleanup_ends(out / f"app-{chunk}.log") if cs <= e < cs + 60), None)
    if end is None:
        return None
    ce = int(end)
    per_sec = requests(out / f"{chunk}-run{r}.csv")
    window = range(cs, ce + 1)
    background = [s for s in range(start + 35, start + 136) if not cs - 2 <= s <= ce + 2]
    bg_counts = [len(per_sec[s]) for s in background]
    return {
        "bg_p99": p99([v for s in background for v in per_sec[s]]),
        "win_p99": p99([v for s in window for v in per_sec[s]]),
        "bg_rps": statistics.mean(bg_counts),
        "win_min_rps": min(len(per_sec[s]) for s in window),
        "seconds": end - cs,
    }


def fmt(values, unit, digits=1):
    med = statistics.median(values)
    return f"{med:.{digits}f}{unit} ({min(values):.{digits}f}~{max(values):.{digits}f})"


def main(out):
    out = Path(out)
    results = defaultdict(list)
    with open(out / "runs.csv") as f:
        for chunk, r, start in csv.reader(f):
            res = run_result(out, chunk, r, int(start))
            if res is None:
                print(f"! 묶음 {chunk} {r}회차 — 정리 기록 없음, 제외", file=sys.stderr)
                continue
            results[chunk].append(res)

    print("| 묶음 | 배경 p99 | 정리 구간 p99 | 배경 처리량 | 정리 구간 최저 처리량 | 최저/배경 | 정리 소요 |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for chunk, rs in results.items():
        ratio = [r["win_min_rps"] / r["bg_rps"] * 100 for r in rs]
        print(f"| {chunk} | {fmt([r['bg_p99'] for r in rs], ' ms')} | {fmt([r['win_p99'] for r in rs], ' ms')} "
              f"| {fmt([r['bg_rps'] for r in rs], '건', 0)} | {fmt([r['win_min_rps'] for r in rs], '건', 0)} "
              f"| {fmt(ratio, '%', 0)} | {fmt([r['seconds'] for r in rs], ' s', 2)} |")
    print("\n회차 수: " + ", ".join(f"{c} {len(rs)}회" for c, rs in results.items()))


if __name__ == "__main__":
    main(sys.argv[1])
