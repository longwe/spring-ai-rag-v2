package com.ezcloud.springai.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Claude request settings that Spring AI 1.0 cannot express, bound from {@code app.claude.*}.
 *
 * @param effort           thinking depth and token spend: low, medium, high, xhigh or max
 * @param fallbacksEnabled re-run requests that Claude declines on Anthropic's recommended fallback model
 */
@ConfigurationProperties(prefix = "app.claude")
public record ClaudeProperties(
        @DefaultValue("medium") String effort,
        @DefaultValue("true") boolean fallbacksEnabled) {
}
