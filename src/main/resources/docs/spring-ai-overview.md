# Spring AI Overview

Spring AI is an application framework for AI engineering. It applies Spring ecosystem design
principles, such as portability and modular design, to the AI domain.

## Retrieval-Augmented Generation

Retrieval-Augmented Generation (RAG) is a technique that grounds a language model's answers in
your own data. Documents are split into chunks, each chunk is converted into an embedding vector,
and the vectors are stored in a vector database such as PGVector. At question time, the most
similar chunks are retrieved and added to the prompt as context.

## Key building blocks

- ChatClient: a fluent API for talking to chat models.
- Advisors: interceptors that enrich prompts; QuestionAnswerAdvisor performs RAG retrieval.
- VectorStore: an abstraction over vector databases.
- DocumentReader and TextSplitter: an ETL pipeline for loading and chunking documents.
