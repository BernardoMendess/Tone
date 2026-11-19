import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { PICKUP_LABELS, PickupType, ProductCard, ProductType, formatBRL } from '../../core/models';

@Component({
  selector: 'app-creator-products-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="studio-split">
      <section class="card pad">
        <h2>Publicar Novo Timbre (RF07)</h2>
        <form (ngSubmit)="publishProduct()">
          <div class="field">
            <label>Nome do Timbre *</label>
            <input class="input" [(ngModel)]="prodForm.title" name="title" required placeholder="Ex.: Fender Deluxe Reverb Clean Pack"/>
          </div>
          <div class="field">
            <label>Descrição Técnica *</label>
            <textarea class="textarea" [(ngModel)]="prodForm.description" name="description" required placeholder="Snapshots, cadeia de sinal, recomendações de saída FOH/FRFR..."></textarea>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>Preço (R$) *</label>
              <input type="number" step="0.10" min="1" class="input mono" [(ngModel)]="prodForm.price" name="price" required/>
            </div>
            <div class="field">
              <label>Tipo de Ativo *</label>
              <select class="select" [(ngModel)]="prodForm.productType" name="productType">
                <option value="PATCH">Preset / Patch</option>
                <option value="CAPTURE">Captura Neural</option>
                <option value="IR">Impulse Response (IR)</option>
              </select>
            </div>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>Gênero Musical *</label>
              <input class="input" [(ngModel)]="prodForm.genre" name="genre" required placeholder="Worship, Metal, Rock, Blues..."/>
            </div>
            <div class="field">
              <label>Tipo de Captação *</label>
              <select class="select" [(ngModel)]="prodForm.pickupType" name="pickupType">
                @for (p of pickupTypes; track p) {
                  <option [value]="p">{{ pickupLabels[p] }}</option>
                }
              </select>
            </div>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>Equipamento de Destino (Pedaleira) *</label>
              <input class="input" [(ngModel)]="prodForm.targetGear" name="targetGear" required placeholder="Line 6 Helix, Quad Cortex, Kemper..."/>
            </div>
            <div class="field">
              <label>Equipamento de Origem (Amp/Cab) *</label>
              <input class="input" [(ngModel)]="prodForm.sourceGear" name="sourceGear" required placeholder="Vox AC30, Marshall JCM800..."/>
            </div>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>Tags (separadas por vírgula)</label>
              <input class="input" [(ngModel)]="prodForm.tags" name="tags" placeholder="worship,clean,helix"/>
            </div>
            <div class="field">
              <label>Link de Vídeo YouTube (https://)</label>
              <input class="input" [(ngModel)]="prodForm.videoUrl" name="videoUrl" placeholder="https://youtube.com/watch?v=..."/>
            </div>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>Arquivo Binário do Patch (.hlx, .qcc, .kipr, .syx, .wav, .zip) *</label>
              <input type="file" class="input" (change)="onPatchFileChange($event)" required/>
              <span class="hint">Armazenado em cofre privado (RNF02).</span>
            </div>
            <div class="field">
              <label>Áudio de Demonstração (.wav / .mp3 / .flac / .ogg) *</label>
              <input type="file" accept="audio/*" class="input" (change)="onDemoFileChange($event)" required/>
              <span class="hint">Normalizado para -14 LUFS (RNF03) e comprimido anti-ripping (RNF04).</span>
            </div>
          </div>
          <button type="submit" class="btn btn-accent" [disabled]="saving()">
            {{ saving() ? 'Processando Áudio e Publicando...' : 'Publicar Timbre no Marketplace' }}
          </button>
        </form>
      </section>

      <section class="card pad">
        <h2>Meus Produtos & Versionamento (RF09)</h2>
        <div class="prod-list">
          @for (p of products(); track p.id) {
            <div class="prod-item" [class.inactive]="!p.active">
              <div class="pi-top">
                <div>
                  <a [routerLink]="['/tones', p.slug]" class="pi-title">{{ p.title }}</a>
                  @if (!p.active) {
                    <span class="badge badge-danger">PAUSADO</span>
                  }
                  <div class="mono muted">{{ p.productType }} · {{ p.targetGear }} · v{{ p.currentVersion }} · {{ p.salesCount }} vendas</div>
                </div>
                <strong class="mono">{{ fmtPrice(p.price) }}</strong>
              </div>
              <div class="btn-row">
                @if (p.productType !== 'BUNDLE') {
                  <button class="btn btn-outline btn-sm" (click)="openVersionModal(p)">
                    ↑ Nova Versão
                  </button>
                }
                <button class="btn btn-ghost btn-sm" (click)="toggleActive(p)">
                  {{ p.active ? 'Pausar anúncio' : 'Reativar anúncio' }}
                </button>
              </div>
            </div>
          }
        </div>

        @if (versionTarget(); as vt) {
          <div class="version-box">
            <h4>Nova versão para: {{ vt.title }} (atual: v{{ vt.currentVersion }})</h4>
            <form (ngSubmit)="submitNewVersion(vt.id)">
              <div class="field">
                <label>Número da Nova Versão (SemVer) *</label>
                <input class="input mono" [(ngModel)]="verForm.versionNumber" name="versionNumber" required placeholder="Ex.: 1.3.0"/>
              </div>
              <div class="field">
                <label>Changelog (exibido na notificação aos compradores)</label>
                <textarea class="textarea" [(ngModel)]="verForm.changelog" name="changelog" placeholder="Melhorias de EQ, compatibilidade com novo firmware..."></textarea>
              </div>
              <div class="field">
                <label>Novo Arquivo Binário *</label>
                <input type="file" class="input" (change)="onVersionFileChange($event)" required/>
              </div>
              <div class="btn-row">
                <button type="submit" class="btn btn-primary btn-sm" [disabled]="saving()">Lançar & Notificar</button>
                <button type="button" class="btn btn-ghost btn-sm" (click)="versionTarget.set(null)">Cancelar</button>
              </div>
            </form>
          </div>
        }
      </section>
    </div>
  `,
  styles: [`
    .pad { padding: 22px; }
    .grid-2 { display: grid; grid-template-columns: 1fr; gap: 12px; @media (min-width: 640px) { grid-template-columns: 1fr 1fr; } }
    .studio-split {
      display: grid;
      grid-template-columns: 1fr;
      gap: 20px;
      @media (min-width: 960px) { grid-template-columns: 1.15fr 0.85fr; }
    }
    .hint { font-size: 11.5px; color: var(--ink-muted); }
    .prod-list { display: flex; flex-direction: column; gap: 10px; }
    .prod-item {
      padding: 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      display: flex;
      flex-direction: column;
      gap: 10px;
      &.inactive { opacity: 0.7; background: var(--bg-subtle); }
    }
    .pi-top { display: flex; justify-content: space-between; gap: 8px; }
    .pi-title { font-weight: 600; font-size: 14px; margin-right: 6px; &:hover { color: var(--accent); } }
    .muted { color: var(--ink-muted); font-size: 12px; }
    .version-box {
      margin-top: 16px;
      padding: 16px;
      background: var(--bg-subtle);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
      h4 { margin: 0 0 12px; font-size: 14px; }
    }
    .btn-row { display: flex; flex-wrap: wrap; gap: 8px; }
  `]
})
export class CreatorProductsTabComponent {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly products = input<ProductCard[]>([]);
  readonly productsChanged = output<void>();

  readonly saving = signal(false);
  readonly versionTarget = signal<ProductCard | null>(null);
  readonly pickupLabels = PICKUP_LABELS;
  readonly pickupTypes: PickupType[] = ['SINGLE_COIL', 'HUMBUCKER', 'P90', 'ACTIVE', 'PIEZO', 'ANY'];
  readonly fmtPrice = formatBRL;

  prodForm = {
    title: '', description: '', price: 49.9, productType: 'PATCH' as ProductType,
    genre: 'Rock', targetGear: 'Line 6 Helix', sourceGear: 'Marshall JCM800',
    pickupType: 'HUMBUCKER' as PickupType, tags: '', videoUrl: ''
  };
  verForm = { versionNumber: '', changelog: '' };

  private patchFile: File | null = null;
  private demoFile: File | null = null;
  private versionFile: File | null = null;

  onPatchFileChange(event: Event): void {
    this.patchFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  onDemoFileChange(event: Event): void {
    this.demoFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  onVersionFileChange(event: Event): void {
    this.versionFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  publishProduct(): void {
    if (!this.patchFile || !this.demoFile) {
      this.toast.show('Selecione o arquivo do patch e o áudio de demonstração.', 'error');
      return;
    }
    const fd = new FormData();
    fd.append('title', this.prodForm.title);
    fd.append('description', this.prodForm.description);
    fd.append('price', String(this.prodForm.price));
    fd.append('productType', this.prodForm.productType);
    fd.append('genre', this.prodForm.genre);
    fd.append('targetGear', this.prodForm.targetGear);
    fd.append('sourceGear', this.prodForm.sourceGear);
    fd.append('pickupType', this.prodForm.pickupType);
    fd.append('tags', this.prodForm.tags);
    if (this.prodForm.videoUrl.trim()) fd.append('videoUrl', this.prodForm.videoUrl.trim());
    fd.append('patchFile', this.patchFile);
    fd.append('demoAudioFile', this.demoFile);

    this.saving.set(true);
    this.api.createProduct(fd).subscribe({
      next: created => {
        this.saving.set(false);
        this.toast.show(`Timbre "${created.title}" publicado com áudio normalizado em -14 LUFS!`, 'success');
        this.prodForm.title = '';
        this.prodForm.description = '';
        this.productsChanged.emit();
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao publicar timbre.', 'error');
      }
    });
  }

  openVersionModal(p: ProductCard): void {
    this.versionTarget.set(p);
    this.verForm = { versionNumber: '', changelog: '' };
    this.versionFile = null;
  }

  submitNewVersion(productId: string): void {
    if (!this.versionFile) {
      this.toast.show('Selecione o novo arquivo binário.', 'error');
      return;
    }
    const fd = new FormData();
    fd.append('versionNumber', this.verForm.versionNumber);
    fd.append('changelog', this.verForm.changelog);
    fd.append('patchFile', this.versionFile);

    this.saving.set(true);
    this.api.publishNewVersion(productId, fd).subscribe({
      next: v => {
        this.saving.set(false);
        this.versionTarget.set(null);
        this.toast.show(`Versão v${v.versionNumber} publicada e liberada aos compradores!`, 'success');
        this.productsChanged.emit();
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao publicar nova versão.', 'error');
      }
    });
  }

  toggleActive(p: ProductCard): void {
    const onDone = () => {
      this.toast.show(p.active ? `"${p.title}" pausado no catálogo.` : `"${p.title}" reativado no catálogo!`, 'success');
      this.productsChanged.emit();
    };
    const onErr = (err: { error?: { message?: string } }) =>
      this.toast.show(err.error?.message || 'Erro ao atualizar anúncio.', 'error');

    if (p.active) {
      this.api.deactivateProduct(p.id).subscribe({ next: onDone, error: onErr });
    } else {
      this.api.updateProduct(p.id, { active: true }).subscribe({ next: onDone, error: onErr });
    }
  }
}
