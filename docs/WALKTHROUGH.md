# Learn observability with IncidentLens

## 1. Send a request and follow its path

Call `curl http://localhost:8081/api/v1/orders/demo`. The order controller calls inventory with a three-second read timeout. A successful response contains synthetic stock data. There are no real payments, account numbers, or customer records.

Open Grafana → IncidentLens → Services. Request rate comes from a counter. Error fraction uses HTTP 5xx counters. p95 is estimated from histogram buckets: it is not the average, nor an exact percentile of every raw request. At low traffic it can be noisy or absent. The JVM panel helps distinguish an application-level symptom from memory usage.

## 2. Read logs and traces

Grafana Explore → Tempo: query `{ resource.service.name = "order-service" }`. Inspect HTTP server/client spans and the inventory call. The Java agent instruments supported libraries and propagates W3C trace context. The services do not manually create fake trace IDs.

Grafana Explore → Loki: `{service="order-service"} | json`. The agent adds trace context to Logback MDC, and structured logging includes MDC fields. Click the derived trace_id link to Tempo. Service identity is a low-cardinality stream label; trace IDs remain in the JSON line, not Loki stream labels or Prometheus labels.

Metrics are independent of traces. Micrometer produces Prometheus metrics; the OTel agent's metrics/log exporters are disabled to prevent duplicate signal paths. The Collector handles traces. Alloy handles file logs.

## 3. Introduce a failure

`python3 scripts/demo.py` creates healthy traffic, records a synthetic deployment marker, enables a controlled inventory failure, generates failed traffic, and requests a report. It reads .env; do not paste real credentials into demo commands or logs.

The fault route exists only on the inventory demo mode and requires X-Demo-Key. It has bounded delay settings. It is not a production chaos engineering API and should not be deployed as a public endpoint.

The synthetic marker deliberately does NOT change service.version or deploy an image. It teaches temporal correlation without pretending a real deployment occurred.

## 4. Read report.json

- `evidence[].query` shows the PromQL, LogQL, or TraceQL expression used.
- Prometheus queries use the requested end timestamp. The baseline is the immediately preceding window of the same duration.
- Logs include selected messages and trace IDs, not arbitrary raw payloads.
- Tempo provides a small set of example traces; an error somewhere in a trace is not sufficient to prove that the root service caused it.
- `findings` explicitly label OBSERVATION, HYPOTHESIS, and COVERAGE_GAP.
- `deployments` is a timestamp-sorted event list, not a reconstruction of every request. Detailed trace timing remains in Tempo.

If a source is unavailable, investigate the backend and exporter before interpreting the missing data. If everything is EMPTY, check traffic, timestamps, scrape targets, ingestion delay, and labels. A green health endpoint only shows the process can answer its health probe.

## 5. Repeat with latency

Use the fault endpoint with `delayMs: 1500, fail: false` and generate traffic. You should see increased duration without necessarily seeing 5xx errors. Reset to `delayMs: 0, fail: false` afterward. This illustrates why successful status codes alone do not describe user experience.

## 6. Explain the project in an interview

“I instrumented two Spring Boot services, collected metrics/logs/traces using distinct pipelines, and built a third API to query those systems for a bounded time window. Reports persist evidence and explicitly distinguish correlation from causation. Docker Compose provides a reproducible lab; GitHub Actions verifies the complete pipeline.”

## Troubleshooting

- Inspect `docker compose ps` and `docker compose logs <service>`.
- Prometheus /targets should show three UP application targets.
- Loki /ready and Tempo /ready may take several seconds to become ready.
- Check the application agent reports service.name and an OTLP endpoint at startup.
- Check Alloy can read /logs and the Collector can reach Tempo.
- Allow at least two Prometheus scrapes before rates can be computed; use a longer traffic window for useful baselines.
