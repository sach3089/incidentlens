#!/usr/bin/env bash
set -euo pipefail
for port in 8080 8081 8082; do
  ready=false
  for attempt in $(seq 1 120); do
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null; then ready=true; break; fi
    sleep 2
  done
  if [ "$ready" != true ]; then echo "Service on $port did not become healthy"; exit 1; fi
done
python3 scripts/demo.py
python3 - <<'PY'
import json
r=json.load(open('report.json'))
assert r['status']=='COMPLETE',r
assert all(e['status']=='OK' for e in r['evidence']),r
assert any(f['type']=='OBSERVATION' for f in r['findings']),r
print('PASS: metrics, ERROR logs, error traces, persisted report, and synthetic change evidence.')
PY
