import { Routes } from '@angular/router';
import { App } from './app';

export const MFE_ROUTES: Routes = [
  {
    path: '',
    component: App,
    children: [
      { path: '', redirectTo: 'main', pathMatch: 'full' },
      {
        path: 'main',
        loadComponent: () => import('./pages/fleet/fleet').then((m) => m.Fleet),
      },
      {
        path: 'main/device/:deviceId',
        loadComponent: () => import('./pages/device/device').then((m) => m.Device),
      },
      {
        path: 'settings',
        loadComponent: () => import('./pages/settings/settings').then((m) => m.Settings),
      },
    ],
  },
];
