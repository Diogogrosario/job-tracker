import { ApplicationStatus } from './application-status';

export type EmailCategory =
  | 'APPLICATION_CONFIRMATION'
  | 'INTERVIEW_INVITATION'
  | 'ASSESSMENT'
  | 'REJECTION'
  | 'OFFER'
  | 'UNRELATED';

export interface ReviewQueueItem {
  id: number;
  emailMessageId: number;
  action: 'CREATE_APPLICATION' | 'UPDATE_STATUS';
  proposedStatus: ApplicationStatus | null;
  proposedCompany: string | null;
  proposedJobTitle: string | null;
  category: EmailCategory | null;
  confidence: number | null;
  reason: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  subject: string | null;
  fromAddress: string | null;
  receivedAt: string;
  snippet: string | null;
  applicationId: number | null;
  applicationCompany: string | null;
  applicationJobTitle: string | null;
  applicationStatus: ApplicationStatus | null;
}

export interface ReviewApprovalRequest {
  company?: string;
  jobTitle?: string;
  status: ApplicationStatus;
  applicationId?: number;
}
