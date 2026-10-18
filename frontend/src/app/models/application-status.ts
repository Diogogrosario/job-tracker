export const APPLICATION_STATUSES = [
  'APPLIED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'CLOSED',
] as const;

export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number];

export function isApplicationStatus(value: string): value is ApplicationStatus {
  return APPLICATION_STATUSES.some((status) => status === value);
}

export function statusLabel(status: ApplicationStatus): string {
  return status.replaceAll('_', ' ');
}
