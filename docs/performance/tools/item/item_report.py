"""item_bench.sh 결과 폴더를 읽어 CASE별 중위값 표(마크다운)를 만든다.

사용: python3 docs/performance/tools/item/item_report.py docs/performance/tools/item/local/results/<폴더>
"""

import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

ORDER = ["search-latest", "search-keyword", "search-category", "search-period", "search-nearest",
         "search-deep-page", "item-detail", "mine", "neighbor", "map", "home"]


def main(folder: Path) -> None:
    runs = defaultdict(list)
    for f in sorted(folder.glob("*.txt")):
        if f.name == "smoke.txt" or f.name.endswith("-warmup.txt"):
            continue
        m = re.search(r"^BILLIM_RESULT (.+)$", f.read_text(encoding="utf-8", errors="replace"), re.M)
        if m:
            r = json.loads(m.group(1))
            runs[r["case"]].append(r)

    if not runs:
        print("결과가 없습니다.")
        return

    first = next(iter(runs.values()))[0]
    print(f"측정 조건: VU {first['vus']} · {first['duration']} · CASE별 {max(len(v) for v in runs.values())}회 중위값\n")
    print("| CASE | req/s | p50 | p95 | p99 | 오류율 | 4xx/5xx | 판정 |")
    print("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
    for case in sorted(runs, key=lambda c: ORDER.index(c) if c in ORDER else 99):
        rs = runs[case]
        med = lambda k: statistics.median(r[k] for r in rs)
        fail = med("failRate")
        p95 = med("p95")
        verdict = "통과" if p95 < 500 and fail < 1 else "기준 미달"
        errs = f"{sum(r['client4xx'] for r in rs)}/{sum(r['server5xx'] for r in rs)}"
        print(f"| {case} | {med('reqPerSec'):.1f} | {med('p50'):.1f}ms | {p95:.1f}ms | {med('p99'):.1f}ms "
              f"| {fail:.2f}% | {errs} | {verdict} |")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("사용: python3 docs/performance/tools/item/item_report.py <결과 폴더>")
    main(Path(sys.argv[1]))
