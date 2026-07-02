import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';
import { criadorOuAdminGuard } from './guards/criador-ou-admin.guard';

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
    path: 'perfil/editar',
    loadComponent: () =>
      import('./paginas/editar-perfil/editar-perfil.component').then((m) => m.EditarPerfilComponent),
    canActivate: [authGuard, criadorOuAdminGuard]
  },
  {
    path: 'criador/:id',
    loadComponent: () =>
      import('./paginas/pagina-criador/pagina-criador.component').then((m) => m.PaginaCriadorComponent)
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
