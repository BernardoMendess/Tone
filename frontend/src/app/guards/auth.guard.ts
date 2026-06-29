import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../servicos/auth.service';
import { map, catchError, of } from 'rxjs';

/**
 * Guard de rota que verifica autenticação consultando o endpoint /me no servidor.
 * Necessário pois o JWT está em HttpOnly Cookie e não é acessível via JavaScript.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.obterUsuarioLogado().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
