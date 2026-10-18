import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApplicationStatus } from '../models/application-status';
import { ApplicationEmail, JobApplication, JobApplicationRequest } from '../models/job-application';
import { StatusHistory } from '../models/status-history';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/applications';

  list(status?: ApplicationStatus, search?: string): Observable<JobApplication[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    if (search?.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<JobApplication[]>(this.baseUrl, { params });
  }

  get(id: number): Observable<JobApplication> {
    return this.http.get<JobApplication>(`${this.baseUrl}/${id}`);
  }

  create(request: JobApplicationRequest): Observable<JobApplication> {
    return this.http.post<JobApplication>(this.baseUrl, request);
  }

  update(id: number, request: JobApplicationRequest): Observable<JobApplication> {
    return this.http.put<JobApplication>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  history(id: number): Observable<StatusHistory[]> {
    return this.http.get<StatusHistory[]>(`${this.baseUrl}/${id}/history`);
  }

  emails(id: number): Observable<ApplicationEmail[]> {
    return this.http.get<ApplicationEmail[]>(`${this.baseUrl}/${id}/emails`);
  }
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body: unknown = error.error;
    if (typeof body === 'object' && body !== null && 'message' in body) {
      const message = body.message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return error.message || 'The request failed.';
  }
  return 'An unexpected error occurred.';
}
