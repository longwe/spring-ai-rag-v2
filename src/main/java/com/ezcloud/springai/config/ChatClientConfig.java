package com.ezcloud.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions using the documents provided as context.
            If the context does not contain the answer, say that you don't know rather than guessing.
            """;

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore, RagProperties properties) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(properties.topK())
                        .similarityThreshold(properties.similarityThreshold())
                        .build())
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, QuestionAnswerAdvisor questionAnswerAdvisor) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(questionAnswerAdvisor, new SimpleLoggerAdvisor())
                .build();
    }
}
