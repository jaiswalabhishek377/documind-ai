import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DocumentItem {
  id: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  pageCount: number;
  status: 'PROCESSING' | 'READY' | 'FAILED';
  summary?: string;
  errorMessage?: string;
  createdAt: string;
}

export interface ReferenceItem {
  pageNumber: number;
  snippet: string;
  score: number;
}

export interface ChatRequest {
  documentId: string;
  question: string;
}

export interface ChatResponse {
  documentId: string;
  question: string;
  answer: string;
  references: ReferenceItem[];
  timestamp: string;
}

export interface SummaryResponse {
  documentId: string;
  summary: string;
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api';

  /** Fetch all uploaded documents for the history library */
  getDocuments(): Observable<DocumentItem[]> {
    return this.http.get<DocumentItem[]>(`${this.baseUrl}/documents`);
  }

  /** Upload a PDF or Word document (.docx/.doc) */
  uploadDocument(file: File): Observable<DocumentItem> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<DocumentItem>(`${this.baseUrl}/documents/upload`, formData);
  }

  /** Delete a document and its vectors from PostgreSQL */
  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/documents/${id}`);
  }

  /** Ask a natural language question about a document */
  askQuestion(documentId: string, question: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.baseUrl}/chat`, { documentId, question });
  }

  /** Retrieve previous chat history for a document */
  getChatHistory(documentId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/chat/history/${documentId}`);
  }

  /** Generate or retrieve the AI summary */
  getSummary(documentId: string): Observable<SummaryResponse> {
    return this.http.post<SummaryResponse>(`${this.baseUrl}/documents/${documentId}/summarize`, {});
  }
}
