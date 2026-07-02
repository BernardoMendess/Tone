import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';

export const routes: Routes = [
  // Rota pública — Landing Page (sem guard)
  {
    path: '',
    loadComponent: () =>
      import('./paginas/landing/landing.component').then((m) => m.LandingComponent),
  },

  // Rotas de autenticação (sem navbar/footer)
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

  // Rotas autenticadas (com navbar/footer via LayoutComponent)
  {
    path: '',
    loadComponent: () =>
      import('./compartilhados/layout/layout.component').then((m) => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'marketplace',
        loadComponent: () =>
          import('./paginas/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'cadastro-produto',
        loadComponent: () =>
          import('./paginas/cadastro-produto/cadastro-produto.component').then(
            (m) => m.CadastroProdutoComponent
          ),
      },
      {
        path: 'categorias/nova',
        loadComponent: () =>
          import('./paginas/cadastro-categoria/cadastro-categoria.component').then(
            (m) => m.CadastroCategoriaComponent
          ),
        canActivate: [adminGuard],
      },
    ],
  },

  // Wildcard
  {
    path: '**',
    redirectTo: '',
  },
];
