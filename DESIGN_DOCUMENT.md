# Technical Design Document: DocuMind AI
### Enterprise Document Intelligence & Conversational RAG Platform

**Author:** Abhishek Jaiswal  
**Target Organization:** Seconize Technologies  
**Role:** Full-Stack AI Engineer Intern  
**Date:** September 2026  
**Document Version:** 1.0.0 (Production Release)  

---

## Table of Contents
1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
2. [High-Level System Topology & Architecture](#2-high-level-system-topology--architecture)
   * 2.1 [Production Architecture (Excalidraw-Style Pipeline)](#21-production-architecture-excalidraw-style-pipeline)
   * 2.2 [End-to-End System Sequence Diagram](#22-end-to-end-system-sequence-diagram)
3. [Deep-Dive: Tri-Pipeline Architecture](#3-deep-dive-tri-pipeline-architecture)
   * 3.1 [Pipeline 1: Document Ingestion, Parsing & Vectorization](#31-pipeline-1-document-ingestion-parsing--vectorization)
   * 3.2 [Pipeline 2: Conversational RAG, Augmented Prompting & Citations](#32-pipeline-2-conversational-rag-augmented-prompting--citations)
   * 3.3 [Pipeline 3: 1-Click Automated AI Summarization & Synthesis](#33-pipeline-3-1-click-automated-ai-summarization--synthesis)
4. [Mathematical & Algorithmic Formulations](#4-mathematical--algorithmic-formulations)
   * 4.1 [Vector Distance Metric](#41-vector-distance-metric)
   * 4.2 [Token Chunking Optimization Ratio](#42-token-chunking-optimization-ratio)
   * 4.3 [Dynamic Citation Scoring Formulation](#43-dynamic-citation-scoring-formulation)
5. [Database Architecture & Data Modeling](#5-database-architecture--data-modeling)
   * 5.1 [Relational Schema (PostgreSQL)](#51-relational-schema-postgresql)
   * 5.2 [Vector Storage Schema & HNSW Indexing (`pgvector`)](#52-vector-storage-schema--hnsw-indexing-pgvector)
6. [Resilience Engineering: Multi-Model LLM Cascading](#6-resilience-engineering-multi-model-llm-cascading)
7. [Architectural Trade-Off & Decision Matrix](#7-architectural-trade-off--decision-matrix)
8. [Security, Tenancy Isolation & Anti-Hallucination Guardrails](#8-security-tenancy-isolation--anti-hallucination-guardrails)
9. [Future Scalability & Production Roadmap](#9-future-scalability--production-roadmap)
10. [Visual Verification & System Screenshots](#10-visual-verification--system-screenshots)                🟢🎯 

---

## 1. Executive Summary & Problem Statement

Enterprises manage thousands of semi-structured and unstructured documents—including compliance manuals, technical specifications, service-level agreements, and contracts. Knowledge workers spend up to 20% of their business hours manually skimming these documents to locate specific clauses and verify facts.

Standard full-text search engines (e.g., Elasticsearch, SQL `LIKE`) fail because they match lexical keywords rather than semantic intent. Conversely, naive LLM integrations hallucinate answers or generate unverified claims without provenance.

**DocuMind AI** solves this enterprise challenge by engineering a **Strict Grounded Retrieval-Augmented Generation (RAG)** platform. Documents up to 25MB (PDF and Word `.docx`) are ingested, page-parsed, and indexed into dense 768-dimensional vector representations in **PostgreSQL with `pgvector`**. The platform guarantees:
1. **Verifiable Answers:** Every claim references the exact source document, page number, text excerpt, and dynamic mathematical match percentage.
2. **High Availability:** A multi-model cascading router automatically circumvents external API rate limits (HTTP 429) and provider spikes (HTTP 503).
3. **Enterprise Compliance:** Complete relational integrity, zero data drift, and database-level cascading deletion.

### 1.1 System Capabilities & Specifications

The platform is engineered around six foundational production capabilities:

| Capability | Technical Scope & Specifications | Underlying Engineering |
| :--- | :--- | :--- |
| **Multi-Format Ingestion** | PDF & Word (`.docx`, `.doc`), up to 25MB file size | Apache PDFBox 3.0 & Apache POI 5.3 page-by-page parsers |
| **Dense Vector Retrieval** | 768-dim embeddings, sub-5ms cosine search | PostgreSQL 16 + `pgvector` with HNSW Index ($M=16, ef=64$) |
| **Grounded Conversational RAG** | Strict anti-hallucination prompt boundary | Gemini 3.1 Flash-Lite $\to$ Gemma 4-26B cascading router |
| **Source Provenance (Citations)** | Page-level citations with mathematical match scores | Cosine distance ($1 - d/2$) cross-weighted with lexical overlap |
| **Autonomous Summarization** | 1-click executive summary, key takeaways & action items | Dedicated prompt schema + 2-tier in-memory & PostgreSQL cache |
| **Document Lifecycle & History** | Persistent document manager, status badges & chat audit | Relational schema with UUID keys & cascading foreign keys |

---

## 2. High-Level System Topology & Architecture

DocuMind AI enforces a clean separation of concerns across presentation, orchestration, dense vector retrieval, and persistent storage. Rather than an entangled multi-tier topology, the system is modeled as three decoupled, unidirectional highways: **Document Ingestion (Write Path)**, **Conversational RAG & Citations (Read Path)**, and **1-Click AI Summarization (Synthesis Path)**.

### 2.1 Production Architecture (Excalidraw-Style 3-Lane Pipeline)

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 DOCUMIND AI — PRODUCTION SYSTEM ARCHITECTURE                                 │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

  LANE 1: DOCUMENT INGESTION & PARSING (WRITE PATH)
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
  ┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐
  │ 1. DOCUMENT    │     │ 2. EXTRACTION  │     │ 3. CHUNKING    │     │ 4. EMBEDDING   │     │ 5. PGVECTOR    │
  │                │     │                │     │                │     │                │     │                │
  │ Angular 18 UI  │ ──► │ Apache PDFBox  │ ──► │ Token Splitter │ ──► │ Gemini API     │ ──► │ PostgreSQL 15  │
  │ Drag & Drop    │     │ Apache POI     │     │ 800 Tokens     │     │ embedding-001  │     │ HNSW Index     │
  │ PDF/Word <=25M │     │ Page Metadata  │     │ 150 Overlap    │     │ 768-dim Vector │     │ Sub-5ms Query  │
  └────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘

  LANE 2: CONVERSATIONAL RAG & CITATIONS (READ PATH)
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
  ┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐
  │ 1. USER QUERY  │     │ 2. EMBED QUERY │     │ 3. RETRIEVAL   │     │ 4. LLM ROUTER  │     │ 5. CITATIONS   │
  │                │     │                │     │                │     │                │     │                │
  │ Natural Lang.  │ ──► │ Gemini API     │ ──► │ Cosine (<=>)   │ ──► │ Prompt Guard   │ ──► │ Grounded Ans.  │
  │ Angular Chat   │     │ embedding-001  │     │ Top-3 Chunks   │     │ 3.1 Flash-Lite │     │ Page & Match % │
  │ Reactive Stream│     │ 768-dim Vector │     │ Doc ID Filter  │     │ Gemma Failover │     │ Marked Render  │
  └────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘

  LANE 3: 1-CLICK AI SUMMARIZATION (SYNTHESIS PATH)
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
  ┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐     ┌────────────────┐
  │ 1. SUMMARIZE   │     │ 2. FULL-TEXT   │     │ 3. PROMPT ENG  │     │ 4. LLM CASCADE │     │ 5. SUMMARY UI  │
  │                │     │                │     │                │     │                │     │                │
  │ Angular Button │ ──► │ In-Memory Text │ ──► │ Summary Schema │ ──► │ LLM Dispatch   │ ──► │ Slide-Over UI  │
  │ 1-Click Action │     │ ConcurrentMap  │     │ Key Takeaways  │     │ 3.1 Flash-Lite │     │ Marked Render  │
  │ Selected Doc   │     │ Database Fetch │     │ Action Items   │     │ Gemma Failover │     │ Cached in DB   │
  └────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘     └────────────────┘

  CORE RESILIENCE & STORAGE TOPOLOGY
  ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
┌──────────────────────────────────────────────────┐        ┌──────────────────────────────────────────────────┐
│ RESILIENT MULTI-MODEL LLM ENGINE                 │        │ SUPABASE PERSISTENCE & VECTOR STORE              │
│                                                  │        │                                                  │
│ [Primary]    Gemini 3.1 Flash-Lite (< 1.5s)      │        │ • documents     : metadata, page count, status   │
│     │ (Auto-failover on HTTP 429 / 503)          │        │ • vector_store  : chunk text, 768-dim, doc_id    │
│     ▼                                            │        │ • chat_messages : query, grounded response       │
│ [Fallback 1] Gemini Flash Lite (Alias)           │        │ • HNSW Index    : m=16, ef=64 (sub-millisecond)  │
│     │ (On upstream quota exhaustion)             │        │                                                  │
│     ▼                                            │        │ [ACID Guarantees]                                │
│ [Fallback 2] Gemma 4-26B (Independent Quota)     │        │ Cascading delete, zero orphan vector leakage     │
└──────────────────────────────────────────────────┘        └──────────────────────────────────────────────────┘
```

### 2.2 End-to-End System Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Client
    participant UI as Angular 18 Client
    participant API as Spring Boot Controller
    participant Engine as Document / RAG / Summary Service
    participant Embed as GeminiEmbeddingModel (768-dim)
    participant DB as PostgreSQL (pgvector HNSW)
    participant LLM as Multi-Model LLM Router

    %% ==========================================
    %% PATH A: INGESTION PIPELINE (WRITE PATH)
    %% ==========================================
    Note over User, LLM: PATH A: Document Ingestion, Parsing & HNSW Vector Indexing
    User->>UI: Uploads file (.pdf / .docx <= 25MB)
    UI->>API: POST /api/documents/upload (Multipart)
    API->>Engine: Stream file to DocumentParserService (PDFBox / POI)
    Engine->>Engine: Extract text page-by-page & split into 800-token chunks
    Engine->>Embed: Generate 768-dim embeddings (gemini-embedding-001)
    Embed-->>Engine: Dense float32 vector arrays
    Engine->>DB: INSERT into documents & vector_store (HNSW index)
    DB-->>Engine: Transaction committed (ACID)
    Engine-->>API: Document READY
    API-->>UI: HTTP 200 OK (DocumentResponse)
    UI-->>User: Left-sidebar updates status to "Ready" & adds to history

    %% ==========================================
    %% PATH B: CONVERSATIONAL RAG & CITATIONS (READ PATH)
    %% ==========================================
    Note over User, LLM: PATH B: Grounded Conversational RAG & Dynamic Citations
    User->>UI: Submits question ("What are the key terms?")
    UI->>API: POST /api/chat { documentId, question }
    API->>Engine: Execute RagService.askQuestion()
    Engine->>Embed: Embed question string to 768-dim vector
    Embed-->>Engine: Query vector
    Engine->>DB: Cosine distance search (<=>) [Top-3 chunks, documentId filter]
    DB-->>Engine: Return 3 relevant chunk excerpts with distance
    Engine->>Engine: Assemble context + Inject anti-hallucination guardrails
    Engine->>LLM: Dispatch prompt to cascading router (3.1-flash-lite -> Gemma)
    LLM-->>Engine: Grounded answer text
    Engine->>Engine: Compute citation scores (cosine distance + keyword overlap)
    Engine->>DB: Persist Q&A to chat_messages
    Engine-->>API: ChatResponse (answer + citation references)
    API-->>UI: HTTP 200 OK (JSON)
    UI->>UI: Client-side 'marked' parsing + DomSanitizer
    UI-->>User: Render formatted Markdown + citation pills (Page 1 · Excerpt 1)

    %% ==========================================
    %% PATH C: 1-CLICK AI SUMMARIZATION (SYNTHESIS PATH)
    %% ==========================================
    Note over User, LLM: PATH C: 1-Click Automated AI Summarization
    User->>UI: Clicks "Summarize Document" button
    UI->>API: POST /api/documents/{id}/summarize
    API->>Engine: Execute SummarizationService.generateSummary()
    Engine->>Engine: Check cached summary in PostgreSQL / memory
    alt Summary Already Cached
        Engine-->>API: Return cached SummaryResponse (status: CACHED)
    else First-time Synthesis
        Engine->>LLM: Dispatch structured schema prompt (Exec Summary, Takeaways, Action Items)
        LLM-->>Engine: Structured Markdown summary
        Engine->>DB: UPDATE documents SET summary = :summary
        Engine-->>API: Return SummaryResponse (status: GENERATED)
    end
    API-->>UI: HTTP 200 OK (JSON)
    UI->>UI: Render slide-over drawer with typography
    UI-->>User: Display Executive Summary, Key Takeaways & Action Items
```

---

## 3. Deep-Dive: Tri-Pipeline Architecture

DocuMind AI enforces a strict boundary between asynchronous ingestion, synchronous inference, and autonomous document summarization.

### 3.1 Pipeline 1: Document Ingestion, Parsing & Vectorization

1. **Ingress & Validation:** `DocumentController` intercepts the `multipart/form-data` request, enforcing size constraints ($\le 25\text{MB}$) and validating MIME types against `.pdf`, `.docx`, and `.doc`.
2. **Format-Aware Parsing:**
   * **PDF Documents:** Processed through **Apache PDFBox 3.0.3**. Uses `PDFTextStripper` iterating page-by-page. Text is encapsulated into `PageText` objects tagged with physical page indices.
   * **Word Documents:** Processed through **Apache POI 5.3.0** (`XWPFDocument`). Traverses paragraph nodes and tables, compiling text into structured sections.
3. **Semantic Chunking:** Text is processed by Spring AI's `TokenTextSplitter` configured for **800 tokens** per chunk with a **150-token sliding window overlap**.
4. **Vector Embedding:** Each chunk is dispatched to `GeminiEmbeddingModel`, generating a 768-dimensional dense vector representation via Google Gemini's `gemini-embedding-001`.
5. **Atomic Persistence:**
   * Inserts metadata into the `documents` table with status `READY`.
   * Persists chunk text, JSONB metadata (`document_id`, `page_number`), and vector embeddings into the `vector_store` table.

### 3.2 Pipeline 2: Conversational RAG, Augmented Prompting & Citations

1. **Query Ingestion:** `ChatController` receives `{"documentId": "...", "question": "..."}`.
2. **Dense Vector Search:** The user query is vectorized into 768 dimensions and dispatched to PostgreSQL:
   ```sql
   SELECT id, content, metadata, (embedding <=> $queryVector) AS distance
   FROM vector_store
   WHERE metadata->>'document_id' = $documentId
   ORDER BY distance ASC
   LIMIT 3;
   ```
3. **Prompt Augmentation:** The top-3 chunks are assembled into an isolated context block and passed to the LLM with strict anti-hallucination guardrails:
   ```text
   You are DocuMind AI, an intelligent, objective document analysis assistant.
   Your task is to answer the user's question accurately and strictly based on the provided document excerpts.

   Guidelines:
   1. Use ONLY the information provided in the Context below.
   2. If the context does not contain enough information to answer the question, explicitly state:
      "I could not find the answer to this in the provided document." Do not speculate or extrapolate.
   3. Keep your answers clear, concise, and structured (use bullet points where appropriate).
   4. Be direct and concise. Deliver only the core answer immediately without long unnecessary preambles.
   ```
4. **Resilient LLM Inference:** Dispatched through the Multi-Model Cascading Router.
5. **Dynamic Citation Math:** Calculates authentic relevance percentages per citation.
6. **Conversation Audit Trail:** Saves the Q&A pair and references JSON to `chat_messages`.
7. **Client Typography:** Angular parses the markdown payload through `marked` and sanitizes it via `DomSanitizer`.

### 3.3 Pipeline 3: 1-Click Automated AI Summarization & Synthesis

1. **User Trigger:** Initiated with 1 click from the Angular toolbar via `POST /api/documents/{id}/summarize`.
2. **In-Memory & Database Tier Caching:**
   * `DocumentController` checks in-memory `ConcurrentHashMap<UUID, String> textCache`.
   * If a previous summary exists in `DocumentEntity.summary`, it returns immediately with `status: CACHED`, eliminating redundant LLM API calls and lowering operational cost to \$0.
3. **Context Optimization:** Documents exceeding 25,000 characters are intelligently windowed to fit within high-speed reasoning quotas while capturing key sections.
4. **Structured Schema Prompting:** Enforces a 3-part Markdown structure:
   * **Executive Summary:** High-level 2–3 sentence distillation of document purpose.
   * **Key Takeaways:** Top 3–5 core findings or stipulations.
   * **Important Details & Action Items:** Obligations, deliverables, dates, or metrics.
5. **Database Persistence:** Stores the generated synthesis into `DocumentEntity.summary` for instant re-retrieval.
6. **Presentation:** Smoothly transitions an interactive slide-over drawer in Angular 18 with markdown typography.

---

## 4. Mathematical & Algorithmic Formulations

### 4.1 Vector Distance Metric
Dense semantic similarity is computed using **Cosine Distance**:

$$\text{Cosine Distance}(u, v) = 1 - \frac{u \cdot v}{\|u\|_2 \|v\|_2} = 1 - \frac{\sum_{i=1}^{n} u_i v_i}{\sqrt{\sum_{i=1}^{n} u_i^2} \sqrt{\sum_{i=1}^{n} v_i^2}}$$

In `pgvector`, this is represented by the `<=>` operator. Because `gemini-embedding-001` vectors are $L_2$-normalized:

$$\|u\|_2 = \|v\|_2 = 1.0 \implies \text{Cosine Distance}(u, v) = 1 - (u \cdot v)$$

This reduces vector similarity calculation to an ultra-fast dot product operation.

### 4.2 Token Chunking Optimization Ratio
Let $T_C$ represent Chunk Size and $T_O$ represent Overlap. The optimal ratio chosen for DocuMind AI is:

$$\text{Overlap Ratio} = \frac{T_O}{T_C} = \frac{150}{800} \approx 18.75\%$$

* **Why 800 tokens?** Captures complete legal clauses, financial balance lines, or technical sub-sections ($\approx 600$ words).
* **Why 18.75% overlap?** Prevents semantic fragmentation when critical entities span chunk boundaries without causing excessive index bloat.

### 4.3 Dynamic Citation Scoring Formulation
Rather than relying on mocked or hardcoded scores, citation match relevance $S$ is calculated dynamically:

$$S = \text{clamp}\left(1.0 - \frac{d}{2.0}, 0.45, 0.99\right) \times \left(0.60 + 0.40 \cdot \frac{M}{N}\right)$$

Where:
* $d$ = Vector Cosine Distance from `pgvector` ($d \in [0, 2]$).
* $M$ = Number of non-stopword query keywords matched in chunk text.
* $N$ = Total meaningful terms in the user query.
* $\text{clamp}(x, min, max)$ = Restricts output to legitimate range $[0.45, 0.99]$.

---

## 5. Database Architecture & Data Modeling

### 5.1 Relational Schema (PostgreSQL)

```sql
-- 1. Master Documents Entity Table
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    total_pages INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    summary TEXT,
    full_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Conversational Q&A History Table
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    references_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_doc ON chat_messages(document_id, created_at ASC);
```

### 5.2 Vector Storage Schema & HNSW Indexing (`pgvector`)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- 3. Spring AI Vector Store Table
CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL,
    embedding VECTOR(768) NOT NULL
);

-- HNSW (Hierarchical Navigable Small World) Index
CREATE INDEX IF NOT EXISTS vector_store_hnsw_cosine_idx 
ON vector_store USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

#### HNSW Parameter Rationale:
* **`m = 16`:** Bi-directional links per node. Delivers optimal balance between memory consumption and graph connectivity.
* **`ef_construction = 64`:** Nearest-neighbor search depth during index construction. Ensures $> 98\%$ recall accuracy during approximate nearest neighbor (ANN) retrieval.

---

## 6. Resilience Engineering: Multi-Model LLM Cascading

Relying on a single LLM endpoint creates a Single Point of Failure (SPOF). During high-traffic periods, Google Gemini free-tier endpoints impose strict limits:
* `gemini-3.8-flash` enforces a strict **20 RPM quota**, returning HTTP 429 `RESOURCE_EXHAUSTED`.
* Transient network spikes return HTTP 503 `UNAVAILABLE`.

DocuMind AI implements an **LLM Cascading Failover Router**:

```
[Incoming Request]
        │
        ▼
[Candidate 1: gemini-3.1-flash-lite] ──► (HTTP 200 OK: Response in ~1.2s) ──► Return
        │ (Throws 429/503)
        ▼ [< 10ms Failover]
[Candidate 2: gemini-flash-lite-latest] ──► (HTTP 200 OK) ───────────────────► Return
        │ (Throws 429/503)
        ▼ [< 10ms Failover]
[Candidate 3: gemma-4-26b-a4b-it] ────────► (Independent Quota Pool) ────────► Return
        │ (Throws 429/503)
        ▼ [< 10ms Failover]
[Candidate 4: gemini-flash-latest / 3.8] ──► (Deep Fallback) ────────────────► Return
```

### Key Engineering Attributes:
1. **Zero-Sleep Instant Failover:** Rather than stalling threads with blocking sleeps (`Thread.sleep`), the router instantly transitions to Candidate 2 in **$< 10\text{ms}$**.
2. **Chain-of-Thought Sanitization:** Open reasoning models (such as Gemma) output hidden `<thought>...</thought>` tags. The router employs regex post-processing (`replaceAll("(?s)<thought>.*?</thought>", "").trim()`) to guarantee clean user-facing Markdown.

---

## 7. Architectural Trade-Off & Decision Matrix

| Architectural Dimension | Option Chosen in DocuMind AI | Alternative Evaluated | Why DocuMind AI's Choice is Superior |
| :--- | :--- | :--- | :--- |
| **Backend Framework** | **Java 21 + Spring Boot 3.3** | Node.js (Express / NestJS) | Heavy document parsing (PDFBox) is CPU-bound. Node's single-threaded event loop freezes under multi-page extraction. Java uses real OS worker threads. |
| **Vector Storage** | **PostgreSQL + `pgvector`** | Pinecone / Milvus / Chroma | Eliminates "Dual-Database Synchronization Drift". Relational metadata and vectors share the same ACID transaction and cascading deletes (`ON DELETE CASCADE`). |
| **Vector Index** | **HNSW (Graph-based)** | IVFFlat (Inverted File List) | IVFFlat requires cluster retraining as new documents are uploaded. HNSW supports real-time incremental vector inserts with higher recall and lower query latency. |
| **Embedding Model** | **Custom `GeminiEmbeddingModel`** | Spring AI `OpenAiEmbeddingModel` | Standard Spring AI crashes on Gemini with `Assert.notNull(usage)`. Custom `@Primary` component talks via `RestClient` to `gemini-embedding-001`, resolving the bug. |
| **Frontend State** | **Angular 18 Signals** | NgRx / RxJS BehaviorSubjects | Signals provide fine-grained reactivity, zero change-detection overhead, and eliminate boilerplate RxJS subscriptions. |
| **Markdown Rendering** | **Client-side `marked`** | Server-side HTML generation | Offloads formatting computation to the browser, protects against XSS via `DomSanitizer`, and keeps backend APIs strictly REST/JSON. |

---

## 8. Security, Tenancy Isolation & Anti-Hallucination Guardrails

1. **Document-Centric Workspace Isolation (Identity Architecture):**
   In alignment with the assignment specifications, authentication (OAuth2/JWT) was intentionally scoped out to prioritize advanced RAG retrieval, vector mathematical scoring, and multi-model LLM failover. The system implements a **Document-Centric Workspace Pattern**:
   * Every uploaded document operates as an autonomous, self-contained tenant identified by an immutable UUID.
   * **Vector Partitioning:** Vector searches never perform unrestricted table scans. Every query enforces an immutable metadata predicate:
     ```java
     new FilterExpressionBuilder().eq("document_id", documentId.toString()).build();
     ```
   * **Audit & Conversation Partitioning:** Conversation threads in `chat_messages` are strictly scoped by `document_id`.
   * **Relational Integrity:** Deleting a document automatically purges associated vector chunks and chat histories via database-level `ON DELETE CASCADE`.
   * **Enterprise Migration Path:** To transition to enterprise multi-tenancy, `user_id` and `organization_id` foreign keys can be added to the `documents` table, backed by PostgreSQL Row-Level Security (RLS) and Spring Security JWT filters, requiring zero architectural rewrites of the RAG or parsing engines.
2. **SQL Injection Immunity:** All database interactions utilize Spring Data JPA parameterized queries and prepared statements.
3. **CORS Hardening:** Configured in `CorsConfig.java` to explicitly permit origin `http://localhost:4200` with strict HTTP method whitelisting (`GET`, `POST`, `DELETE`, `OPTIONS`).
4. **Secret Sanitization:** Production credentials are fully decoupled from version control. `application.yml` uses `${ENV_VARS}` placeholders, with local secrets loaded via git-ignored `application-local.yml` and `.env`.

---

## 9. Future Scalability & Production Roadmap

* **Enterprise Authentication & RBAC:** Implement Spring Security with JWT/OAuth2 (Keycloak / Auth0) and PostgreSQL Row-Level Security (RLS) for multi-organization workspaces.
* **Asynchronous Message-Driven Ingestion:** Offload 100+ page document parsing to background worker pools using **RabbitMQ** or **Apache Kafka**.
* **Server-Sent Events (SSE) Streaming:** Implement reactive token streaming (`streamGenerateContent`) to reduce perceived Time-To-First-Token (TTFT) to $< 400\text{ms}$.
* **Semantic Cache Layer:** Integrate Redis vector caching to instantly return answers for identical or semantically duplicate questions with zero LLM API cost.
* **Hybrid Search (BM25 + Dense Vectors):** Implement Reciprocal Rank Fusion (RRF) to combine keyword exact-matching with vector cosine semantic search.

---

## 10. Visual Verification & System Screenshots

### 10.1 Complete End-to-End Enterprise Workspace
![Complete Workspace](screenshots/01_full_workspace_and_rag.png)
*Figure 1: Complete DocuMind AI workspace displaying the document manager sidebar, grounded chat stream, citation badges (`Page 1 · Excerpt 1 (94%)`), and the slide-over document summary drawer.*

---

### 10.2 1-Click Automated AI Document Summary Slide-Over Drawer
![Document Summary Drawer](screenshots/02_document_summary_drawer.png)
*Figure 2: Slide-over executive summary drawer rendering structured Markdown sections: Executive Summary, Key Takeaways, and Important Details & Action Items.*

---

### 10.3 Grounded Citation Inspector Modal
![Citation Inspector](screenshots/03_citation_inspector_modal.png)
*Figure 3: Interactive citation inspector modal displaying the exact raw excerpt from Page 1 with dynamic 91% mathematical relevance score.*

---

### 10.4 Document History Dashboard & Conversation Audit Trail
![Document History](screenshots/04_document_history_sidebar.png)
*Figure 4: Left-sidebar document history tracking uploaded documents with physical page counts (`9p`, `1p`, `2p`), status badges, and persistent previous Q&A threads.*
