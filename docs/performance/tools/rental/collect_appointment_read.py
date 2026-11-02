"""약속 전체 조회 테스트의 SQL·서비스 시간·응답 및 소스 지문 추출."""

import argparse
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import xml.etree.ElementTree as ET


def percentile(values, rate):
    values = sorted(values)
    position = (len(values) - 1) * rate
    lower = int(position)
    upper = min(lower + 1, len(values) - 1)
    return values[lower] + (values[upper] - values[lower]) * (position - lower)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    suite = ET.parse(root / "backend/build/test-results/test/TEST-com.billim.domain.rental.service.AppointmentReadQueryDbTest.xml").getroot()
    if any(int(suite.attrib.get(key, "0")) for key in ("failures", "errors", "skipped")):
        raise SystemExit("전체 테스트 성공 결과만 추출 가능")
    rows = [json.loads(p.attrib["value"]) for p in suite.findall("testcase/properties/property")
            if p.attrib["name"] == "appointmentRead"]
    scenarios = {r["scenario"] for r in rows}
    if len(rows) != 18 or len(scenarios) != 6:
        raise SystemExit("6개 상태 × 3회 계측 필요")
    responses = {}
    for row in rows:
        if len(row["sql"]) != 60 or len(row["nanos"]) != 60:
            raise SystemExit("각 회차 60개 표본 필요")
        response = row.pop("response")
        if row["scenario"] in responses and responses[row["scenario"]] != response:
            raise SystemExit("회차별 응답 불일치")
        responses[row["scenario"]] = response
        row["sqlUnique"] = sorted(set(row["sql"]))
        row["p50Ms"] = percentile(row["nanos"], .5) / 1e6
        row["p95Ms"] = percentile(row["nanos"], .95) / 1e6
    sources = [
        "backend/src/main/java/com/billim/domain/rental/service/AppointmentService.java",
        "backend/src/main/java/com/billim/domain/rental/repository/AppointmentProposalRepository.java",
        "backend/src/main/java/com/billim/domain/community/repository/CommunityPlaceRepository.java",
        "backend/src/test/java/com/billim/domain/rental/service/AppointmentReadQueryDbTest.java",
    ]
    output = {
        "timestamp": suite.attrib["timestamp"],
        "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
        "sourceSha256": {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources},
        "tests": int(suite.attrib["tests"]),
        "measurement": "서비스 트랜잭션 종료까지 System.nanoTime·Hibernate prepareStatementCount. HTTP·세션 인증 제외",
        "summary": {scenario: {
            "sqlUnique": sorted({v for r in rows if r["scenario"] == scenario for v in r["sql"]}),
            "p50MedianMs": statistics.median(r["p50Ms"] for r in rows if r["scenario"] == scenario),
            "p95MedianMs": statistics.median(r["p95Ms"] for r in rows if r["scenario"] == scenario),
        } for scenario in sorted(scenarios)},
        "responses": responses,
        "rows": rows,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
