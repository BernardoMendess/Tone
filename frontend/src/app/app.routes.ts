import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./paginas/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'cadastro',
    loadComponent: () =>
      import('./paginas/cadastro/cadastro.component').then((m) => m.CadastroComponent),
  },
  {
    path: 'verificar-email',
    loadComponent: () =>
      import('./paginas/verificar-email/verificar-email.component').then(
        (m) => m.VerificarEmailComponent
      ),
  },
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full',
  },
];
