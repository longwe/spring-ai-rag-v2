package com.ezcloud.springai.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class ClaudeClientConfig {

    /**
     * Registers {@link ClaudeApiInterceptor} on every auto-configured RestClient. Spring AI builds its Claude client
     * from that builder; the interceptor only acts on calls to the Claude API host.
     */
    @Bean
    public RestClientCustomizer claudeApiRestClientCustomizer(
            @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            ClaudeProperties properties, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        ClaudeApiInterceptor interceptor =
                new ClaudeApiInterceptor(URI.create(baseUrl).getHost(), properties, meterRegistry, objectMapper);
        return builder -> builder.requestInterceptor(interceptor);
    }
}
