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
