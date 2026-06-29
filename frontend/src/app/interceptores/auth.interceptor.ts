import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Interceptor de autenticação.
 * Com JWT em HttpOnly Cookie, o browser envia o cookie automaticamente nas requisições
 * para o mesmo domínio. Apenas garantimos withCredentials: true em todas as chamadas à API.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const reqComCredenciais = req.clone({
    withCredentials: true,
  });

  return next(reqComCredenciais);
};
