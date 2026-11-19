import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { CartService } from '../../core/services/cart.service';
import { WishlistService } from '../../core/services/wishlist.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-auth-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container auth-wrap">
      <div class="auth-card card">
        <div class="tabs-bar">
          <button class="tab-btn" [class.active]="mode() === 'login'" (click)="mode.set('login')">Entrar</button>
          <button class="tab-btn" [class.active]="mode() === 'register'" (click)="mode.set('register')">Criar Conta</button>
        </div>

        @if (mode() === 'login') {
          <form (ngSubmit)="onLogin()">
            <div class="field">
              <label>E-mail</label>
              <input type="email" class="input" [(ngModel)]="email" name="email" required placeholder="voce@exemplo.com"/>
            </div>
            <div class="field">
              <label>Senha</label>
              <input type="password" class="input" [(ngModel)]="password" name="password" required placeholder="••••••••"/>
            </div>
            <button type="submit" class="btn btn-primary full" [disabled]="loading()">
              {{ loading() ? 'Autenticando...' : 'Entrar no Tone' }}
            </button>
          </form>

          @if (sandboxEnabled()) {
            <div class="demo-box">
              <div class="demo-title mono">ACESSO RÁPIDO (CONTAS DE DEMONSTRAÇÃO — DEV)</div>
              <div class="demo-grid">
                <button type="button" class="btn btn-outline btn-sm" (click)="fillAndLogin('buyer@tone.com', 'Buyer@123')">
                  Comprador (Biblioteca + Favoritos)
                </button>
                <button type="button" class="btn btn-outline btn-sm" (click)="fillAndLogin('lucas@tone.com', 'Creator@123')">
                  Criador Aprovado (Estúdio + Financeiro)
                </button>
                <button type="button" class="btn btn-outline btn-sm" (click)="fillAndLogin('pedro@tone.com', 'Creator@123')">
                  Criador Pendente (Em Curadoria)
                </button>
                <button type="button" class="btn btn-outline btn-sm" (click)="fillAndLogin('admin@tone.com', 'Admin@123')">
                  Administrador (Curadoria + Split)
                </button>
              </div>
            </div>
          }
        } @else {
          <form (ngSubmit)="onRegister()">
            <div class="field">
              <label>Nome Completo</label>
              <input type="text" class="input" [(ngModel)]="fullName" name="fullName" required placeholder="Seu nome"/>
            </div>
            <div class="field">
              <label>E-mail</label>
              <input type="email" class="input" [(ngModel)]="email" name="email" required placeholder="voce@exemplo.com"/>
            </div>
            <div class="field">
              <label>Senha (mín. 10 caracteres)</label>
              <input type="password" class="input" [(ngModel)]="password" name="password" required minlength="10"/>
            </div>
            <button type="submit" class="btn btn-primary full" [disabled]="loading()">
              {{ loading() ? 'Criando conta...' : 'Criar Conta Gratuita' }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
  styles: [`
    .auth-wrap {
      padding-top: 42px;
      display: flex;
      justify-content: center;
    }
    .auth-card {
      width: 100%;
      max-width: 440px;
      padding: 24px;
    }
    .full { width: 100%; margin-top: 6px; }
    .demo-box {
      margin-top: 22px;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }
    .demo-title {
      font-size: 10.5px;
      color: var(--ink-muted);
      letter-spacing: 0.05em;
      margin-bottom: 10px;
    }
    .demo-grid {
      display: flex;
      flex-direction: column;
      gap: 7px;
      button { justify-content: flex-start; }
    }
  `]
})
export class AuthPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);
  private readonly wishlist = inject(WishlistService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly mode = signal<'login' | 'register'>('login');
  readonly loading = signal(false);
  readonly sandboxEnabled = signal(false);

  fullName = '';
  email = '';
  password = '';

  ngOnInit(): void {
    this.api.getPublicConfig().subscribe({
      next: cfg => this.sandboxEnabled.set(!!cfg.sandboxEnabled),
      error: () => this.sandboxEnabled.set(false)
    });
  }

  fillAndLogin(email: string, password: string): void {
    this.email = email;
    this.password = password;
    this.onLogin();
  }

  onLogin(): void {
    this.loading.set(true);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.afterAuth(),
      error: err => {
        this.loading.set(false);
        this.toast.show(err.error?.message || 'Credenciais inválidas.', 'error');
      }
    });
  }

  onRegister(): void {
    this.loading.set(true);
    this.auth.register(this.fullName, this.email, this.password).subscribe({
      next: () => this.afterAuth(),
      error: err => {
        this.loading.set(false);
        this.toast.show(err.error?.message || 'Erro ao criar conta.', 'error');
      }
    });
  }

  private afterAuth(): void {
    this.loading.set(false);
    this.cart.refresh();
    this.wishlist.refresh();
    this.toast.show(`Bem-vindo(a), ${this.auth.user()?.fullName}!`, 'success');
    const redirect = this.route.snapshot.queryParamMap.get('redirect') || '/';
    this.router.navigateByUrl(redirect);
  }
}
