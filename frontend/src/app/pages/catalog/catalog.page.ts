import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, CatalogSearchParams } from '../../core/services/api.service';
import {
  FilterOptions,
  PICKUP_LABELS,
  PRODUCT_TYPE_LABELS,
  PickupType,
  ProductCard,
  ProductType
} from '../../core/models';
import { ToneCardComponent } from '../../shared/components/tone-card.component';

@Component({
  selector: 'app-catalog-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ToneCardComponent],
  template: `
    <!-- Minimalist Audio-First Hero Strip -->
    <section class="hero-strip">
      <div class="container hero-inner">
        <div class="hero-copy">
          <div class="eyebrow mono">MARKETPLACE DE TIMBRES DIGITAIS · -14 LUFS PADRONIZADO</div>
          <h1>Ouça o timbre antes de tocar. Baixe com entrega instantânea.</h1>
          <p>
            Capturas neurais, presets de pedaleiras e Impulse Responses de criadores curados —
            com demonstrações normalizadas, pagamento via PIX e biblioteca vitalícia segura.
          </p>
        </div>

        <form class="search-bar" (ngSubmit)="loadProducts()">
          <input
            type="search"
            class="input search-input"
            [(ngModel)]="filters.q"
            name="q"
            placeholder="Buscar por amplificador, pedaleira, gênero, tag ou criador..."
          />
          <button type="submit" class="btn btn-primary">Buscar</button>
          <button type="button" class="btn btn-outline mobile-filter-btn" (click)="mobileFiltersOpen.set(!mobileFiltersOpen())">
            Filtros
            @if (activeFilterCount() > 0) {
              <span class="mono">({{ activeFilterCount() }})</span>
            }
          </button>
        </form>
      </div>
    </section>

    <div class="container catalog-layout">
      <!-- Cross-Filter Sidebar (RF01) — collapsible on mobile (RNF06) -->
      <aside class="filter-sidebar card" [class.open]="mobileFiltersOpen()">
        <div class="filter-head">
          <h2>Filtros Cruzados</h2>
          @if (activeFilterCount() > 0) {
            <button class="clear-btn" (click)="clearFilters()">Limpar tudo</button>
          }
        </div>

        <div class="field">
          <label for="f-genre">Gênero Musical</label>
          <select id="f-genre" class="select" [(ngModel)]="filters.genre" (change)="loadProducts()">
            <option value="">Todos os gêneros</option>
            @for (g of filterOptions()?.genres ?? []; track g) {
              <option [value]="g">{{ g }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-target">Equipamento de Destino (Pedaleira)</label>
          <select id="f-target" class="select" [(ngModel)]="filters.targetGear" (change)="loadProducts()">
            <option value="">Todos os equipamentos</option>
            @for (t of filterOptions()?.targetGears ?? []; track t) {
              <option [value]="t">{{ t }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-source">Equipamento de Origem (Amp / Cab)</label>
          <select id="f-source" class="select" [(ngModel)]="filters.sourceGear" (change)="loadProducts()">
            <option value="">Todos os amplificadores</option>
            @for (s of filterOptions()?.sourceGears ?? []; track s) {
              <option [value]="s">{{ s }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-pickup">Tipo de Captação da Guitarra</label>
          <select id="f-pickup" class="select" [(ngModel)]="filters.pickupType" (change)="loadProducts()">
            <option value="">Qualquer captação</option>
            @for (p of pickupTypes; track p) {
              <option [value]="p">{{ pickupLabels[p] }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-type">Formato do Ativo</label>
          <select id="f-type" class="select" [(ngModel)]="filters.productType" (change)="loadProducts()">
            <option value="">Todos os formatos</option>
            @for (pt of productTypes; track pt) {
              <option [value]="pt">{{ productTypeLabels[pt] }}</option>
            }
          </select>
        </div>
      </aside>

      <!-- Results Grid -->
      <section class="results-col">
        <div class="results-toolbar">
          <div class="results-count mono">
            {{ products().length }} {{ products().length === 1 ? 'timbre encontrado' : 'timbres encontrados' }}
          </div>

          <div class="sort-wrap">
            <label for="sort-sel">Ordenar:</label>
            <select id="sort-sel" class="select select-sm" [(ngModel)]="filters.sort" (change)="loadProducts()">
              <option value="popular">Mais vendidos</option>
              <option value="newest">Mais recentes</option>
              <option value="rating">Melhor avaliados</option>
              <option value="price_asc">Menor preço</option>
              <option value="price_desc">Maior preço</option>
            </select>
          </div>
        </div>

        @if (loading()) {
          <div class="tone-grid">
            @for (s of [1,2,3,4,5,6]; track s) {
              <div class="skeleton card"></div>
            }
          </div>
        } @else if (products().length === 0) {
          <div class="empty-state">
            <h3>Nenhum timbre encontrado com esses filtros</h3>
            <p>Experimente remover um dos critérios cruzados ou limpar a busca.</p>
            <button class="btn btn-outline" (click)="clearFilters()">Limpar filtros</button>
          </div>
        } @else {
          <div class="tone-grid">
            @for (p of products(); track p.id) {
              <app-tone-card [product]="p" [siblingProducts]="products()"></app-tone-card>
            }
          </div>
        }
      </section>
    </div>
  `,
  styles: [`
    .hero-strip {
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border);
      padding: 28px 0 24px;
      margin-bottom: 24px;
    }
    .hero-inner {
      display: flex;
      flex-direction: column;
      gap: 18px;
    }
    .eyebrow {
      font-size: 11px;
      letter-spacing: 0.06em;
      color: var(--accent);
      font-weight: 600;
      margin-bottom: 8px;
    }
    .hero-copy h1 {
      margin: 0 0 8px;
      font-size: 24px;
      line-height: 1.2;
      letter-spacing: -0.03em;
      font-weight: 600;
      @media (min-width: 768px) { font-size: 30px; }
    }
    .hero-copy p {
      margin: 0;
      color: var(--ink-muted);
      max-width: 680px;
      font-size: 14.5px;
    }
    .search-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      max-width: 720px;
    }
    .search-input { flex: 1; min-width: 220px; }
    .mobile-filter-btn {
      @media (min-width: 960px) { display: none; }
    }

    .catalog-layout {
      display: grid;
      grid-template-columns: 1fr;
      gap: 20px;
      align-items: start;
      @media (min-width: 960px) {
        grid-template-columns: 265px 1fr;
        gap: 24px;
      }
    }
    .filter-sidebar {
      display: none;
      padding: 18px;
      @media (min-width: 960px) {
        display: block;
        position: sticky;
        top: 76px;
      }
      &.open { display: block; }
    }
    .filter-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 10px;
      border-bottom: 1px solid var(--border);
      h2 { margin: 0; font-size: 14px; font-weight: 600; }
    }
    .clear-btn {
      background: none;
      border: none;
      color: var(--accent);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
    }

    .results-toolbar {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
    }
    .results-count {
      font-size: 12.5px;
      color: var(--ink-muted);
    }
    .sort-wrap {
      display: flex;
      align-items: center;
      gap: 8px;
      label { font-size: 12.5px; color: var(--ink-muted); }
    }
    .select-sm {
      width: auto;
      padding: 6px 10px;
      font-size: 13px;
    }

    .tone-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 16px;
      @media (min-width: 640px) {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      @media (min-width: 1160px) {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    .skeleton {
      height: 270px;
      background: linear-gradient(90deg, #f4f4f6 25%, #ececef 50%, #f4f4f6 75%);
    }
  `]
})
export class CatalogPage implements OnInit {
  private readonly api = inject(ApiService);

  readonly products = signal<ProductCard[]>([]);
  readonly filterOptions = signal<FilterOptions | null>(null);
  readonly loading = signal(true);
  readonly mobileFiltersOpen = signal(false);

  readonly pickupLabels = PICKUP_LABELS;
  readonly productTypeLabels = PRODUCT_TYPE_LABELS;
  readonly pickupTypes: PickupType[] = ['SINGLE_COIL', 'HUMBUCKER', 'P90', 'ACTIVE', 'PIEZO', 'ANY'];
  readonly productTypes: ProductType[] = ['PATCH', 'CAPTURE', 'IR', 'BUNDLE'];

  filters: CatalogSearchParams = {
    q: '',
    genre: '',
    targetGear: '',
    sourceGear: '',
    pickupType: '',
    productType: '',
    sort: 'popular'
  };

  ngOnInit(): void {
    this.api.getFilterOptions().subscribe(opts => this.filterOptions.set(opts));
    this.loadProducts();
  }

  activeFilterCount(): number {
    return [this.filters.genre, this.filters.targetGear, this.filters.sourceGear, this.filters.pickupType, this.filters.productType]
      .filter(Boolean).length;
  }

  loadProducts(): void {
    this.loading.set(true);
    this.api.searchProducts(this.filters).subscribe({
      next: list => {
        this.products.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  clearFilters(): void {
    this.filters = {
      q: '',
      genre: '',
      targetGear: '',
      sourceGear: '',
      pickupType: '',
      productType: '',
      sort: 'popular'
    };
    this.loadProducts();
  }
}
