export interface MailboxStatus {
  connected: boolean;
  needsReconnect: boolean;
  email: string | null;
  lastSyncAt: string | null;
  lastSyncStatus: string | null;
  messageCount: number;
}

export interface EmailSyncResult {
  fetched: number;
  newMessages: number;
  skipped: number;
  errors: number;
}

export interface EmailSyncResponse extends EmailSyncResult {
  throttled: boolean;
}

export interface EmailProcessingStatus {
  unprocessedCount: number;
  pendingReviewCount: number;
  running: boolean;
  progress: {
    processed: number;
    proposals: number;
    autoApplied: number;
    errors: number;
  };
  lastRunSummary: {
    processed: number;
    proposals: number;
    autoApplied: number;
    errors: number;
    completedAt: string;
  } | null;
  lastError: string | null;
}
