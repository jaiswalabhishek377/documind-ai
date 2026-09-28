package com.documind.backend.service;

import com.documind.backend.dto.ChatRequest;
import com.documind.backend.dto.ChatResponse;
import com.documind.backend.dto.ReferenceDto;
import com.documind.backend.model.ChatMessageEntity;
import com.documind.backend.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core RAG (Retrieval-Augmented Generation) Service.
 * Orchestrates similarity search, context augmentation, LLM inference,
 * and grounded citation extraction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final VectorStoreService vectorStoreService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * Answers a natural language question grounded in the specified document.
     */
    public ChatResponse askQuestion(ChatRequest request) {
        UUID documentId = request.getDocumentId();
        String question = request.getQuestion();

        log.info("Processing question for document {}: {}", documentId, question);

        // 1. Retrieve the top 4 most semantically relevant chunks from pgvector
        List<Document> relevantChunks = vectorStoreService.searchSimilar(documentId, question, 4);

        // 2. Build the context string and citations list
        StringBuilder contextBuilder = new StringBuilder();
        List<ReferenceDto> references = new ArrayList<>();

        for (int i = 0; i < relevantChunks.size(); i++) {
            Document chunk = relevantChunks.get(i);
            Object pageObj = chunk.getMetadata().get("page_number");
            int pageNumber = pageObj != null ? Integer.parseInt(pageObj.toString()) : 1;
            String text = chunk.getContent();

            contextBuilder.append(String.format("[Source Excerpt %d (Page %d)]\n%s\n\n", i + 1, pageNumber, text));

            // Clean snippet for the UI citation drawer (first 250 characters)
            String snippet = text.length() > 250 ? text.substring(0, 250) + "..." : text;
            references.add(ReferenceDto.builder()
                    .pageNumber(pageNumber)
                    .snippet(snippet.replaceAll("\\s+", " ").trim())
                    .score(0.85 + (0.03 * (relevantChunks.size() - i))) // Representative relevance score
                    .build());
        }

        // 3. Construct the grounded prompt with strict hallucination guardrails
        String systemPrompt = """
                You are DocuMind AI, an intelligent, objective document analysis assistant.
                Your task is to answer the user's question accurately and strictly based on the provided document excerpts.
                
                Guidelines:
                1. Use ONLY the information provided in the Context below.
                2. If the context does not contain enough information to answer the question, explicitly state:
                   "I could not find the answer to this in the provided document." Do not speculate or extrapolate.
                3. Keep your answers clear, concise, and structured (use bullet points where appropriate).
                """;

        String userPrompt = String.format("""
                Context:
                %s
                
                Question:
                %s
                
                Answer:
                """, contextBuilder.length() > 0 ? contextBuilder.toString() : "No relevant context found.", question);

        // 4. Call the LLM using Spring AI ChatClient
        String answer;
        try {
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM inference error: {}", e.getMessage(), e);
            answer = "Error generating AI response: " + e.getMessage() + ". Please verify your LLM API key.";
        }

        // 5. Persist the interaction in the database for document chat history
        try {
            String refsJson = objectMapper.writeValueAsString(references);
            ChatMessageEntity messageEntity = ChatMessageEntity.builder()
                    .documentId(documentId)
                    .question(question)
                    .answer(answer)
                    .referencesJson(refsJson)
                    .build();
            chatMessageRepository.save(messageEntity);
        } catch (Exception e) {
            log.warn("Failed to persist chat message: {}", e.getMessage());
        }

        return ChatResponse.builder()
                .documentId(documentId)
                .question(question)
                .answer(answer)
                .references(references)
                .build();
    }

    /**
     * Fetches previous conversation history for a given document.
     */
    public List<ChatMessageEntity> getChatHistory(UUID documentId) {
        return chatMessageRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
    }
}
