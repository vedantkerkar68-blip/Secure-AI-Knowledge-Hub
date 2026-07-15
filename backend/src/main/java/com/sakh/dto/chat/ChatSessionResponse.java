package com.sakh.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class ChatSessionResponse {

    private final Long id;
    private final String title;
    private final Instant createdAt;
}
