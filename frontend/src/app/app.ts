import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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

  // Reactive State using Angular Signals
  readonly documents = signal<DocumentItem[]>([]);
  readonly selectedDoc = signal<DocumentItem | null>(null);
  readonly activeTab = signal<'chat' | 'summary'>('chat');

  readonly isUploading = signal(false);
  readonly isThinking = signal(false);
  readonly isSummarizing = signal(false);

  readonly uploadError = signal<string>('');
  readonly currentQuestion = signal('');
  readonly chatMessages = signal<ChatMessage[]>([]);
  readonly summaryText = signal<string>('');

  // Selected citation snippet for expandable modal/drawer
  readonly expandedRef = signal<ReferenceItem | null>(null);

  ngOnInit(): void {
    this.loadDocuments();
  }

  /** Load document history from backend */
  loadDocuments(): void {
    this.docService.getDocuments().subscribe({
      next: (docs) => this.documents.set(docs),
      error: (err) => console.error('Failed to load documents', err)
    });
  }

  /** Handle file upload via input or drag-and-drop */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.uploadFile(input.files[0]);
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      this.uploadFile(event.dataTransfer.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  uploadFile(file: File): void {
    const validExtensions = ['.pdf', '.docx', '.doc'];
    const hasValidExt = validExtensions.some(ext => file.name.toLowerCase().endsWith(ext));
    if (!hasValidExt) {
      this.uploadError.set('Unsupported file format. Please upload a PDF or Word (.docx) document.');
      return;
    }

    this.uploadError.set('');
    this.isUploading.set(true);

    this.docService.uploadDocument(file).subscribe({
      next: (newDoc) => {
        this.isUploading.set(false);
        this.loadDocuments();
        // Automatically open the uploaded document
        this.openDocument(newDoc);
      },
      error: (err) => {
        this.isUploading.set(false);
        this.uploadError.set(err.error?.message || 'Failed to process document. Please try again.');
      }
    });
  }

  /** Open document workspace (switches from Dashboard to Document View) */
  openDocument(doc: DocumentItem): void {
    this.selectedDoc.set(doc);
    this.activeTab.set('chat');
    this.chatMessages.set([]);
    this.summaryText.set(doc.summary || '');
    this.expandedRef.set(null);

    // Load past chat history from PostgreSQL
    this.docService.getChatHistory(doc.id).subscribe({
      next: (history) => {
        const mapped = history.map(item => [
          { role: 'user' as const, text: item.question },
          {
            role: 'assistant' as const,
            text: item.answer,
            references: item.referencesJson ? JSON.parse(item.referencesJson) : []
          }
        ]).flat();
        this.chatMessages.set(mapped);
      }
    });
  }

  /** Return back to the Document Library dashboard */
  backToDashboard(): void {
    this.selectedDoc.set(null);
    this.expandedRef.set(null);
    this.loadDocuments();
  }

  /** Delete document and refresh library */
  deleteDocument(id: string, event: MouseEvent): void {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this document and all its chat history?')) {
      return;
    }

    this.docService.deleteDocument(id).subscribe({
      next: () => {
        if (this.selectedDoc()?.id === id) {
          this.backToDashboard();
        }
        this.loadDocuments();
      }
    });
  }

  /** Switch between Chat and Summary views */
  setTab(tab: 'chat' | 'summary'): void {
    this.activeTab.set(tab);
    if (tab === 'summary' && !this.summaryText()) {
      this.generateSummary();
    }
  }

  /** Send natural language question to RAG engine */
  sendQuestion(): void {
    const question = this.currentQuestion().trim();
    const doc = this.selectedDoc();
    if (!question || !doc || this.isThinking()) return;

    // Append user question to chat UI immediately
    this.chatMessages.update(msgs => [...msgs, { role: 'user', text: question }]);
    this.currentQuestion.set('');
    this.isThinking.set(true);

    this.docService.askQuestion(doc.id, question).subscribe({
      next: (resp) => {
        this.isThinking.set(false);
        this.chatMessages.update(msgs => [
          ...msgs,
          { role: 'assistant', text: resp.answer, references: resp.references }
        ]);
      },
      error: (err) => {
        this.isThinking.set(false);
        this.chatMessages.update(msgs => [
          ...msgs,
          {
            role: 'assistant',
            text: 'An error occurred while answering: ' + (err.error?.message || err.message)
          }
        ]);
      }
    });
  }

  /** Trigger 1-click document summarization */
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
        this.summaryText.set('Failed to generate summary: ' + (err.error?.message || err.message));
      }
    });
  }

  /** Helper: Toggle reference citation details */
  toggleRef(ref: ReferenceItem): void {
    if (this.expandedRef() === ref) {
      this.expandedRef.set(null);
    } else {
      this.expandedRef.set(ref);
    }
  }

  /** Helper: format file size */
  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  /** Helper: format date */
  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
