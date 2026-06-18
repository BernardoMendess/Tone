import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../servicos/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  formulario: FormGroup;
  carregando = false;
  erroGeral = '';
  erroEmailNaoVerificado = false;
  senhaVisivel = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.formulario = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      senha: ['', [Validators.required]],
    });
  }

  get email() { return this.formulario.get('email'); }
  get senha() { return this.formulario.get('senha'); }

  alternarSenhaVisivel(): void {
    this.senhaVisivel = !this.senhaVisivel;
  }

  entrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.carregando = true;
    this.erroGeral = '';
    this.erroEmailNaoVerificado = false;

    this.authService.login(this.formulario.value).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err) => {
        this.carregando = false;
        if (err.status === 403) {
          this.erroEmailNaoVerificado = true;
        } else if (err.status === 401) {
          this.erroGeral = 'E-mail ou senha inválidos.';
        } else {
          this.erroGeral = 'Erro ao fazer login. Tente novamente.';
        }
      },
    });
  }
}
