package com.documind.backend.repository;

import com.documind.backend.model.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ChatMessageEntity.
 * Handles fetching previous chat history for a given document.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    // Retrieve chat history for a specific document ordered chronologically
    List<ChatMessageEntity> findByDocumentIdOrderByCreatedAtAsc(UUID documentId);

    // Delete all messages associated with a document when the document is deleted
    void deleteByDocumentId(UUID documentId);
}
