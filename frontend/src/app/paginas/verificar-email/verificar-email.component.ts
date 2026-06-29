import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AuthService } from '../../servicos/auth.service';

type EstadoVerificacao = 'carregando' | 'sucesso' | 'erro';

@Component({
  selector: 'app-verificar-email',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './verificar-email.component.html',
  styleUrls: ['./verificar-email.component.css'],
})
export class VerificarEmailComponent implements OnInit {
  estado: EstadoVerificacao = 'carregando';

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.estado = 'erro';
      return;
    }

    this.authService.verificarEmail(token).subscribe({
      next: () => (this.estado = 'sucesso'),
      error: () => (this.estado = 'erro'),
    });
  }
}
