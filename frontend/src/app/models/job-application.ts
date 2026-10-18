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
}

export interface JobApplicationRequest {
  company: string;
  jobTitle: string;
  jobUrl: string | null;
  dateApplied: string | null;
  status: ApplicationStatus | null;
  notes: string | null;
}
