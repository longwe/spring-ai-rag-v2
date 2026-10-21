package com.ezcloud.springai.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Runs a real Spring AI {@link AnthropicChatModel} call through the interceptor against a mock Claude API.
 */
class ClaudeApiInterceptorTest {

    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";

    private SimpleMeterRegistry meterRegistry;
    private MockRestServiceServer server;
    private AnthropicChatModel chatModel;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ClaudeApiInterceptor interceptor = new ClaudeApiInterceptor("api.anthropic.com",
                new ClaudeProperties("medium", true), meterRegistry, new ObjectMapper());

        RestClient.Builder restClientBuilder = RestClient.builder().requestInterceptor(interceptor);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        AnthropicApi api = AnthropicApi.builder().apiKey("test-key").restClientBuilder(restClientBuilder).build();
        chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-opus-5-5").maxTokens(1000).temperature(0.8).build())
                .build();
    }

    @Test
    void adaptsRequestForCurrentClaudeModels() {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.output_config.effort").value("medium"))
                .andExpect(jsonPath("$.fallbacks").value("default"))
                .andExpect(header("anthropic-beta", org.hamcrest.Matchers.containsString(
                        ClaudeApiInterceptor.FALLBACK_BETA)))
                .andRespond(withSuccess(response("end_turn", ""), MediaType.APPLICATION_JSON));

        chatModel.call(new Prompt("What is RAG?"));

        server.verify();
    }

    @Test
    void dropsThinkingAndFallbackBlocksSoTheAnswerIsTheFirstResult() {
        String fallbackBlock = """
                {"type": "fallback", "from": {"model": "claude-opus-5-5"}, "to": {"model": "claude-opus-5"}},
                """;
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess(response("end_turn", fallbackBlock), MediaType.APPLICATION_JSON)
                        .headers(requestIdHeader()));

        ChatResponse response = chatModel.call(new Prompt("What is RAG?"));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResult().getOutput().getText()).isEqualTo("RAG grounds answers in your data.");
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(120);
        assertThat(meterRegistry.counter(ClaudeApiInterceptor.FALLBACKS_METRIC,
                "from", "claude-opus-5-5", "to", "claude-opus-5").count()).isEqualTo(1);
    }

    @Test
    void countsRefusalsByCategory() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess("""
                        {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                         "content": [], "stop_reason": "refusal",
                         "stop_details": {"type": "refusal", "category": "cyber"},
                         "usage": {"input_tokens": 50, "output_tokens": 0}}
                        """, MediaType.APPLICATION_JSON));

        chatModel.call(new Prompt("Something declined"));

        assertThat(meterRegistry.counter(ClaudeApiInterceptor.REFUSALS_METRIC,
                "model", "claude-opus-5-5", "category", "cyber").count()).isEqualTo(1);
    }

    private static String response(String stopReason, String extraBlocks) {
        return """
                {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                 "content": [
                   %s
                   {"type": "thinking", "thinking": "", "signature": "sig"},
                   {"type": "text", "text": "RAG grounds answers in your data."}
                 ],
                 "stop_reason": "%s",
                 "usage": {"input_tokens": 120, "output_tokens": 30}}
                """.formatted(extraBlocks, stopReason);
    }

    private static HttpHeaders requestIdHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("request-id", "req_123");
        return headers;
    }
}
