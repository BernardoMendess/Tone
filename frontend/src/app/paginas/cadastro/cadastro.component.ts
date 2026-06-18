import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../servicos/auth.service';
import { obterCriterios, senhaForteValidator } from '../../validadores/senha-forte.validator';

function confirmacaoSenhaValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const senha = group.get('senha')?.value;
    const confirmacao = group.get('confirmacaoSenha')?.value;
    return senha === confirmacao ? null : { senhasNaoConferem: true };
  };
}

@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './cadastro.component.html',
  styleUrls: ['./cadastro.component.css'],
})
export class CadastroComponent {
  formulario: FormGroup;
  carregando = false;
  erroGeral = '';
  cadastroRealizado = false;
  emailCadastrado = '';
  senhaVisivel = false;
  confirmacaoVisivel = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.formulario = this.fb.group(
      {
        nomeCompleto: ['', [Validators.required, Validators.minLength(3)]],
        email: ['', [Validators.required, Validators.email]],
        cpf: ['', [Validators.required]],
        senha: ['', [Validators.required, senhaForteValidator()]],
        confirmacaoSenha: ['', Validators.required],
      },
      { validators: confirmacaoSenhaValidator() }
    );
  }

  get nomeCompleto() { return this.formulario.get('nomeCompleto'); }
  get email() { return this.formulario.get('email'); }
  get cpf() { return this.formulario.get('cpf'); }
  get senha() { return this.formulario.get('senha'); }
  get confirmacaoSenha() { return this.formulario.get('confirmacaoSenha'); }

  get criteriosSenha() {
    return obterCriterios(this.senha);
  }

  alternarSenhaVisivel(): void { this.senhaVisivel = !this.senhaVisivel; }
  alternarConfirmacaoVisivel(): void { this.confirmacaoVisivel = !this.confirmacaoVisivel; }

  formatarCpf(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    let valor = input.value.replace(/\D/g, '');
    if (valor.length > 11) valor = valor.substring(0, 11);

    if (valor.length > 9) {
      valor = valor.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    } else if (valor.length > 6) {
      valor = valor.replace(/(\d{3})(\d{3})(\d{0,3})/, '$1.$2.$3');
    } else if (valor.length > 3) {
      valor = valor.replace(/(\d{3})(\d{0,3})/, '$1.$2');
    }

    this.formulario.patchValue({ cpf: valor }, { emitEvent: false });
    input.value = valor;
  }

  cadastrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.carregando = true;
    this.erroGeral = '';

    const { confirmacaoSenha, ...dados } = this.formulario.value;

    this.authService.cadastrar(dados).subscribe({
      next: () => {
        this.carregando = false;
        this.cadastroRealizado = true;
        this.emailCadastrado = dados.email;
      },
      error: (err) => {
        this.carregando = false;
        if (err.status === 409) {
          const mensagem: string = err.error?.mensagem ?? '';
          if (mensagem.toLowerCase().includes('e-mail') || mensagem.toLowerCase().includes('email')) {
            this.email?.setErrors({ jaExiste: true });
          } else if (mensagem.toLowerCase().includes('cpf')) {
            this.cpf?.setErrors({ jaExiste: true });
          } else {
            this.erroGeral = mensagem;
          }
        } else {
          this.erroGeral = 'Erro ao criar conta. Tente novamente.';
        }
      },
    });
  }
}
