package com.n11.ai.interfaces.rest.dto;

import java.util.UUID;

public record ChatReply(
    UUID conversationId,
    String text
) {}
