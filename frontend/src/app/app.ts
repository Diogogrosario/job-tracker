import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { EmailSyncService } from './services/email-sync.service';

@Component({
  imports: [DatePipe, RouterLink, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly emailSync = inject(EmailSyncService);

  ngOnInit(): void {
    this.emailSync.start();
  }
}
