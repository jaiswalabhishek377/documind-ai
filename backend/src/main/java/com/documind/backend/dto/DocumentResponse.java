package com.documind.backend.dto;

import com.documind.backend.model.DocumentEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO returned to Angular frontend representing document metadata and status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private UUID id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer pageCount;
    private String status;
    private String summary;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static DocumentResponse fromEntity(DocumentEntity entity) {
        return DocumentResponse.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .pageCount(entity.getPageCount())
                .status(entity.getStatus())
                .summary(entity.getSummary())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
