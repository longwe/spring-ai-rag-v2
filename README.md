# spring-ai-rag

A Retrieval-Augmented Generation (RAG) service built with Spring Boot 3.5 and Spring AI 1.0.
Upload PDF, TXT or Markdown documents. They are chunked, embedded with OpenAI and stored in PGVector.
Then ask questions, answered by Claude (`claude-opus-5-5`) from those documents.

## Requirements

- Java 21+
- Docker (for PostgreSQL + pgvector, Prometheus and Grafana)
- An Anthropic API key (chat) and an OpenAI API key (embeddings)

## Run

```bash
docker compose up -d                 # pgvector (5432), Prometheus (9090), Grafana (3000)
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Set `app.rag.load-sample-on-startup=true` to ingest the bundled `docs/spring-ai-overview.md` at startup.

## API

Upload a document:

```bash
curl -F "file=@my-doc.pdf" http://localhost:8080/api/documents
# {"source":"my-doc.pdf","chunks":12}
```

Ask a question:

```bash
curl -H "Content-Type: application/json" \
     -d '{"question":"What is RAG?"}' \
     http://localhost:8080/api/chat
# {"answer":"...","sources":["my-doc.pdf"],"model":"claude-opus-5-5","usage":{"inputTokens":812,"outputTokens":164}}
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `app.claude.effort` | `medium` | Claude effort: `low`, `medium`, `high`, `xhigh` or `max` |
| `app.claude.fallbacks-enabled` | `true` | Re-run requests Claude declines on Anthropic's recommended fallback model |
| `app.rag.top-k` | `4` | Number of chunks retrieved per question |
| `app.rag.similarity-threshold` | `0.3` | Minimum similarity for a chunk to be used |
| `app.rag.chunk-size` | `800` | Target chunk size in tokens |
| `app.rag.min-chunk-size-chars` | `350` | Minimum chunk size in characters |
| `app.rag.load-sample-on-startup` | `false` | Ingest the bundled sample document at startup |

Database connection: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

## Claude telemetry

Every Claude call is logged by `ClaudeApiInterceptor` with its request ID (quote it to Anthropic support),
model, stop reason and token usage:

```
Claude call: requestId=req_... model=claude-opus-5-5 stopReason=end_turn inputTokens=812 outputTokens=164
```

Metrics at `/actuator/metrics`:

| Metric | Source | What it measures |
|---|---|---|
| `gen_ai.client.operation` | Spring AI | Duration of each Claude call (tags: model, operation) |
| `gen_ai.client.token.usage` | Spring AI | Tokens used, tagged by `gen_ai.token.type` (input / output / total) |
| `claude.refusals` | this app | Requests Claude declined, tagged by model and category |
| `claude.fallbacks` | this app | Requests re-run on a fallback model, tagged by from / to model |

### Prometheus

Metrics are exported in Prometheus format at `/actuator/prometheus`, tagged with `application="spring-ai-rag"`.
Dots in names become underscores, so the metrics above appear as `gen_ai_client_operation_seconds`,
`gen_ai_client_token_usage_total`, `claude_refusals_total` and `claude_fallbacks_total`. The two `claude_*`
counters only appear after their first event.

### Prometheus and Grafana

`docker compose up -d` also starts:

- **Prometheus** at http://localhost:9090, scraping the app every 15 seconds. The app runs on the host, so the
  scrape target is `host.docker.internal:8080` (see `monitoring/prometheus/prometheus.yml`).
- **Grafana** at http://localhost:3000 (login `admin` / `admin`; override with `GRAFANA_ADMIN_USER` and
  `GRAFANA_ADMIN_PASSWORD`). The **spring-ai-rag: Claude telemetry** dashboard opens as the home page and shows
  Claude tokens, calls, latency, refusals and fallbacks, `/api/chat` latency, and OpenAI embedding tokens.

Both are bound to `127.0.0.1` only. The dashboard and data source are provisioned from `monitoring/grafana/`;
edit `monitoring/grafana/dashboards/spring-ai-rag.json` to change the dashboard. Short time ranges with few calls
can show fractional totals, because Prometheus extrapolates `increase()` from sparse samples.

Example queries:

```promql
# Claude tokens per minute, by type
sum by (gen_ai_token_type) (rate(gen_ai_client_token_usage_total{gen_ai_system="anthropic"}[5m])) * 60

# Average Claude call latency
rate(gen_ai_client_operation_seconds_sum{gen_ai_system="anthropic"}[5m])
  / rate(gen_ai_client_operation_seconds_count{gen_ai_system="anthropic"}[5m])
```

### Spring AI 1.0 compatibility

Spring AI 1.0 predates current Claude models, so `ClaudeApiInterceptor` also adapts each Claude call:
it removes `temperature`/`top_p`/`top_k`/`thinking` (rejected by `claude-opus-5-5`), adds
`output_config.effort` and `fallbacks: "default"`, and keeps only text and tool-use blocks in responses
(Spring AI 1.0 would otherwise return an empty thinking block as the answer, and cannot parse `fallback` blocks).

## Project layout

```
com.ezcloud.springai
├── SpringAiRagApplication
├── claude        Claude API adapter and telemetry (ClaudeApiInterceptor)
├── config        ChatClient + QuestionAnswerAdvisor wiring, RagProperties
├── controller    REST endpoints for documents and chat
├── dto           Request/response records
├── exception     Global error handling
└── ingestion     Document reading, chunking and vector-store loading

monitoring/
├── prometheus    Scrape config
└── grafana       Data source, dashboard provisioning and the Claude telemetry dashboard
```
