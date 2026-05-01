package com.n11.ai.domain.chat;

/** Mirror of com.n11.ai.port.dto.MessageRole. Kept as a separate enum so JPA
 *  string mapping doesn't bleed port types into the persistence layer. */
public enum MessageRoleEntity { USER, ASSISTANT, TOOL }
