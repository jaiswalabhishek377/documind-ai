import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import { DocumentService, DocumentItem, ReferenceItem } from './services/document.service';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  references?: ReferenceItem[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly docService = inject(DocumentService);
  private readonly sanitizer = inject(DomSanitizer);

  // Core State
  readonly documents = signal<DocumentItem[]>([]);
  readonly selectedDoc = signal<DocumentItem | null>(null);
  readonly chatMessages = signal<ChatMessage[]>([]);
  readonly currentQuestion = signal<string>('');

  // UI States
  readonly isUploading = signal<boolean>(false);
  readonly isThinking = signal<boolean>(false);
  readonly isSummarizing = signal<boolean>(false);
  readonly isSummaryOpen = signal<boolean>(false);
  readonly uploadError = signal<string>('');
  readonly chatError = signal<string>('');
  readonly summaryText = signal<string>('');
  readonly expandedRef = signal<ReferenceItem | null>(null);

  ngOnInit(): void {
    this.loadDocuments();
  }

  /** Load all uploaded documents into the sidebar */
  loadDocuments(): void {
    this.docService.getDocuments().subscribe({
      next: (docs) => {
        this.documents.set(docs);
        // Automatically select the first document if none is selected
        if (!this.selectedDoc() && docs.length > 0) {
          this.selectDocument(docs[0]);
        }
      },
      error: (err) => console.error('Error loading documents:', err)
    });
  }

  /** Select a document from the sidebar */
  selectDocument(doc: DocumentItem): void {
    this.selectedDoc.set(doc);
    this.chatMessages.set([]);
    this.summaryText.set(doc.summary || '');
    this.isSummaryOpen.set(false);
    this.expandedRef.set(null);

    // Fetch previous chat history for this document from PostgreSQL
    this.docService.getChatHistory(doc.id).subscribe({
      next: (history) => {
        const msgs: ChatMessage[] = [];
        for (const item of history) {
          msgs.push({ role: 'user', text: item.question });
          msgs.push({
            role: 'assistant',
            text: item.answer,
            references: item.referencesJson ? JSON.parse(item.referencesJson) : []
          });
        }
        this.chatMessages.set(msgs);
      }
    });
  }

  /** Start a fresh new chat session */
  newChat(): void {
    this.chatMessages.set([]);
    this.currentQuestion.set('');
    this.isSummaryOpen.set(false);
    this.expandedRef.set(null);
  }

  /** Handle file upload */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.uploadFile(input.files[0]);
      input.value = ''; // reset input
    }
  }

  uploadFile(file: File): void {
    const validExtensions = ['.pdf', '.docx', '.doc'];
    const hasValidExt = validExtensions.some(ext => file.name.toLowerCase().endsWith(ext));
    if (!hasValidExt) {
      this.uploadError.set('Please upload a PDF (.pdf) or Word (.docx) document.');
      return;
    }

    this.uploadError.set('');
    this.isUploading.set(true);

    this.docService.uploadDocument(file).subscribe({
      next: (newDoc) => {
        this.isUploading.set(false);
        this.loadDocuments();
        this.selectDocument(newDoc);
      },
      error: (err) => {
        this.isUploading.set(false);
        this.uploadError.set(err.error?.message || 'Upload failed. Check file format or size.');
      }
    });
  }

  /** Delete a document */
  deleteDocument(id: string, event: MouseEvent): void {
    event.stopPropagation();
    if (!confirm('Delete this document and all its chat history?')) return;

    this.docService.deleteDocument(id).subscribe({
      next: () => {
        const remaining = this.documents().filter(d => d.id !== id);
        this.documents.set(remaining);
        if (this.selectedDoc()?.id === id) {
          this.selectedDoc.set(remaining.length > 0 ? remaining[0] : null);
          if (remaining.length > 0) {
            this.selectDocument(remaining[0]);
          } else {
            this.chatMessages.set([]);
          }
        }
      }
    });
  }

  /** Send natural language question to Spring AI RAG backend */
  sendQuestion(): void {
    const question = this.currentQuestion().trim();
    const doc = this.selectedDoc();
    if (!question || !doc || this.isThinking()) return;

    // Immediately push user question to UI
    this.chatMessages.update(msgs => [...msgs, { role: 'user', text: question }]);
    this.currentQuestion.set('');
    this.chatError.set('');
    this.isThinking.set(true);

    this.docService.askQuestion(doc.id, question).subscribe({
      next: (resp) => {
        this.isThinking.set(false);
        this.chatMessages.update(msgs => [
          ...msgs,
          { role: 'assistant', text: resp.answer, references: resp.references }
        ]);
      },
      error: () => {
        this.isThinking.set(false);
        this.chatError.set('Unable to get an answer right now. Please try asking again in a few moments.');
        setTimeout(() => this.chatError.set(''), 7000);
      }
    });
  }

  /** Toggle and generate AI summary */
  toggleSummary(): void {
    this.isSummaryOpen.set(!this.isSummaryOpen());
    if (this.isSummaryOpen() && !this.summaryText()) {
      this.generateSummary();
    }
  }

  generateSummary(): void {
    const doc = this.selectedDoc();
    if (!doc || this.isSummarizing()) return;

    this.isSummarizing.set(true);
    this.docService.getSummary(doc.id).subscribe({
      next: (resp) => {
        this.isSummarizing.set(false);
        this.summaryText.set(resp.summary);
      },
      error: (err) => {
        this.isSummarizing.set(false);
        this.summaryText.set('Error generating summary: ' + (err.error?.message || err.message));
      }
    });
  }

  /** Toggle Citation Inspector */
  toggleRef(ref: ReferenceItem): void {
    this.expandedRef.set(this.expandedRef() === ref ? null : ref);
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  /** Renders markdown safely into formatted HTML */
  renderMarkdown(text: string): SafeHtml {
    if (!text) return '';
    try {
      const rawHtml = marked.parse(text, { breaks: true, gfm: true }) as string;
      return this.sanitizer.bypassSecurityTrustHtml(rawHtml);
    } catch {
      return text;
    }
  }
}
