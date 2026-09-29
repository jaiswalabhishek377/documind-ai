package com.documind.backend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom Gemini Embedding Model for Spring AI.
 * Directly integrates with Google Gemini's embedding endpoint while eliminating
 * the "OpenAI Usage must not be null" exception caused by missing token usage headers.
 */
@Component
@Primary
@Slf4j
public class GeminiEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;

    public GeminiEmbeddingModel(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>();

        for (int i = 0; i < instructions.size(); i++) {
            float[] vector = fetchEmbedding(instructions.get(i));
            embeddings.add(new Embedding(vector, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return fetchEmbedding(document.getContent());
    }

    @Override
    public float[] embed(String text) {
        return fetchEmbedding(text);
    }

    @Override
    public int dimensions() {
        return 768;
    }

    private float[] fetchEmbedding(String text) {
        try {
            Map<String, Object> body = Map.of(
                    "input", text,
                    "model", "gemini-embedding-001",
                    "dimensions", 768
            );

            OpenAiEmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                List<Double> doubleList = response.getData().get(0).getEmbedding();
                float[] floatArray = new float[doubleList.size()];
                for (int i = 0; i < doubleList.size(); i++) {
                    floatArray[i] = doubleList.get(i).floatValue();
                }
                return floatArray;
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding from Gemini: {}", e.getMessage(), e);
        }
        return new float[768];
    }

    @Data
    public static class OpenAiEmbeddingResponse {
        private List<EmbeddingData> data;
    }

    @Data
    public static class EmbeddingData {
        private List<Double> embedding;
        private int index;
    }
}
