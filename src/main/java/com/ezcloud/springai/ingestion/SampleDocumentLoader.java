package com.ezcloud.springai.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Optionally ingests a bundled sample document at startup so the app can be tried out immediately.
 */
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "load-sample-on-startup", havingValue = "true")
public class SampleDocumentLoader implements ApplicationRunner {

    private final DocumentIngestionService ingestionService;
    private final Resource sampleDocument;

    public SampleDocumentLoader(DocumentIngestionService ingestionService,
                                @Value("classpath:docs/spring-ai-overview.md") Resource sampleDocument) {
        this.ingestionService = ingestionService;
        this.sampleDocument = sampleDocument;
    }

    @Override
    public void run(ApplicationArguments args) {
        ingestionService.ingest(sampleDocument, sampleDocument.getFilename());
    }
}
