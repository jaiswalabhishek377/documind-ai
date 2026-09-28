package com.documind.backend.controller;

import com.documind.backend.dto.ChatRequest;
import com.documind.backend.dto.ChatResponse;
import com.documind.backend.model.ChatMessageEntity;
import com.documind.backend.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for conversational RAG Q&A and conversation history.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final RagService ragService;

    /**
     * Ask a natural language question about a specific document.
     * Returns a grounded answer with verifiable document citations.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragService.askQuestion(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve previous Q&A conversation history for a document.
     */
    @GetMapping("/history/{documentId}")
    public ResponseEntity<List<ChatMessageEntity>> getChatHistory(@PathVariable UUID documentId) {
        List<ChatMessageEntity> history = ragService.getChatHistory(documentId);
        return ResponseEntity.ok(history);
    }
}
