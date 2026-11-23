"""Measure local outbox-to-notification processing on the isolated DB only."""

import json
import os
import secrets
import subprocess
import sys
import time
from pathlib import Path


HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.yaml"
FIXTURE = HERE / "local" / os.environ.get("PERF_FIXTURE_NAME", "fixtures.json")


def query(sql):
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE), "exec", "-T", "mysql", "sh", "-c",
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N billim_perf'],
        input=sql, text=True, capture_output=True, check=True,
    )
    return result.stdout.strip()


def main():
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 100
    if not 1 <= count <= 1000:
        raise SystemExit("count must be between 1 and 1000")
    if "name: billim-perf" not in COMPOSE.read_text():
        raise SystemExit("Isolated billim-perf compose file is required")
    members = [pair["requester"]["memberId"] for pair in json.loads(FIXTURE.read_text())["pairs"]]
    run = secrets.token_hex(6)
    prefix = f"perf-outbox-{run}-"
    statements = ["START TRANSACTION;"]
    for index in range(count):
        member = members[index % len(members)]
        payload = json.dumps({
            "recipients": [{"memberId": member, "role": "REPORTER", "targetType": None, "targetId": None}],
            "targetType": "REPORT", "targetId": 900000 + index,
            "display": {"actorName": None, "itemTitle": None, "startDate": None, "endDate": None,
                        "placeName": None, "appointmentAt": None, "label": None},
        }, separators=(",", ":"))
        statements.append(
            "INSERT INTO outbox_events (event_key,event_type,aggregate_type,aggregate_id,aggregate_version,payload,status,attempts,available_at,created_at,updated_at) "
            f"VALUES ('{prefix}{index}','REPORT_RESOLVED','REPORT',{900000 + index},1,'{payload}','PENDING',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));"
        )
    statements.append("COMMIT;")
    query("\n".join(statements))
    started = time.monotonic()
    while time.monotonic() - started < 90:
        rows = query(
            "SELECT status,COUNT(*) FROM outbox_events "
            f"WHERE event_key LIKE '{prefix}%' GROUP BY status;"
        )
        totals = dict(line.split("\t") for line in rows.splitlines()) if rows else {}
        if totals.get("FAILED"):
            raise SystemExit(f"Outbox failed: {totals}")
        if int(totals.get("DONE", 0)) == count:
            elapsed = time.monotonic() - started
            lag = query(
                "SELECT TIMESTAMPDIFF(MICROSECOND,created_at,processed_at) FROM outbox_events "
                f"WHERE event_key LIKE '{prefix}%' ORDER BY processed_at;"
            )
            lags = sorted(int(row) / 1000 for row in lag.splitlines())
            delivered = int(query(
                "SELECT COUNT(*) FROM notifications n JOIN outbox_events e ON n.event_id=e.id "
                f"WHERE e.event_key LIKE '{prefix}%';"
            ))
            if delivered != count:
                raise SystemExit(f"Notification count mismatch: {delivered}/{count}")
            print(f"events={count} notifications={delivered} elapsed_s={elapsed:.2f} throughput_per_s={count / elapsed:.1f} "
                  f"lag_p95_ms={lags[int((count - 1) * .95)]:.1f} lag_max_ms={lags[-1]:.1f}")
            return
        time.sleep(0.5)
    raise SystemExit(f"Timed out waiting for outbox: {totals}")


if __name__ == "__main__":
    main()
