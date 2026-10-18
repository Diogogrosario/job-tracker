import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, finalize, switchMap } from 'rxjs';
import { APPLICATION_STATUSES, ApplicationStatus, isApplicationStatus, statusLabel } from '../../models/application-status';
import { JobApplication } from '../../models/job-application';
import { apiErrorMessage, ApplicationService } from '../../services/application.service';

@Component({
  selector: 'app-application-list',
  imports: [DatePipe, RouterLink],
  templateUrl: './application-list.html',
})
export class ApplicationList implements OnInit {
  private readonly service = inject(ApplicationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests$ = new Subject<{ status?: ApplicationStatus; search?: string }>();
  private readonly searchChanges$ = new Subject<string>();

  protected readonly applications = signal<JobApplication[]>([]);
  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly searchText = signal('');
  protected readonly selectedStatus = signal<ApplicationStatus | ''>('');
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly deletingId = signal<number | null>(null);
  protected readonly statusLabel = statusLabel;

  ngOnInit(): void {
    this.requests$
      .pipe(
        switchMap(({ status, search }) => {
          this.loading.set(true);
          this.error.set('');
          return this.service.list(status, search).pipe(
            finalize(() => this.loading.set(false)),
            catchError((error: unknown) => {
              this.error.set(apiErrorMessage(error));
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((applications) => this.applications.set(applications));

    this.searchChanges$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadApplications());

    this.loadApplications();
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    this.searchChanges$.next(value);
  }

  protected onStatusChange(value: string): void {
    if (!value) {
      this.selectedStatus.set('');
    } else if (isApplicationStatus(value)) {
      this.selectedStatus.set(value);
    } else {
      this.error.set('Choose a valid status.');
      return;
    }
    this.loadApplications();
  }

  protected remove(application: JobApplication): void {
    if (!window.confirm(`Delete the application for ${application.company}?`)) {
      return;
    }

    this.deletingId.set(application.id);
    this.error.set('');
    this.service.delete(application.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.loadApplications();
      },
      error: (error: unknown) => {
        this.deletingId.set(null);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  private loadApplications(): void {
    this.requests$.next({
      status: this.selectedStatus() || undefined,
      search: this.searchText().trim() || undefined,
    });
  }
}
