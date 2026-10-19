package com.ezcloud.springai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable settings for retrieval and document chunking, bound from {@code app.rag.*}.
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        @DefaultValue("4") int topK,
        @DefaultValue("0.5") double similarityThreshold,
        @DefaultValue("800") int chunkSize,
        @DefaultValue("350") int minChunkSizeChars) {
}
