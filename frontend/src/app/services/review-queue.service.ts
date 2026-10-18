import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApplicationStatus } from '../models/application-status';
import { ReviewApprovalRequest, ReviewQueueItem } from '../models/review-queue';

@Injectable({ providedIn: 'root' })
export class ReviewQueueService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/review-queue';

  pending(): Observable<ReviewQueueItem[]> {
    return this.http.get<ReviewQueueItem[]>(this.baseUrl, {
      params: new HttpParams().set('status', 'PENDING'),
    });
  }

  approve(id: number, request: ReviewApprovalRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/approve`, request);
  }

  dismiss(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/reject`, null);
  }
}
