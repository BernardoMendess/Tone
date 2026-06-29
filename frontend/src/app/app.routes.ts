import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';

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
    path: 'cadastro-produto',
    loadComponent: () =>
      import('./paginas/cadastro-produto/cadastro-produto.component').then((m) => m.CadastroProdutoComponent),
    canActivate: [authGuard]
  },
  {
    path: 'categorias/nova',
    loadComponent: () =>
      import('./paginas/cadastro-categoria/cadastro-categoria.component').then((m) => m.CadastroCategoriaComponent),
    canActivate: [adminGuard]
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
    canActivate: [authGuard],
    loadComponent: () =>
      import('./paginas/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: '**',
    redirectTo: '',
  }
];
