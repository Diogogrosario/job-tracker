import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { EmailSyncService } from './services/email-sync.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        {
          provide: EmailSyncService,
          useValue: {
            state: signal('checking'),
            email: signal<string | null>(null),
            lastSyncAt: signal<string | null>(null),
            lastResult: signal(null),
            errorMessage: signal<string | null>(null),
            start: () => {},
            syncNow: () => {},
            resultTooltip: () => '',
          },
        },
      ],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the application brand', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand')?.textContent).toContain('Job Tracker');
  });
});
