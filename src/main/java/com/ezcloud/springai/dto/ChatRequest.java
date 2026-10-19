package com.ezcloud.springai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String question) {
}
