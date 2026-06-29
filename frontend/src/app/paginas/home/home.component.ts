import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../servicos/auth.service';
import { Router } from '@angular/router';
import { Usuario } from '../../modelos/usuario.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
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
}
