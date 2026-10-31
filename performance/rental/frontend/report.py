"""브라우저에서 저장한 전후 원본의 3회 중위값·편차 집계."""
import json
from pathlib import Path
from statistics import median, pstdev

ROOT = Path(__file__).resolve().parents[3]
RAW = ROOT / "docs/performance/raw"
rows = []
for variant in ("before", "after"):
    samples = json.loads((RAW / f"rental-frontend-{variant}.json").read_text())
    for count in (10, 100, 1000):
        group = [r for r in samples if r["count"] == count]
        assert len(group) == 3 and {r["run"] for r in group} == {1, 2, 3}
        assert all(r["variant"] == variant and r["delay"] == 100 and r["errors"] == 0 for r in group)
        assert all(r["requests"] == r["completed"] == len(r["paths"]) for r in group)
        for r in group:
            assert all(p["status"] == 200 for p in r["paths"])
            expected = count // 2 if variant == "before" else min(count // 2, 20)
            assert r["cards"] == expected
            if variant == "after":
                assert not any("role=OWNER" in p["path"] for p in r["paths"])
        row = {"variant": variant, "count": count}
        for metric in ("firstCardMs", "readyMs", "requests", "cards"):
            values = [r[metric] for r in group]
            row[metric] = {"median": round(median(values), 2), "min": round(min(values), 2), "max": round(max(values), 2), "stddev": round(pstdev(values), 2)}
        rows.append(row)
(RAW / "rental-frontend-summary.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n")
for count in (10, 100, 1000):
    group = [r for r in rows if r["count"] == count]
    print(count, json.dumps(group, ensure_ascii=False))
