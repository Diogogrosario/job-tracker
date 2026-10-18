import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApplicationStatus, statusLabel } from '../../models/application-status';
import { JobApplication } from '../../models/job-application';
import { StatusHistory } from '../../models/status-history';
import { apiErrorMessage, ApplicationService } from '../../services/application.service';

@Component({
  selector: 'app-application-detail',
  imports: [DatePipe, RouterLink],
  templateUrl: './application-detail.html',
})
export class ApplicationDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(ApplicationService);

  protected readonly application = signal<JobApplication | null>(null);
  protected readonly history = signal<StatusHistory[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly statusLabel = statusLabel;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isSafeInteger(id) || id < 1) {
      this.error.set('Invalid application id.');
      return;
    }

    this.loading.set(true);
    forkJoin({
      application: this.service.get(id),
      history: this.service.history(id),
    }).subscribe({
      next: (result) => {
        this.application.set(result.application);
        this.history.set(result.history);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(apiErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected statusClass(status: ApplicationStatus): string {
    return `status-badge status-${status.toLowerCase()}`;
  }
}
