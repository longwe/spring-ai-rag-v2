package com.ezcloud.springai.ingestion;

import com.ezcloud.springai.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DocumentIngestionServiceTest {

    private VectorStore vectorStore;
    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        service = new DocumentIngestionService(vectorStore, new RagProperties(4, 0.5, 800, 350));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestsTextDocumentAndTagsChunksWithSource() {
        int chunks = service.ingest(new ClassPathResource("docs/spring-ai-overview.md"), "spring-ai-overview.md");

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(chunks).isPositive().isEqualTo(captor.getValue().size());
        assertThat(captor.getValue())
                .allSatisfy(doc -> assertThat(doc.getMetadata())
                        .containsEntry(DocumentIngestionService.SOURCE_METADATA_KEY, "spring-ai-overview.md"));
    }

    @Test
    void rejectsUnsupportedDocumentType() {
        assertThatThrownBy(() -> service.ingest(new ByteArrayResource(new byte[0]), "data.xlsx"))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        verify(vectorStore, never()).add(any());
    }
}
