package com.documind.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a referenced citation extracted from the document
 * that was used to answer a natural language question.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceDto {
    private Integer pageNumber;
    private String snippet;
    private Double score; // Relevance / Cosine similarity score
}
