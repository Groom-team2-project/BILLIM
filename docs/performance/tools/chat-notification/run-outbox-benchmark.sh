#!/bin/sh
set -eu

export COMPOSE_PROJECT_NAME=billim-perf-followup
export PERF_FIXTURE_NAME=fixtures-followup.json
export PERF_OUTBOX_ENABLED=true

for batch in 50 100; do
  export PERF_OUTBOX_BATCH_SIZE="$batch"
  docker compose -p billim-perf-followup -f /scripts/compose.yaml up -d --no-build --force-recreate backend
  sleep 20
  python3 /scripts/measure-outbox.py "outbox-batch${batch}-docker-20261031" --events 500 --runs 3
done
