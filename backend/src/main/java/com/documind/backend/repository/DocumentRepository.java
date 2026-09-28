package com.documind.backend.repository;

import com.documind.backend.model.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for DocumentEntity.
 * Spring Data JPA provides standard CRUD methods (findAll, findById, save, deleteById)
 * automatically at runtime without writing SQL.
 */
@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    
    // Returns documents ordered with newest uploads first for the history dashboard
    List<DocumentEntity> findAllByOrderByCreatedAtDesc();
}
