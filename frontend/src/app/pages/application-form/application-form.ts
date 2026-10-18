import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { APPLICATION_STATUSES, ApplicationStatus, statusLabel } from '../../models/application-status';
import { JobApplicationRequest } from '../../models/job-application';
import { apiErrorMessage, ApplicationService } from '../../services/application.service';

function todayAsDateInputValue(): string {
  const today = new Date();
  const timezoneOffset = today.getTimezoneOffset() * 60_000;
  return new Date(today.getTime() - timezoneOffset).toISOString().slice(0, 10);
}

@Component({
  selector: 'app-application-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './application-form.html',
})
export class ApplicationForm {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(ApplicationService);

  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly statusLabel = statusLabel;
  protected readonly form = this.formBuilder.nonNullable.group({
    company: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    jobTitle: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    jobUrl: ['', Validators.maxLength(1000)],
    dateApplied: [todayAsDateInputValue()],
    status: ['APPLIED' as ApplicationStatus],
    notes: [''],
  });
  protected readonly applicationId = signal<number | null>(null);
  protected readonly isEditing = signal(false);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id !== null) {
      const parsedId = Number(id);
      if (!Number.isSafeInteger(parsedId) || parsedId < 1) {
        this.loadFailed.set(true);
        this.error.set('Invalid application id.');
        return;
      }

      this.applicationId.set(parsedId);
      this.isEditing.set(true);
      this.loading.set(true);
      this.service.get(parsedId).subscribe({
        next: (application) => {
          this.form.patchValue({
            company: application.company,
            jobTitle: application.jobTitle,
            jobUrl: application.jobUrl ?? '',
            dateApplied: application.dateApplied ?? '',
            status: application.status,
            notes: application.notes ?? '',
          });
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadFailed.set(true);
          this.error.set(apiErrorMessage(error));
        },
      });
    }
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const values = this.form.getRawValue();
    const request: JobApplicationRequest = {
      company: values.company.trim(),
      jobTitle: values.jobTitle.trim(),
      jobUrl: values.jobUrl.trim() || null,
      dateApplied: values.dateApplied || null,
      status: values.status,
      notes: values.notes.trim() || null,
    };

    this.saving.set(true);
    this.error.set('');
    const id = this.applicationId();
    if (id !== null) {
      this.service.update(id, request).subscribe({
        next: () => void this.router.navigate(['/applications', id]),
        error: (error: unknown) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(error));
        },
      });
      return;
    }

    this.service.create(request).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }
}
