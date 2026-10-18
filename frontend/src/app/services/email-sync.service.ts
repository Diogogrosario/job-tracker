import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { apiErrorMessage } from './application.service';
import { EmailProcessingStatus, EmailSyncResponse, EmailSyncResult, MailboxStatus } from '../models/email-sync';

type EmailSyncState =
  | 'checking'
  | 'notConnected'
  | 'needsReconnect'
  | 'idle'
  | 'syncing'
  | 'error';

const GMAIL_SYNC_INTERVAL_MS = 2 * 60 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class EmailSyncService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private started = false;
  private cycleInProgress = false;
  private syncPromise: Promise<void> | null = null;
  private intervalId: ReturnType<typeof setInterval> | null = null;
  private readonly onVisibilityChange = (): void => {
    if (document.visibilityState === 'visible' && this.isLastSyncStale()) {
      void this.checkStatusAndSync();
    }
  };

  readonly state = signal<EmailSyncState>('checking');
  readonly email = signal<string | null>(null);
  readonly lastSyncAt = signal<string | null>(null);
  readonly lastResult = signal<EmailSyncResult | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly syncRevision = signal(0);

  constructor() {
    this.destroyRef.onDestroy(() => this.cleanup());
  }

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    void this.checkStatusAndSync();
    this.intervalId = setInterval(() => {
      void this.checkStatusAndSync();
    }, GMAIL_SYNC_INTERVAL_MS);
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', this.onVisibilityChange);
    }
  }

  syncNow(): void {
    void this.sync(true);
  }

  processStatus() {
    return this.http.get<EmailProcessingStatus>('/api/email/process/status');
  }

  resultTooltip(): string {
    const result = this.lastResult();
    if (!result) {
      return 'No Gmail sync result is available.';
    }
    return `${result.newMessages} new, ${result.skipped} already known, ${result.errors} errors`;
  }

  private async checkStatusAndSync(): Promise<void> {
    if (this.cycleInProgress || this.syncPromise) {
      return;
    }
    this.cycleInProgress = true;
    this.state.set('checking');
    this.errorMessage.set(null);
    try {
      const status = await firstValueFrom(this.http.get<MailboxStatus>('/api/mailbox/status'));
      this.email.set(status.email);
      this.lastSyncAt.set(status.lastSyncAt);
      if (status.needsReconnect) {
        this.state.set('needsReconnect');
        return;
      }
      if (!status.connected) {
        this.state.set('notConnected');
        return;
      }
      await this.sync(false);
    } catch (error) {
      this.setRequestError(error);
    } finally {
      this.cycleInProgress = false;
    }
  }

  private async sync(force: boolean): Promise<void> {
    if (this.syncPromise) {
      await this.syncPromise;
      return;
    }
    this.state.set('syncing');
    this.errorMessage.set(null);
    const operation = this.performSync(force);
    this.syncPromise = operation;
    try {
      await operation;
    } finally {
      if (this.syncPromise === operation) {
        this.syncPromise = null;
      }
    }
  }

  private async performSync(force: boolean): Promise<void> {
    try {
      const result = await firstValueFrom(this.http.post<EmailSyncResponse>(
        '/api/email/sync',
        null,
        { params: { force: String(force) } },
      ));
      this.lastResult.set({
        fetched: result.fetched,
        newMessages: result.newMessages,
        skipped: result.skipped,
        errors: result.errors,
      });
      if (!result.throttled && result.errors === 0) {
        this.lastSyncAt.set(new Date().toISOString());
      }
      if (result.errors > 0) {
        this.state.set('error');
        this.errorMessage.set('Some Gmail messages could not be processed.');
      } else {
        this.state.set('idle');
      }
    } catch (error) {
      this.setRequestError(error);
    } finally {
      this.syncRevision.update((revision) => revision + 1);
    }
  }

  private setRequestError(error: unknown): void {
    const message = apiErrorMessage(error);
    if (error instanceof HttpErrorResponse && error.status === 409) {
      const normalizedMessage = message.toLowerCase();
      if (normalizedMessage.includes('already running') || normalizedMessage.includes('already in progress')) {
        this.state.set('idle');
        return;
      }
      if (normalizedMessage.includes('reconnect')) {
        this.state.set('needsReconnect');
        return;
      }
      if (normalizedMessage.includes('not connected')) {
        this.email.set(null);
        this.state.set('notConnected');
        return;
      }
    }
    this.errorMessage.set(message);
    this.state.set('error');
  }

  private isLastSyncStale(): boolean {
    const lastSyncAt = this.lastSyncAt();
    if (!lastSyncAt) {
      return true;
    }
    const lastSyncTime = Date.parse(lastSyncAt);
    return !Number.isFinite(lastSyncTime) || Date.now() - lastSyncTime >= GMAIL_SYNC_INTERVAL_MS;
  }

  private cleanup(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
  }
}
