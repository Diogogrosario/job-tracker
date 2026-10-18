import { ApplicationStatus } from './application-status';

export interface JobApplication {
  id: number;
  company: string;
  jobTitle: string;
  jobUrl: string | null;
  dateApplied: string | null;
  status: ApplicationStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  lastStatusSource: string | null;
  lastEmailSubject: string | null;
  lastEmailAt: string | null;
}

export interface ApplicationEmail {
  id: number;
  fromAddress: string | null;
  subject: string | null;
  receivedAt: string;
  category: string | null;
  confidence: number | null;
}

export interface JobApplicationRequest {
  company: string;
  jobTitle: string;
  jobUrl: string | null;
  dateApplied: string | null;
  status: ApplicationStatus | null;
  notes: string | null;
}
