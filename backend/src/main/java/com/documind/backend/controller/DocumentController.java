package com.documind.backend.controller;

import com.documind.backend.dto.DocumentResponse;
import com.documind.backend.dto.SummaryResponse;
import com.documind.backend.model.DocumentEntity;
import com.documind.backend.repository.ChatMessageRepository;
import com.documind.backend.repository.DocumentRepository;
import com.documind.backend.service.DocumentParserService;
import com.documind.backend.service.DocumentParserService.ParsedDocument;
import com.documind.backend.service.SummarizationService;
import com.documind.backend.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for document ingestion, history, management, and summarization.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentParserService parserService;
    private final VectorStoreService vectorStoreService;
    private final SummarizationService summarizationService;

    // Cache of full text in memory for immediate summarization requests
    private final java.util.concurrent.ConcurrentHashMap<UUID, String> textCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Upload a PDF or Word document (.docx/.doc).
     * Parses the file, indexes chunks into pgvector, and updates status to READY.
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        log.info("Received file upload: {} ({} bytes)", originalFilename, file.getSize());

        // 1. Create document entity in PROCESSING state
        DocumentEntity entity = DocumentEntity.builder()
                .fileName(originalFilename)
                .fileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .status("PROCESSING")
                .build();
        entity = documentRepository.save(entity);

        try {
            // 2. Parse text and page structure using PDFBox / Apache POI
            ParsedDocument parsedDoc = parserService.parse(file);
            entity.setPageCount(parsedDoc.getTotalPages());
            textCache.put(entity.getId(), parsedDoc.getFullText());

            // 3. Chunk and index into Supabase pgvector
            vectorStoreService.indexDocument(entity.getId(), parsedDoc);

            // 4. Mark status as READY
            entity.setStatus("READY");
            entity = documentRepository.save(entity);

            log.info("Document {} successfully parsed and indexed in pgvector", entity.getId());
            return ResponseEntity.ok(DocumentResponse.fromEntity(entity));

        } catch (Exception e) {
            log.error("Failed to process document {}: {}", entity.getId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setErrorMessage(e.getMessage());
            documentRepository.save(entity);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DocumentResponse.fromEntity(entity));
        }
    }

    /**
     * Retrieve all uploaded documents (Document History Dashboard).
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        List<DocumentResponse> documents = documentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(DocumentResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(documents);
    }

    /**
     * Retrieve a single document's metadata by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .map(entity -> ResponseEntity.ok(DocumentResponse.fromEntity(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Generate an AI summary for a specific document.
     */
    @PostMapping("/{id}/summarize")
    public ResponseEntity<SummaryResponse> summarizeDocument(@PathVariable UUID id) {
        String fullText = textCache.getOrDefault(id, "");
        SummaryResponse summary = summarizationService.generateSummary(id, fullText);
        return ResponseEntity.ok(summary);
    }

    /**
     * Delete a document, its chat history, and its vector embeddings atomically.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        if (!documentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // Purge vector store chunks
        vectorStoreService.deleteVectorsForDocument(id);
        
        // Purge chat history
        chatMessageRepository.deleteByDocumentId(id);

        // Delete document metadata
        documentRepository.deleteById(id);
        textCache.remove(id);

        log.info("Document {} and associated vectors/chats purged", id);
        return ResponseEntity.noContent().build();
    }
}
