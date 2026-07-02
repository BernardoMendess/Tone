import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Usuario } from '../../modelos/usuario.model';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css']
})
export class NavbarComponent {
  @Input() usuario: Partial<Usuario> | null = null;
  @Output() logoutClicado = new EventEmitter<void>();

  menuMobileAberto = false;
  menuUsuarioAberto = false;

  alternarMenuMobile(): void {
    this.menuMobileAberto = !this.menuMobileAberto;
  }

  fecharMenuMobile(): void {
    this.menuMobileAberto = false;
  }

  alternarMenuUsuario(): void {
    this.menuUsuarioAberto = !this.menuUsuarioAberto;
  }

  fecharMenuUsuario(): void {
    this.menuUsuarioAberto = false;
  }

  realizarLogout(): void {
    this.fecharMenuUsuario();
    this.fecharMenuMobile();
    this.logoutClicado.emit();
  }

  get iniciais(): string {
    if (!this.usuario?.nomeCompleto) return '?';
    return this.usuario.nomeCompleto
      .split(' ')
      .slice(0, 2)
      .map(n => n[0])
      .join('')
      .toUpperCase();
  }

  get podePublicar(): boolean {
    return this.usuario?.tipoPerfil === 'CRIADOR' || this.usuario?.tipoPerfil === 'ADMIN';
  }
}
