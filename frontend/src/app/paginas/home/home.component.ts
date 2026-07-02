import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../servicos/auth.service';
import { Usuario } from '../../modelos/usuario.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  usuario: Partial<Usuario> | null = null;
  carregando = true;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.authService.obterUsuarioLogado().subscribe({
      next: (user) => {
        this.usuario = user;
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.router.navigate(['/login']);
      }
    });
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login']);
      }
    });
  }

  /** Saudação baseada na hora do dia (somente para UI) */
  get saudacao(): string {
    const hora = new Date().getHours();
    if (hora >= 5 && hora < 12) return 'Bom dia';
    if (hora >= 12 && hora < 18) return 'Boa tarde';
    return 'Boa noite';
  }

  get podePublicar(): boolean {
    return this.usuario?.tipoPerfil === 'CRIADOR' || this.usuario?.tipoPerfil === 'ADMIN';
  }
}
