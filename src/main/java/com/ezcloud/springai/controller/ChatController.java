package com.ezcloud.springai.controller;

import com.ezcloud.springai.dto.ChatRequest;
import com.ezcloud.springai.dto.ChatResponse;
import com.ezcloud.springai.ingestion.DocumentIngestionService;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    static final String REFUSAL_ANSWER = "Claude declined to answer this question.";

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ChatClientResponse response = chatClient.prompt()
                .user(request.question())
                .call()
                .chatClientResponse();

        org.springframework.ai.chat.model.ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return new ChatResponse("", sourcesOf(response), null, null);
        }

        Generation result = chatResponse.getResult();
        boolean refused = result != null && "refusal".equals(result.getMetadata().getFinishReason());
        String answer = refused ? REFUSAL_ANSWER : result != null ? result.getOutput().getText() : "";

        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata.getUsage();
        ChatResponse.TokenUsage tokens = usage == null ? null : new ChatResponse.TokenUsage(
                valueOf(usage.getPromptTokens()), valueOf(usage.getCompletionTokens()));
        return new ChatResponse(answer, sourcesOf(response), metadata.getModel(), tokens);
    }

    private static long valueOf(Integer tokens) {
        return tokens != null ? tokens : 0L;
    }

    @SuppressWarnings("unchecked")
    private static List<String> sourcesOf(ChatClientResponse response) {
        Object retrieved = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(retrieved instanceof List<?> documents)) {
            return List.of();
        }
        return ((List<Document>) documents).stream()
                .map(doc -> doc.getMetadata().get(DocumentIngestionService.SOURCE_METADATA_KEY))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .toList();
    }
}
