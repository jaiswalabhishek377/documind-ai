# DocuMind AI — Enterprise Document Intelligence & Conversational RAG Platform

[![Java](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![Angular](https://img.shields.io/badge/Angular-18-DD0031?style=for-the-badge&logo=angular&logoColor=white)](https://angular.dev/)
[![TailwindCSS](https://img.shields.io/badge/Tailwind_CSS-3.4-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20with%20pgvector-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![Google Gemini](https://img.shields.io/badge/Google%20Gemini-Flash%20%26%20Embeddings-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/)


## 📌 Overview

**DocuMind AI** is an enterprise-grade document intelligence and retrieval-augmented generation (RAG) platform engineered for the **Seconize Full-Stack AI Engineer Internship Assignment**.

The platform ingests complex PDF and Word documents up to 25MB, performs page-aware text extraction, indexes dense 768-dimensional semantic embeddings into **PostgreSQL with `pgvector` (HNSW indexing)**, and enables grounded conversational Q&A. Responses feature clickable, page-level citation references with mathematical match scores alongside instant 1-click executive summaries.


## ※ System Capabilities & Specifications

| Capability | Technical Scope & Specifications | Underlying Engineering |
| :--- | :--- | :--- |
| **Multi-Format Ingestion** | PDF & Word (`.docx`, `.doc`), up to 25MB file size | Apache PDFBox 3.0 & Apache POI 5.3 page-by-page parsers |
| **Dense Vector Retrieval** | 768-dim embeddings, sub-5ms cosine search | PostgreSQL 16 + `pgvector` with HNSW Index ($M=16, ef=64$) |
| **Grounded Conversational RAG** | Strict anti-hallucination prompt boundary | Gemini 3.1 Flash-Lite $\to$ Gemma 4-26B cascading router |
| **Source Provenance (Citations)** | Page-level citations with mathematical match scores | Cosine distance ($1 - d/2$) cross-weighted with lexical overlap |
| **Autonomous Summarization** | 1-click executive summary, key takeaways & action items | Dedicated prompt schema + 2-tier in-memory & PostgreSQL cache |
| **Document Lifecycle & History** | Persistent document manager, status badges & chat audit | Relational schema with UUID keys & cascading foreign keys |

---

## 🛠️ Tech Stack & Dependencies

| Layer | Technologies Used |
| :--- | :--- |
| **Frontend** | Angular 18 (Standalone Components, Signals), Tailwind CSS, `marked` |
| **Backend** | Java 21 LTS, Spring Boot 3.3.4, Spring AI 1.0.0-M3, Lombok, Jackson |
| **Document Parsers**| Apache PDFBox 3.0.3, Apache POI 5.3.0 |
| **Database & Vector Store** | PostgreSQL 16 on Supabase with `pgvector` extension (HNSW index, 768 dimensions) |
| **LLM & Embeddings** | Google Gemini (`gemini-3.1-flash-lite`, `gemma-4-26b-a4b-it`, `gemini-embedding-001`) |

---

## ⚙️ Prerequisites

* **Java 21 LTS** or higher
* **Node.js v18+** & **npm v9+**
* **Git**

---

## 🏃 Quickstart / Local Setup Guide

### 1. Clone the Repository
```bash
git clone https://github.com/jaiswalabhishek377/documind-ai.git
cd documind-ai
```

### 2. Configure Environment Variables
Copy the template file to `.env`:
```bash
cp .env.example .env
```
Update `.env` with your Google Gemini API key:
```properties
GEMINI_API_KEY=your-gemini-api-key-here
SPRING_DATASOURCE_URL=jdbc:postgresql://your-supabase-url:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres.your-ref
SPRING_DATASOURCE_PASSWORD=your-db-password
```

### 3. Run the Backend (Spring Boot)
```bash
cd backend
./mvnw.cmd spring-boot:run
```
*The Spring Boot server starts at `http://localhost:8080`.*

### 4. Run the Frontend (Angular)
In a separate terminal window:
```bash
cd frontend
npm install
npm start
```
*Open `http://localhost:4200` in your browser.*

---

## 📡 API Specification

### 1. Document Management Endpoints

| Method | Endpoint | Description | Payload |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/documents/upload` | Upload & index PDF/Word document ($\le 25\text{MB}$) | `multipart/form-data` (`file`) |
| `GET` | `/api/documents` | List all uploaded documents with status | None |
| `GET` | `/api/documents/{id}/summary` | Generate or retrieve executive AI summary | None |
| `DELETE` | `/api/documents/{id}` | Delete document and cascade-purge vectors | None |

### 2. Conversational RAG Endpoints

| Method | Endpoint | Description | Payload |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/chat` | Natural language grounded Q&A with citations | `{"documentId": "UUID", "question": "..."}` |
| `GET` | `/api/chat/history/{documentId}` | Retrieve previous Q&A conversation thread | None |

#### Sample Request & Grounded Citation Response:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"documentId": "c4b12345-6789-abcd-ef01-23456789abcd", "question": "What are the core technical achievements?"}'
```

```json
{
  "documentId": "c4b12345-6789-abcd-ef01-23456789abcd",
  "question": "What are the core technical achievements?",
  "answer": "Based on the document, the core achievements include:\n* **Competitive Programming:** Attained a 1761+ LeetCode rating (top 9% globally).\n* **Project Engineering:** Architected a multi-tenant RAG system featuring dual-threshold retrieval.",
  "references": [
    {
      "pageNumber": 1,
      "snippet": "LeetCode Performance: He attained a 1761+ rating, placing him in the top 9% globally with over 800 algorithmic problems solved...",
      "score": 0.94
    },
    {
      "pageNumber": 1,
      "snippet": "Advanced Engineering: It includes a dual-threshold RAG pipeline with a Gemini fallback chain to prevent AI failure...",
      "score": 0.82
    }
  ]
}
```

---

## 📄 Complete Technical Design Document
For an in-depth breakdown of the mathematical models, database schemas, prompt guardrails, and architectural trade-off evaluations, consult the **[Technical Design Document (DESIGN_DOCUMENT.md)](DESIGN_DOCUMENT.md)**.

---
❤️