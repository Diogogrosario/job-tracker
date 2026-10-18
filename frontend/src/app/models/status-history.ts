import { ApplicationStatus } from './application-status';

export interface StatusHistory {
  id: number;
  oldStatus: ApplicationStatus | null;
  newStatus: ApplicationStatus;
  source: string;
  sourceMessageId: string | null;
  changedAt: string;
}
