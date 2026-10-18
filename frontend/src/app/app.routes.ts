import { Routes } from '@angular/router';
import { ApplicationDetail } from './pages/application-detail/application-detail';
import { ApplicationForm } from './pages/application-form/application-form';
import { ApplicationList } from './pages/application-list/application-list';

export const routes: Routes = [
  { path: '', component: ApplicationList },
  { path: 'applications/new', component: ApplicationForm },
  { path: 'applications/:id/edit', component: ApplicationForm },
  { path: 'applications/:id', component: ApplicationDetail },
  { path: '**', redirectTo: '' },
];
