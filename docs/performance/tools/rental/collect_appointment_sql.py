"""약속 응답 통합 테스트의 SQL 계측값·소스 지문 추출. 전체 SQL 로그 제외."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    result = root / "backend/build/test-results/test/TEST-com.billim.domain.rental.service.AppointmentCommandQueryDbTest.xml"
    suite = ET.parse(result).getroot()
    if any(int(suite.attrib.get(key, "0")) for key in ("failures", "errors", "skipped")):
        raise SystemExit("성공한 전체 측정 결과만 추출 가능")
    rows = []
    for case in suite.findall("testcase"):
        properties = case.findall("properties/property")
        if len(properties) != 24:
            raise SystemExit("시나리오별 명령 6개 계측 필요")
        for offset in range(0, len(properties), 4):
            row = {p.attrib["name"]: p.attrib["value"] for p in properties[offset:offset + 4]}
            if set(row) != {"action", "kind", "populatedOther", "sql"}:
                raise SystemExit("계측 필드 불일치")
            row["sql"] = int(row["sql"])
            row["populatedOther"] = row["populatedOther"] == "true"
            rows.append(row)
    if len(rows) != 24:
        raise SystemExit("전달·반납 × 다른 슬롯 2상태 × 명령 6개 필요")
    sources = [
        "backend/src/main/java/com/billim/domain/rental/service/AppointmentService.java",
        "backend/src/main/java/com/billim/domain/rental/service/AppointmentCommandService.java",
        "backend/src/test/java/com/billim/domain/rental/service/AppointmentCommandQueryDbTest.java",
    ]
    output = {
        "timestamp": suite.attrib["timestamp"],
        "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
        "sourceSha256": {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources},
        "tests": int(suite.attrib["tests"]),
        "measurement": "Hibernate prepareStatementCount, service transaction including commit; HTTP/authentication excluded",
        "rows": rows,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
