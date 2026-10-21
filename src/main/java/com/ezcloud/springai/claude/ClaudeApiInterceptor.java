package com.ezcloud.springai.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Adapts Spring AI 1.0's Claude Messages API calls to current Claude models and records telemetry for each call.
 *
 * <p>Requests: removes sampling parameters (temperature, top_p, top_k) and thinking configuration, which current
 * models reject, and adds {@code output_config.effort} and server-side refusal fallbacks.
 *
 * <p>Responses: keeps only text and tool_use content blocks. Spring AI 1.0 turns every thinking block into its own
 * generation (so the first result can be an empty thinking block) and cannot parse {@code fallback} blocks.
 *
 * <p>Telemetry: logs the request ID, model, stop reason and token usage of every call, and counts refusals and
 * fallbacks. Latency and token metrics come from Spring AI's own {@code gen_ai.client.*} observations.
 */
public class ClaudeApiInterceptor implements ClientHttpRequestInterceptor {

    static final String MESSAGES_PATH = "/v1/messages";
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    static final String REFUSALS_METRIC = "claude.refusals";
    static final String FALLBACKS_METRIC = "claude.fallbacks";

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiInterceptor.class);
    private static final Set<String> UNSUPPORTED_REQUEST_FIELDS = Set.of("temperature", "top_p", "top_k", "thinking");
    private static final Set<String> SUPPORTED_RESPONSE_BLOCKS = Set.of("text", "tool_use");

    private final String claudeHost;
    private final ClaudeProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public ClaudeApiInterceptor(String claudeHost, ClaudeProperties properties, MeterRegistry meterRegistry,
                                ObjectMapper objectMapper) {
        this.claudeHost = claudeHost;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!isMessagesCall(request) || body.length == 0) {
            return execution.execute(request, body);
        }

        byte[] adaptedBody = adaptRequest(request.getHeaders(), body);
        ClientHttpResponse response = execution.execute(request, adaptedBody);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Claude call failed: status={} requestId={}", response.getStatusCode().value(),
                    requestId(response));
            return response;
        }
        return adaptResponse(response);
    }

    private boolean isMessagesCall(HttpRequest request) {
        return claudeHost.equalsIgnoreCase(request.getURI().getHost())
                && request.getURI().getPath().endsWith(MESSAGES_PATH);
    }

    private byte[] adaptRequest(HttpHeaders headers, byte[] body) throws IOException {
        ObjectNode json = (ObjectNode) objectMapper.readTree(body);
        json.remove(UNSUPPORTED_REQUEST_FIELDS);
        json.putObject("output_config").put("effort", properties.effort());

        if (properties.fallbacksEnabled()) {
            json.put("fallbacks", "default");
            String betas = headers.getFirst("anthropic-beta");
            headers.set("anthropic-beta", betas == null || betas.isBlank() ? FALLBACK_BETA : betas + "," + FALLBACK_BETA);
        }

        byte[] adapted = objectMapper.writeValueAsBytes(json);
        headers.setContentLength(adapted.length);
        return adapted;
    }

    private ClientHttpResponse adaptResponse(ClientHttpResponse response) throws IOException {
        JsonNode json;
        try (InputStream in = response.getBody()) {
            json = objectMapper.readTree(in);
        }

        String model = json.path("model").asText();
        String stopReason = json.path("stop_reason").asText();
        if (json.get("content") instanceof ArrayNode content) {
            ArrayNode kept = objectMapper.createArrayNode();
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("fallback".equals(type)) {
                    recordFallback(block);
                }
                if (SUPPORTED_RESPONSE_BLOCKS.contains(type)) {
                    kept.add(block);
                }
            }
            ((ObjectNode) json).set("content", kept);
        }

        if ("refusal".equals(stopReason)) {
            String category = json.path("stop_details").path("category").asText("unknown");
            meterRegistry.counter(REFUSALS_METRIC, "model", model, "category", category).increment();
            log.warn("Claude declined the request: model={} category={} requestId={}", model, category,
                    requestId(response));
        }

        JsonNode usage = json.path("usage");
        log.info("Claude call: requestId={} model={} stopReason={} inputTokens={} outputTokens={}",
                requestId(response), model, stopReason, usage.path("input_tokens").asLong(),
                usage.path("output_tokens").asLong());

        return new ReplacedBodyResponse(response, objectMapper.writeValueAsBytes(json));
    }

    private void recordFallback(JsonNode block) {
        String from = block.path("from").path("model").asText("unknown");
        String to = block.path("to").path("model").asText("unknown");
        meterRegistry.counter(FALLBACKS_METRIC, "from", from, "to", to).increment();
        log.warn("Claude request fell back from {} to {}", from, to);
    }

    private static String requestId(ClientHttpResponse response) {
        String id = response.getHeaders().getFirst("request-id");
        return id != null ? id : "n/a";
    }

    /** A response whose body has been rewritten; status and headers come from the original response. */
    private static final class ReplacedBodyResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] body;
        private final HttpHeaders headers;

        ReplacedBodyResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
            this.headers = new HttpHeaders();
            this.headers.putAll(delegate.getHeaders());
            this.headers.setContentLength(body.length);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
