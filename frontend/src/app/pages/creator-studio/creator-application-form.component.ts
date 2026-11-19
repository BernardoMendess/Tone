import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { CreatorProfileDto } from '../../core/models';

@Component({
  selector: 'app-creator-application-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card pad narrow">
      @if (rejectedReason()) {
        <div class="reject-alert">
          <strong>Solicitação anterior recusada pela curadoria:</strong> {{ rejectedReason() }}
        </div>
      }
      <h2>Torne-se um Criador Curado no Tone</h2>
      <p class="muted-p">
        Para garantir excelência sonora na plataforma, todos os novos criadores passam por curadoria antes de publicar produtos (RF12).
      </p>

      <form (ngSubmit)="submit()">
        <div class="field">
          <label>Nome do Estúdio / Marca *</label>
          <input class="input" [(ngModel)]="form.displayName" name="displayName" required placeholder="Ex.: Studio Valve Tones"/>
        </div>
        <div class="field">
          <label>Biografia e Especialidade *</label>
          <textarea class="textarea" [(ngModel)]="form.bio" name="bio" required placeholder="Conte sobre sua experiência, amplificadores e processo de captação..."></textarea>
        </div>
        <div class="grid-2">
          <div class="field">
            <label>Equipamento Principal *</label>
            <input class="input" [(ngModel)]="form.mainGear" name="mainGear" required placeholder="Helix, Quad Cortex, Kemper..."/>
          </div>
          <div class="field">
            <label>Chave PIX para Recebimento *</label>
            <input class="input" [(ngModel)]="form.pixKey" name="pixKey" required placeholder="CPF, CNPJ, e-mail, +55 ou UUID"/>
          </div>
        </div>
        <div class="grid-2">
          <div class="field">
            <label>Link de Demonstração / Portfólio (https://)</label>
            <input class="input" [(ngModel)]="form.portfolioDemoUrl" name="portfolioDemoUrl" placeholder="https://youtube.com/..."/>
          </div>
          <div class="field">
            <label>Instagram (https://instagram.com/...)</label>
            <input class="input" [(ngModel)]="form.instagramUrl" name="instagramUrl" placeholder="https://instagram.com/..."/>
          </div>
        </div>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ saving() ? 'Enviando...' : 'Enviar para Fila de Curadoria' }}
        </button>
      </form>
    </section>
  `,
  styles: [`
    .pad { padding: 22px; }
    .narrow { max-width: 720px; }
    .muted-p { color: var(--ink-muted); font-size: 14px; margin: 0 0 18px; }
    .grid-2 { display: grid; grid-template-columns: 1fr; gap: 12px; @media (min-width: 640px) { grid-template-columns: 1fr 1fr; } }
    .reject-alert {
      padding: 12px 14px;
      background: var(--danger-soft);
      border: 1px solid #fecaca;
      color: var(--danger);
      border-radius: var(--radius-sm);
      margin-bottom: 16px;
      font-size: 13.5px;
    }
  `]
})
export class CreatorApplicationFormComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly rejectedReason = input<string | null>(null);
  readonly applied = output<CreatorProfileDto>();

  readonly saving = signal(false);
  form = { displayName: '', bio: '', mainGear: '', pixKey: '', portfolioDemoUrl: '', instagramUrl: '' };

  submit(): void {
    this.saving.set(true);
    this.api.applyCreator(this.form).subscribe({
      next: prof => {
        this.saving.set(false);
        this.auth.refreshMe().subscribe();
        this.toast.show('Candidatura enviada para a fila de curadoria!', 'success');
        this.applied.emit(prof);
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao enviar solicitação.', 'error');
      }
    });
  }
}
