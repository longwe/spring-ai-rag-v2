# spring-ai-rag

A Retrieval-Augmented Generation (RAG) service built with Spring Boot 3.5 and Spring AI 1.0.
Upload PDF, TXT or Markdown documents. They are chunked, embedded with OpenAI and stored in PGVector.
Then ask questions answered from those documents.

## Requirements

- Java 21+
- Docker (for PostgreSQL + pgvector)
- An OpenAI API key

## Run

```bash
docker compose up -d                 # start pgvector on localhost:5432
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
# {"answer":"...","sources":["my-doc.pdf"]}
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `app.rag.top-k` | `4` | Number of chunks retrieved per question |
| `app.rag.similarity-threshold` | `0.5` | Minimum similarity for a chunk to be used |
| `app.rag.chunk-size` | `800` | Target chunk size in tokens |
| `app.rag.min-chunk-size-chars` | `350` | Minimum chunk size in characters |
| `app.rag.load-sample-on-startup` | `false` | Ingest the bundled sample document at startup |

Database connection: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

## Project layout

```
com.ezcloud.springai
├── SpringAiRagApplication
├── config        ChatClient + QuestionAnswerAdvisor wiring, RagProperties
├── controller    REST endpoints for documents and chat
├── dto           Request/response records
├── exception     Global error handling
└── ingestion     Document reading, chunking and vector-store loading
```
