import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../servicos/auth.service';
import { map, catchError, of } from 'rxjs';

export const criadorOuAdminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.obterUsuarioLogado().pipe(
    map((usuario) => {
      if (usuario.tipoPerfil === 'CRIADOR' || usuario.tipoPerfil === 'ADMIN') {
        return true;
      }
      return router.createUrlTree(['/']);
    }),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
