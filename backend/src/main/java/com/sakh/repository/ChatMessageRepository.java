package com.sakh.repository;

import com.sakh.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    long countBySessionId(Long sessionId);
}