import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { SKIP_ALERT, THttpResponse } from 'hmdm-ui-kit';
import { map, Observable } from 'rxjs';
import { TFleetResponse } from '../types/fleet.type';
import { THistory } from '../types/history.type';
import { THealthSettings, THealthSettingsUpdate } from '../types/settings.type';

const BASE = 'rest/private/plugin-locker24-health';

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ALERT, true);
}

export class HealthApiError extends Error {
  constructor(readonly key: string) {
    super(key);
  }
}

const SERVER_KEYS: Record<string, string> = {
  'error.validation': 'plugin.locker24-health.error.validation',
  'error.permission.denied': 'plugin.locker24-health.error.permission',
  'error.internal.server': 'plugin.locker24-health.error.server',
};

export function errorKey(error: unknown): string {
  if (error instanceof HealthApiError) {
    return SERVER_KEYS[error.key] ?? 'plugin.locker24-health.error.server';
  }
  return 'plugin.locker24-health.error.network';
}

export function unwrap<T>(response: THttpResponse<T>): T {
  if (!response || response.status !== 'OK') {
    throw new HealthApiError(response?.message || 'error.unknown');
  }
  return response.data;
}

@Injectable({
  providedIn: 'root',
})
export class HealthService {
  private readonly http = inject(HttpClient);

  fetchFleet(): Observable<TFleetResponse> {
    return this.http
      .get<THttpResponse<TFleetResponse>>(`${BASE}/devices`, { context: quiet() })
      .pipe(map(unwrap));
  }

  fetchHistory(deviceId: number, hours: number): Observable<THistory> {
    const params = new HttpParams().set('hours', String(hours));
    return this.http
      .get<THttpResponse<THistory>>(`${BASE}/devices/${deviceId}/history`, {
        params,
        context: quiet(),
      })
      .pipe(map(unwrap));
  }

  fetchSettings(): Observable<THealthSettings> {
    return this.http
      .get<THttpResponse<THealthSettings>>(`${BASE}/settings`, { context: quiet() })
      .pipe(map(unwrap));
  }

  saveSettings(body: THealthSettingsUpdate): Observable<THealthSettings> {
    return this.http
      .put<THttpResponse<THealthSettings>>(`${BASE}/settings`, body, {
        context: quiet(),
      })
      .pipe(map(unwrap));
  }
}
