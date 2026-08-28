package com.sakh.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ChatSessionDetailResponse {

    private final Long id;
    private final String title;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<ChatMessageResponse> messages;
}