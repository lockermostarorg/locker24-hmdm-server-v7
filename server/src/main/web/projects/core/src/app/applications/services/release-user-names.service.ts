import { inject, Injectable, signal } from '@angular/core';
import { take } from 'rxjs';
import { UserService } from '../../entity/user/services/user.service';
import { AuthService } from '../../shared/services/auth.service';

@Injectable({ providedIn: 'root' })
export class ReleaseUserNamesService {
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private requested = false;

  readonly names = signal<ReadonlyMap<number, string>>(new Map());

  load(): void {
    if (this.requested || !this.authService.hasPermission('settings')) {
      return;
    }

    this.requested = true;
    this.userService
      .searchUsers('')
      .pipe(take(1))
      .subscribe({
        next: (users) =>
          this.names.set(new Map((users ?? []).map((user) => [user.id, user.name || user.login]))),
        error: () => (this.requested = false),
      });
  }

  nameOf(userId: number): string | null {
    return this.names().get(userId) ?? null;
  }
}
