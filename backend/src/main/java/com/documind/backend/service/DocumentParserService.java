package com.documind.backend.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for extracting text from PDF and Word documents.
 * Preserves page/section numbers to enable grounded citations in RAG answers.
 */
@Service
@Slf4j
public class DocumentParserService {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedPage {
        private int pageNumber;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedDocument {
        private int totalPages;
        private List<ParsedPage> pages;
        private String fullText;
    }

    /**
     * Extracts text from uploaded PDF or Word document.
     */
    public ParsedDocument parse(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        
        if (fileName.endsWith(".pdf")) {
            return parsePdf(file.getBytes());
        } else if (fileName.endsWith(".docx")) {
            return parseDocx(file.getInputStream());
        } else if (fileName.endsWith(".doc")) {
            return parseDoc(file.getInputStream());
        } else {
            throw new IllegalArgumentException("Unsupported file type. Please upload a .pdf or .docx document.");
        }
    }

    /**
     * Parses PDF using Apache PDFBox 3.x, extracting text page-by-page.
     */
    private ParsedDocument parsePdf(byte[] pdfBytes) throws Exception {
        List<ParsedPage> pages = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    pages.add(new ParsedPage(i, pageText));
                    fullText.append(pageText).append("\n\n");
                }
            }

            log.info("Successfully extracted {} pages from PDF", totalPages);
            return ParsedDocument.builder()
                    .totalPages(totalPages)
                    .pages(pages)
                    .fullText(fullText.toString())
                    .build();
        }
    }

    /**
     * Parses Word .docx documents using Apache POI.
     */
    private ParsedDocument parseDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument docx = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
            
            String text = extractor.getText();
            return createPagedDocumentFromText(text);
        }
    }

    /**
     * Parses legacy Word .doc documents using Apache POI HWPF.
     */
    private ParsedDocument parseDoc(InputStream inputStream) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(doc)) {
            
            String text = extractor.getText();
            return createPagedDocumentFromText(text);
        }
    }

    /**
     * Splits plain text into logical pseudo-pages (approx 500 words per page)
     * so Word documents have clear, grounded section numbers for citations.
     */
    private ParsedDocument createPagedDocumentFromText(String fullText) {
        List<ParsedPage> pages = new ArrayList<>();
        if (fullText == null || fullText.isBlank()) {
            return ParsedDocument.builder()
                    .totalPages(0)
                    .pages(pages)
                    .fullText("")
                    .build();
        }

        String[] paragraphs = fullText.split("\n{2,}");
        StringBuilder currentBlock = new StringBuilder();
        int pageNumber = 1;
        int wordCount = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            currentBlock.append(trimmed).append("\n\n");
            wordCount += trimmed.split("\\s+").length;

            // Roughly 400-500 words per logical page
            if (wordCount >= 400) {
                pages.add(new ParsedPage(pageNumber++, currentBlock.toString().trim()));
                currentBlock.setLength(0);
                wordCount = 0;
            }
        }

        if (currentBlock.length() > 0) {
            pages.add(new ParsedPage(pageNumber, currentBlock.toString().trim()));
        }

        return ParsedDocument.builder()
                .totalPages(pages.size())
                .pages(pages)
                .fullText(fullText)
                .build();
    }
}
