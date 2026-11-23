"""Count MySQL SELECT commands for one isolated chat room list request."""

import json
import subprocess
import urllib.request
from pathlib import Path


HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.yaml"
FIXTURE = HERE / "local" / "fixtures-followup.json"
PROJECT = "billim-perf-followup"


def select_count():
    command = [
        "docker", "compose", "-p", PROJECT, "-f", str(COMPOSE), "exec", "-T", "mysql",
        "sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -N -uroot billim_perf -e \"SHOW GLOBAL STATUS LIKE 'Com_select'\"",
    ]
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return int(result.stdout.split()[-1])


def request_rooms(token, size):
    request = urllib.request.Request(
        f"http://localhost:18080/api/v1/chat/rooms?filter=ALL&page=0&size={size}",
        headers={"Cookie": f"BILLIM_SESSION={token}"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected status: {response.status}")
        return len(json.load(response)["items"])


def main():
    token = json.loads(FIXTURE.read_text(encoding="utf-8"))["pairs"][0]["owner"]["token"]
    for size in (1, 20, 50):
        request_rooms(token, size)
        deltas = []
        for _ in range(3):
            before = select_count()
            items = request_rooms(token, size)
            deltas.append(select_count() - before)
        print(json.dumps({"page_size": size, "items": items, "select_counts": deltas}))


if __name__ == "__main__":
    main()
