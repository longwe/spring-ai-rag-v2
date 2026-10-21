package com.ezcloud.springai.dto;

import java.util.List;

public record ChatResponse(String answer, List<String> sources, String model, TokenUsage usage) {

    public record TokenUsage(long inputTokens, long outputTokens) {
    }
}
