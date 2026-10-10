package com.sakh.backend;

import com.sakh.dto.chat.ChatResponse;
import com.sakh.exception.ResourceNotFoundException;
import com.sakh.security.RateLimitService;
import com.sakh.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-boundary tests for {@link com.sakh.controller.ChatController}.
 *
 * <p>These tests exercise the real Spring MVC dispatch, validation, security,
 * and exception-handling layers while stubbing the {@link ChatService} collaborator.
 * The context path is {@code /api} but MockMvc dispatches against the servlet
 * mapping directly, so the chat endpoint is reached at {@code /chat}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

    private static final Long SESSION_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void allowRequestsThroughRateLimiter() {
        when(rateLimitService.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "user@sakh.com", roles = {"USER"})
    void sendMessage_withValidRequest_returns200AndDelegatesToService() throws Exception {
        ChatResponse response = ChatResponse.builder()
                .answer("The resignation policy requires 30 days notice.")
                .confidence(BigDecimal.valueOf(0.95))
                .citations(List.of())
                .build();
        when(chatService.sendMessage(SESSION_ID, "What is the resignation policy?", null))
                .thenReturn(response);

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The resignation policy requires 30 days notice."))
                .andExpect(jsonPath("$.confidence").value(0.95))
                .andExpect(jsonPath("$.citations").isArray());

        verify(chatService).sendMessage(SESSION_ID, "What is the resignation policy?", null);
    }

    @Test
    @WithMockUser(username = "user@sakh.com", roles = {"USER"})
    void sendMessage_withBlankQuestion_returns400AndDoesNotInvokeService() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": " + SESSION_ID + ", \"question\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."));

        verify(chatService, never()).sendMessage(eq(SESSION_ID), anyString(), eq(null));
    }

    @Test
    @WithMockUser(username = "user@sakh.com", roles = {"USER"})
    void sendMessage_withMissingSessionId_returns400AndDoesNotInvokeService() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What is the policy?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."));

        verify(chatService, never()).sendMessage(eq(SESSION_ID), anyString(), eq(null));
    }

    @Test
    @WithMockUser(username = "user@sakh.com", roles = {"USER"})
    void sendMessage_whenSessionNotFound_returns404() throws Exception {
        when(chatService.sendMessage(SESSION_ID, "What is the policy?", null))
                .thenThrow(new ResourceNotFoundException("Chat session not found: " + SESSION_ID));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": " + SESSION_ID + ", \"question\": \"What is the policy?\"}"))
                .andExpect(status().isNotFound());

        verify(chatService).sendMessage(SESSION_ID, "What is the policy?", null);
    }

    @Test
    void sendMessage_withoutAuthentication_returns401AndDoesNotInvokeService() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());

        verify(chatService, never()).sendMessage(eq(SESSION_ID), anyString(), eq(null));
    }

    @Test
    @WithMockUser(username = "user@sakh.com", roles = {"USER"})
    void chatStream_withValidRequest_returns200AndDelegatesToService() throws Exception {
        when(chatService.sendMessageStream(SESSION_ID, "What is the resignation policy?", null))
                .thenReturn(Flux.just("The ", "resignation ", "policy."));

        mockMvc.perform(post("/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());

        verify(chatService).sendMessageStream(SESSION_ID, "What is the resignation policy?", null);
    }

    private String validBody() {
        return "{\"sessionId\": " + SESSION_ID + ", \"question\": \"What is the resignation policy?\"}";
    }
}
