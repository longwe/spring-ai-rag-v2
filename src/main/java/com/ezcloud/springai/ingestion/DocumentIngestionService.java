package com.ezcloud.springai.ingestion;

import com.ezcloud.springai.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Reads a document, splits it into chunks, and stores the chunk embeddings in the vector store.
 */
@Service
public class DocumentIngestionService {

    public static final String SOURCE_METADATA_KEY = "source";

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public DocumentIngestionService(VectorStore vectorStore, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .build();
    }

    public int ingest(Resource resource, String source) {
        List<Document> documents = readerFor(resource, source).get();
        List<Document> chunks = splitter.apply(documents);
        chunks.forEach(chunk -> chunk.getMetadata().put(SOURCE_METADATA_KEY, source));

        vectorStore.add(chunks);
        log.info("Ingested '{}' as {} chunks", source, chunks.size());
        return chunks.size();
    }

    private DocumentReader readerFor(Resource resource, String source) {
        String name = source.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return new PagePdfDocumentReader(resource);
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return new TextReader(resource);
        }
        throw new UnsupportedDocumentTypeException(source);
    }
}
