import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, finalize, switchMap } from 'rxjs';
import { APPLICATION_STATUSES, ApplicationStatus, isApplicationStatus, statusLabel } from '../../models/application-status';
import { EmailProcessingStatus } from '../../models/email-sync';
import { JobApplication } from '../../models/job-application';
import { ReviewApprovalRequest, ReviewQueueItem } from '../../models/review-queue';
import { apiErrorMessage, ApplicationService } from '../../services/application.service';
import { EmailSyncService } from '../../services/email-sync.service';
import { ReviewQueueService } from '../../services/review-queue.service';

interface AttentionDraft {
  company: string;
  jobTitle: string;
  status: ApplicationStatus | '';
  applicationId: number | null;
}

@Component({
  selector: 'app-application-list',
  imports: [DatePipe, RouterLink],
  templateUrl: './application-list.html',
})
export class ApplicationList implements OnInit {
  private readonly service = inject(ApplicationService);
  private readonly reviewService = inject(ReviewQueueService);
  private readonly emailSync = inject(EmailSyncService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests$ = new Subject<{ status?: ApplicationStatus; search?: string }>();
  private readonly searchChanges$ = new Subject<string>();

  protected readonly applications = signal<JobApplication[]>([]);
  protected readonly attentionItems = signal<ReviewQueueItem[]>([]);
  protected readonly drafts = signal<Record<number, AttentionDraft>>({});
  protected readonly busyIds = signal<Set<number>>(new Set());
  protected readonly cardErrors = signal<Record<number, string>>({});
  protected readonly processStatus = signal<EmailProcessingStatus | null>(null);
  protected readonly notice = signal('');
  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly searchText = signal('');
  protected readonly selectedStatus = signal<ApplicationStatus | ''>('');
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly deletingId = signal<number | null>(null);
  protected readonly statusLabel = statusLabel;
  private processPollTimeout: ReturnType<typeof setTimeout> | null = null;
  private fallbackRefreshInterval: ReturnType<typeof setInterval> | null = null;

  constructor() {
    let observedSyncRevision = this.emailSync.syncRevision();
    effect(() => {
      const revision = this.emailSync.syncRevision();
      if (revision !== observedSyncRevision) {
        observedSyncRevision = revision;
        this.refreshAll();
        this.scheduleProcessStatusCheck(1_000);
      }
    });
    this.destroyRef.onDestroy(() => {
      if (this.processPollTimeout !== null) {
        clearTimeout(this.processPollTimeout);
      }
      if (this.fallbackRefreshInterval !== null) {
        clearInterval(this.fallbackRefreshInterval);
      }
    });
  }

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

    this.refreshAll();
    this.checkProcessStatus();
    this.fallbackRefreshInterval = setInterval(() => {
      this.refreshAll();
      this.checkProcessStatus();
    }, 60_000);
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

  protected draft(item: ReviewQueueItem): AttentionDraft {
    return this.drafts()[item.id] ?? this.initialDraft(item);
  }

  protected changeDraft(
    item: ReviewQueueItem,
    field: keyof AttentionDraft,
    value: string,
  ): void {
    const current = this.draft(item);
    let updated: AttentionDraft;
    if (field === 'status') {
      updated = { ...current, status: isApplicationStatus(value) ? value : '' };
    } else if (field === 'applicationId') {
      updated = { ...current, applicationId: value ? Number(value) : null };
    } else if (field === 'company') {
      updated = { ...current, company: value };
    } else {
      updated = { ...current, jobTitle: value };
    }
    this.drafts.update((drafts) => ({ ...drafts, [item.id]: updated }));
  }

  protected selectedApplicationCompany(item: ReviewQueueItem): string {
    const id = this.draft(item).applicationId;
    return this.applications().find((application) => application.id === id)?.company ?? '';
  }

  protected canApprove(item: ReviewQueueItem): boolean {
    const draft = this.draft(item);
    if (!draft.status || this.busyIds().has(item.id)) {
      return false;
    }
    if (item.applicationId !== null || draft.applicationId !== null) {
      return true;
    }
    return Boolean(draft.company.trim() && draft.jobTitle.trim());
  }

  protected approve(item: ReviewQueueItem): void {
    if (!this.canApprove(item)) {
      return;
    }
    const draft = this.draft(item);
    const request: ReviewApprovalRequest = { status: draft.status as ApplicationStatus };
    if (item.applicationId !== null) {
      request.applicationId = item.applicationId;
    } else if (draft.applicationId !== null) {
      request.applicationId = draft.applicationId;
    } else {
      request.company = draft.company.trim();
      request.jobTitle = draft.jobTitle.trim();
    }
    this.setBusy(item.id, true);
    this.cardErrors.update((errors) => ({ ...errors, [item.id]: '' }));
    this.reviewService.approve(item.id, request).subscribe({
      next: () => {
        this.setBusy(item.id, false);
        this.removeAttentionItem(item.id);
        this.notice.set('');
        this.refreshAll();
      },
      error: (error: unknown) => {
        this.setBusy(item.id, false);
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.removeAttentionItem(item.id);
          this.notice.set('This attention item was already handled.');
          return;
        }
        this.cardErrors.update((errors) => ({ ...errors, [item.id]: apiErrorMessage(error) }));
      },
    });
  }

  protected dismiss(item: ReviewQueueItem): void {
    this.setBusy(item.id, true);
    this.cardErrors.update((errors) => ({ ...errors, [item.id]: '' }));
    this.reviewService.dismiss(item.id).subscribe({
      next: () => {
        this.setBusy(item.id, false);
        this.removeAttentionItem(item.id);
        this.refreshAll();
      },
      error: (error: unknown) => {
        this.setBusy(item.id, false);
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.removeAttentionItem(item.id);
          this.notice.set('This attention item was already handled.');
          return;
        }
        this.cardErrors.update((errors) => ({ ...errors, [item.id]: apiErrorMessage(error) }));
      },
    });
  }

  protected categoryLabel(category: string | null): string {
    return category ? category.replaceAll('_', ' ') : 'Unclassified';
  }

  private loadApplications(): void {
    this.requests$.next({
      status: this.selectedStatus() || undefined,
      search: this.searchText().trim() || undefined,
    });
  }

  private refreshAll(): void {
    this.loadApplications();
    this.reviewService.pending()
      .pipe(
        catchError((error: unknown) => {
          this.error.set(apiErrorMessage(error));
          return EMPTY;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((items) => {
        const previousDrafts = this.drafts();
        this.attentionItems.set(items);
        this.drafts.set(Object.fromEntries(items.map((item) => [
          item.id,
          previousDrafts[item.id] ?? this.initialDraft(item),
        ])));
      });
  }

  private checkProcessStatus(): void {
    this.emailSync.processStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => {
          const wasRunning = this.processStatus()?.running ?? false;
          this.processStatus.set(status);
          if (status.running) {
            this.scheduleProcessStatusCheck(10_000);
          } else {
            this.clearProcessPoll();
            if (wasRunning) {
              this.refreshAll();
            }
          }
        },
        error: () => this.processStatus.set(null),
      });
  }

  private scheduleProcessStatusCheck(delay: number): void {
    this.clearProcessPoll();
    this.processPollTimeout = setTimeout(() => {
      this.processPollTimeout = null;
      this.checkProcessStatus();
    }, delay);
  }

  private clearProcessPoll(): void {
    if (this.processPollTimeout !== null) {
      clearTimeout(this.processPollTimeout);
      this.processPollTimeout = null;
    }
  }

  private initialDraft(item: ReviewQueueItem): AttentionDraft {
    return {
      company: item.proposedCompany ?? '',
      jobTitle: item.proposedJobTitle ?? '',
      status: item.proposedStatus ?? '',
      applicationId: null,
    };
  }

  private removeAttentionItem(id: number): void {
    this.attentionItems.update((items) => items.filter((item) => item.id !== id));
    this.drafts.update((drafts) => {
      const { [id]: _removed, ...remaining } = drafts;
      return remaining;
    });
    this.cardErrors.update((errors) => {
      const { [id]: _removed, ...remaining } = errors;
      return remaining;
    });
  }

  private setBusy(id: number, busy: boolean): void {
    this.busyIds.update((ids) => {
      const updated = new Set(ids);
      if (busy) {
        updated.add(id);
      } else {
        updated.delete(id);
      }
      return updated;
    });
  }
}
