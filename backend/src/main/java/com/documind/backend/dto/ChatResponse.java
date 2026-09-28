package com.documind.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outgoing response payload containing the LLM's grounded answer
 * along with verifiable reference citations from the source document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private UUID documentId;
    private String question;
    private String answer;
    private List<ReferenceDto> references;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
