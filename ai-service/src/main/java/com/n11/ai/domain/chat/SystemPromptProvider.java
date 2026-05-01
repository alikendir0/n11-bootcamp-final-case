package com.n11.ai.domain.chat;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SystemPromptProvider {
    private final String prompt;
    public SystemPromptProvider() {
        try {
            this.prompt = new String(new ClassPathResource("prompts/system-prompt-tr.txt")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("system-prompt-tr.txt missing from classpath", e);
        }
    }
    public String prompt() { return prompt; }
}
