import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { ProductCard, formatBRL } from '../../core/models';

@Component({
  selector: 'app-creator-bundles-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card pad narrow">
      <h2>Criar Pacote Promocional — Bundle (RF08)</h2>
      <p class="muted-p">Agrupe 2 ou mais dos seus timbres individuais ativos sob um único produto com preço promocional.</p>

      <form (ngSubmit)="publishBundle()">
        <div class="field">
          <label>Nome do Pacote *</label>
          <input class="input" [(ngModel)]="bundleForm.title" name="bTitle" required placeholder="Ex.: Pack Completo Estúdio 2026"/>
        </div>
        <div class="field">
          <label>Descrição do Pacote *</label>
          <textarea class="textarea" [(ngModel)]="bundleForm.description" name="bDesc" required></textarea>
        </div>

        <div class="field">
          <label>Selecione os timbres incluídos (mínimo 2):</label>
          <div class="check-list">
            @for (p of individualProducts(); track p.id) {
              <label class="check-row">
                <input type="checkbox" [checked]="selectedIds().has(p.id)" (change)="toggleItem(p.id)"/>
                <span>{{ p.title }} <span class="mono muted">({{ p.targetGear }})</span></span>
                <strong class="mono">{{ fmtPrice(p.price) }}</strong>
              </label>
            }
          </div>
        </div>

        <div class="bundle-math mono">
          Soma individual: <strong>{{ fmtPrice(bundleSum()) }}</strong>
          @if (bundleDiscount() > 0) {
            · Desconto exibido ao comprador: <span class="badge badge-success">-{{ bundleDiscount() }}%</span>
          }
        </div>

        <div class="field">
          <label>Preço Promocional do Pacote (R$) *</label>
          <input type="number" step="0.10" min="5" class="input mono" [(ngModel)]="bundleForm.promotionalPrice" name="bPrice" required/>
        </div>

        <button type="submit" class="btn btn-accent" [disabled]="saving() || selectedIds().size < 2">
          Publicar Pacote Promocional
        </button>
      </form>
    </section>
  `,
  styles: [`
    .pad { padding: 22px; }
    .narrow { max-width: 720px; }
    .muted-p { color: var(--ink-muted); font-size: 14px; margin: 0 0 18px; }
    .muted { color: var(--ink-muted); font-size: 12px; }
    .check-list { display: flex; flex-direction: column; gap: 8px; margin-top: 6px; }
    .check-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      cursor: pointer;
      span { flex: 1; font-size: 13.5px; }
    }
    .bundle-math {
      margin: 12px 0 16px;
      padding: 10px 12px;
      background: var(--bg-subtle);
      border-radius: var(--radius-sm);
      font-size: 13px;
    }
  `]
})
export class CreatorBundlesTabComponent {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly products = input<ProductCard[]>([]);
  readonly bundleCreated = output<void>();

  readonly saving = signal(false);
  readonly selectedIds = signal<Set<string>>(new Set());
  readonly fmtPrice = formatBRL;

  readonly individualProducts = computed(() =>
    this.products().filter(p => p.productType !== 'BUNDLE' && p.active)
  );
  readonly bundleSum = computed(() => {
    const ids = this.selectedIds();
    return this.individualProducts().filter(p => ids.has(p.id)).reduce((s, p) => s + p.price, 0);
  });

  bundleForm = { title: '', description: '', promotionalPrice: 89.9 };

  toggleItem(productId: string): void {
    const next = new Set(this.selectedIds());
    if (next.has(productId)) next.delete(productId);
    else next.add(productId);
    this.selectedIds.set(next);
  }

  bundleDiscount(): number {
    const sum = this.bundleSum();
    if (sum <= 0 || this.bundleForm.promotionalPrice >= sum) return 0;
    return Math.round(((sum - this.bundleForm.promotionalPrice) / sum) * 100);
  }

  publishBundle(): void {
    this.saving.set(true);
    this.api.createBundle({
      title: this.bundleForm.title,
      description: this.bundleForm.description,
      promotionalPrice: Number(this.bundleForm.promotionalPrice),
      includedProductIds: Array.from(this.selectedIds())
    }).subscribe({
      next: b => {
        this.saving.set(false);
        this.selectedIds.set(new Set());
        this.bundleForm = { title: '', description: '', promotionalPrice: 89.9 };
        this.toast.show(`Pacote "${b.title}" criado com sucesso!`, 'success');
        this.bundleCreated.emit();
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao criar pacote.', 'error');
      }
    });
  }
}
