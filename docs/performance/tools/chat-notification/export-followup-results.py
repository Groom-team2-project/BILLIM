"""Export measured values without session fixtures to the tracked report evidence."""

import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
LOCAL = HERE / "local"
TARGET = HERE.parent.parent / "raw" / "chat-followup-20261031" / "summary.json"


def read(name):
    return json.loads((LOCAL / name).read_text(encoding="utf-8"))


def main():
    result = {"chat_room_list": [], "idle_websocket": [], "chat_send": [], "outbox": {}}
    for stage in ("baseline", "rental-once", "batch-base"):
        for size in (1, 20, 50):
            for run in (1, 2, 3):
                metrics = read(f"{stage}-20261031-rooms-{size}-run-{run}.json")["metrics"]
                result["chat_room_list"].append({
                    "stage": stage, "page_size": size, "run": run,
                    "requests": metrics["http_reqs"]["count"],
                    "rps": metrics["http_reqs"]["rate"],
                    "p50_ms": metrics["http_req_duration"]["med"],
                    "p95_ms": metrics["http_req_duration"]["p(95)"],
                    "failure_rate": metrics["app_failures"]["value"],
                })
    for stage in ("idle-before", "idle-after"):
        for run in (1, 2, 3):
            metrics = read(f"{stage}-20261031-idle-run-{run}.json")["metrics"]
            result["idle_websocket"].append({
                "stage": stage, "run": run,
                "connection_failure_rate": metrics["app_failures"]["value"],
            })
    for stage in ("send-stable", "send-64"):
        for variant in ("distributed", "hot"):
            for run in (1, 2, 3):
                metrics = read(f"{stage}-20261031-{variant}-run-{run}.json")["metrics"]
                result["chat_send"].append({
                    "stage": stage, "variant": variant, "run": run,
                    "requests": metrics["http_reqs"]["count"],
                    "rps": metrics["http_reqs"]["rate"],
                    "p95_ms": metrics["http_req_duration"]["p(95)"],
                    "failure_rate": metrics["app_failures"]["value"],
                })
    for stage in ("outbox-batch50", "outbox-batch100"):
        path = LOCAL / f"{stage}-docker-20261031.json"
        result["outbox"][stage] = read(path.name)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(TARGET)


if __name__ == "__main__":
    main()
