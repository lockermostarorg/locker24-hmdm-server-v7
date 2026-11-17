import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// The landing page after sign-in: the fleet health dashboard when the plugin is loaded and the
// user may open it, otherwise the device list as before
export const HOME_PLUGIN = 'locker24-health';

export const homeRedirectGuard: CanActivateFn = () => {
  const router = inject(Router);
  const authService = inject(AuthService);
  const plugin = ((window as any)['__DYNPLUGINS__'] || []).find(
    (p: any) => p.identifier === HOME_PLUGIN,
  );
  const allowed =
    !!plugin && (!plugin.functionsPermission || authService.hasPermission(plugin.functionsPermission));

  return router.createUrlTree(allowed ? ['/home', ...plugin.path.split('/')] : ['/home/devices']);
};
