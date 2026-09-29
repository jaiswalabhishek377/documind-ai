package com.documind.backend.service;

import com.documind.backend.dto.SummaryResponse;
import com.documind.backend.model.DocumentEntity;
import com.documind.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service that handles 1-click automated AI document summarization.
 * Generates an executive summary, key takeaways, and action items.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SummarizationService {

    private final DocumentRepository documentRepository;
    private final ChatModel chatModel;

    /**
     * Generates or retrieves an AI summary for the given document.
     */
    public SummaryResponse generateSummary(UUID documentId, String documentText) {
        DocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        // Return cached summary if already generated
        if (entity.getSummary() != null && !entity.getSummary().isBlank()) {
            return SummaryResponse.builder()
                    .documentId(documentId)
                    .summary(entity.getSummary())
                    .status("CACHED")
                    .build();
        }

        // Limit text length to prevent context window overflow (first ~6000 words / 25k chars)
        String sampleText = documentText;
        if (sampleText.length() > 25000) {
            sampleText = sampleText.substring(0, 25000) + "\n... [Remaining content truncated for summary]";
        }

        String prompt = String.format("""
                You are an expert document summarizer. Analyze the following document text and provide a structured, professional summary in Markdown format.
                
                Document Text:
                %s
                
                Format your response using the following structure:
                ### Executive Summary
                (A concise 2-3 sentence overview of the document's core purpose and thesis)
                
                ### Key Takeaways
                * (Core finding / key point 1)
                * (Core finding / key point 2)
                * (Core finding / key point 3)
                
                ### Important Details & Action Items
                * (Notable dates, obligations, parties, or metrics mentioned)
                """, sampleText);

        String summary = null;
        List<String> candidateModels = List.of(
                "gemini-3.1-flash-lite",
                "gemini-flash-lite-latest",
                "gemini-3.5-flash-lite",
                "gemini-3.7-flash",
                "gemma-4-26b-a4b-it",
                "gemini-flash-latest",
                "gemini-3.8-flash"
        );

        for (String modelName : candidateModels) {
            long startTime = System.currentTimeMillis();
            try {
                log.info("🤖 [Summary] Calling model '{}' for document {}...", modelName, documentId);
                ChatClient chatClient = ChatClient.builder(chatModel).build();
                String response = chatClient.prompt()
                        .user(prompt)
                        .options(org.springframework.ai.openai.OpenAiChatOptions.builder().withModel(modelName).build())
                        .call()
                        .content();

                if (response != null && !response.isBlank()) {
                    long duration = System.currentTimeMillis() - startTime;
                    summary = response.replaceAll("(?s)<thought>.*?</thought>", "").trim();
                    log.info("✅ [Summary Success] Model '{}' completed in {} ms!", modelName, duration);
                    entity.setSummary(summary);
                    documentRepository.save(entity);
                    break;
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.warn("⚠️ [Summary] Model '{}' failed in {} ms ({}). Trying next candidate...", modelName, duration, e.getMessage());
            }
        }

        if (summary == null || summary.isBlank()) {
            summary = "Summary generation temporarily throttled. Please try again in a few moments.";
        }

        return SummaryResponse.builder()
                .documentId(documentId)
                .summary(summary)
                .status("GENERATED")
                .build();
    }
}
