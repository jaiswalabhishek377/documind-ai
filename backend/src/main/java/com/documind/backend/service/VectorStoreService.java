package com.documind.backend.service;

import com.documind.backend.service.DocumentParserService.ParsedDocument;
import com.documind.backend.service.DocumentParserService.ParsedPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that handles chunking, embedding generation, and vector indexing
 * into PostgreSQL with the pgvector extension via Spring AI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    // Splits text into ~500 token chunks with 50 token overlap to preserve semantic context
    private final TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 20, 10000, true);

    /**
     * Chunks and indexes the extracted document pages into pgvector.
     */
    public void indexDocument(UUID documentId, ParsedDocument parsedDoc) {
        List<Document> allChunks = new ArrayList<>();

        for (ParsedPage page : parsedDoc.getPages()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("document_id", documentId.toString());
            metadata.put("page_number", page.getPageNumber());

            // Create initial Spring AI document for this page
            Document pageDoc = new Document(page.getText(), metadata);

            // Split page into smaller semantic chunks
            List<Document> chunks = splitter.apply(List.of(pageDoc));
            for (Document chunk : chunks) {
                // Ensure chunk metadata retains the page number and document ID
                chunk.getMetadata().put("document_id", documentId.toString());
                chunk.getMetadata().put("page_number", page.getPageNumber());
                allChunks.add(chunk);
            }
        }

        log.info("Indexing {} vector chunks for document {}", allChunks.size(), documentId);
        if (!allChunks.isEmpty()) {
            vectorStore.add(allChunks);
        }
    }

    /**
     * Performs cosine similarity search in pgvector for the most relevant chunks.
     */
    public List<Document> searchSimilar(UUID documentId, String query, int topK) {
        long start = System.currentTimeMillis();
        try {
            log.info("🔎 Querying pgvector for documentId: '{}', query: '{}'", documentId, query);
            var filter = new org.springframework.ai.vectorstore.filter.FilterExpressionBuilder()
                    .eq("document_id", documentId.toString())
                    .build();

            SearchRequest request = SearchRequest.query(query)
                    .withTopK(topK)
                    .withSimilarityThreshold(0.2)
                    .withFilterExpression(filter);

            List<Document> results = vectorStore.similaritySearch(request);
            log.info("🔎 pgvector returned {} matches in {} ms", results.size(), (System.currentTimeMillis() - start));
            return results;
        } catch (Exception e) {
            log.warn("Filter expression search failed ({} ms): {}, using fallback similarity search",
                    (System.currentTimeMillis() - start), e.getMessage());
            try {
                List<Document> broadResults = vectorStore.similaritySearch(
                        SearchRequest.query(query).withTopK(Math.max(topK * 4, 10))
                );
                List<Document> filtered = broadResults.stream()
                        .filter(doc -> Objects.equals(doc.getMetadata().get("document_id"), documentId.toString()))
                        .limit(topK)
                        .toList();
                log.info("🔎 Fallback similarity search returned {} matches in {} ms", filtered.size(), (System.currentTimeMillis() - start));
                return filtered;
            } catch (Exception ex) {
                log.error("Fallback similarity search also failed in {} ms: {}", (System.currentTimeMillis() - start), ex.getMessage());
                return Collections.emptyList();
            }
        }
    }

    /**
     * Purges all vectors belonging to a document from PostgreSQL when deleted.
     */
    public void deleteVectorsForDocument(UUID documentId) {
        try {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'document_id' = ?", documentId.toString());
            log.info("Successfully purged vector embeddings for document {}", documentId);
        } catch (Exception e) {
            log.error("Failed to delete vectors for document {}: {}", documentId, e.getMessage());
        }
    }
}
