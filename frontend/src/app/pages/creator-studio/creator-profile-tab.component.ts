import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { CreatorProfileDto } from '../../core/models';

@Component({
  selector: 'app-creator-profile-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (profile(); as prof) {
      <section class="card pad narrow">
        <div class="prof-head">
          <h2>Gestão do Perfil Público (RF11)</h2>
          <a [routerLink]="['/creators', prof.slug]" class="btn btn-outline btn-sm">Ver Página Pública ↗</a>
        </div>
        <form (ngSubmit)="saveProfile()">
          <div class="grid-2">
            <div class="field">
              <label>Nome de Exibição / Estúdio *</label>
              <input class="input" [(ngModel)]="form.displayName" name="pName" required/>
            </div>
            <div class="field">
              <label>Slug da URL (/creators/slug) *</label>
              <input class="input mono" [(ngModel)]="form.slug" name="pSlug" required/>
            </div>
          </div>
          <div class="field">
            <label>Biografia</label>
            <textarea class="textarea" [(ngModel)]="form.bio" name="pBio"></textarea>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>Equipamento Principal</label>
              <input class="input" [(ngModel)]="form.mainGear" name="pGear"/>
            </div>
            <div class="field">
              <label>Chave PIX para Repasses *</label>
              <input class="input" [(ngModel)]="form.pixKey" name="pPix" required/>
            </div>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>YouTube (https://youtube.com/...)</label>
              <input class="input" [(ngModel)]="form.youtubeUrl" name="pYt"/>
            </div>
            <div class="field">
              <label>Instagram (https://instagram.com/...)</label>
              <input class="input" [(ngModel)]="form.instagramUrl" name="pIg"/>
            </div>
          </div>
          <div class="field">
            <label>Website (https://)</label>
            <input class="input" [(ngModel)]="form.websiteUrl" name="pWeb"/>
          </div>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">Salvar Alterações</button>
        </form>
      </section>
    }
  `,
  styles: [`
    .pad { padding: 22px; }
    .narrow { max-width: 720px; }
    .grid-2 { display: grid; grid-template-columns: 1fr; gap: 12px; @media (min-width: 640px) { grid-template-columns: 1fr 1fr; } }
    .prof-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; h2 { margin: 0; } }
  `]
})
export class CreatorProfileTabComponent {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly profile = input<CreatorProfileDto | null>(null);
  readonly profileUpdated = output<CreatorProfileDto>();

  readonly saving = signal(false);
  form = { displayName: '', slug: '', bio: '', mainGear: '', pixKey: '', youtubeUrl: '', instagramUrl: '', websiteUrl: '' };

  constructor() {
    effect(() => {
      const prof = this.profile();
      if (prof) {
        this.form = {
          displayName: prof.displayName,
          slug: prof.slug,
          bio: prof.bio || '',
          mainGear: prof.mainGear || '',
          pixKey: prof.pixKey || '',
          youtubeUrl: prof.youtubeUrl || '',
          instagramUrl: prof.instagramUrl || '',
          websiteUrl: prof.websiteUrl || ''
        };
      }
    });
  }

  saveProfile(): void {
    this.saving.set(true);
    this.api.updateMyCreatorProfile(this.form).subscribe({
      next: updated => {
        this.saving.set(false);
        this.toast.show('Perfil público atualizado!', 'success');
        this.profileUpdated.emit(updated);
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao atualizar perfil.', 'error');
      }
    });
  }
}
