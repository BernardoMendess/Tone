import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BehaviorSubject, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';
import { ApiService, CatalogSearchParams } from '../../core/services/api.service';
import {
  FilterOptions,
  PICKUP_LABELS,
  PRODUCT_TYPE_LABELS,
  PageResponse,
  PickupType,
  ProductCard,
  ProductType
} from '../../core/models';
import { ToneCardComponent } from '../../shared/components/tone-card.component';

const EMPTY_PAGE: PageResponse<ProductCard> = {
  content: [],
  page: 0,
  size: 24,
  totalElements: 0,
  totalPages: 0
};

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

        <form class="search-bar" (ngSubmit)="applyFilters(true)">
          <input
            type="search"
            class="input search-input"
            [(ngModel)]="filters.q"
            (ngModelChange)="applyFilters(true)"
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
      <aside class="filter-sidebar card" [class.open]="mobileFiltersOpen()">
        <div class="filter-head">
          <h2>Filtros Cruzados</h2>
          @if (activeFilterCount() > 0) {
            <button class="clear-btn" (click)="clearFilters()">Limpar tudo</button>
          }
        </div>

        <div class="field">
          <label for="f-genre">Gênero Musical</label>
          <select id="f-genre" class="select" [(ngModel)]="filters.genre" (change)="applyFilters(true)">
            <option value="">Todos os gêneros</option>
            @for (g of filterOptions()?.genres ?? []; track g) {
              <option [value]="g">{{ g }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-target">Equipamento de Destino (Pedaleira)</label>
          <select id="f-target" class="select" [(ngModel)]="filters.targetGear" (change)="applyFilters(true)">
            <option value="">Todos os equipamentos</option>
            @for (t of filterOptions()?.targetGears ?? []; track t) {
              <option [value]="t">{{ t }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-source">Equipamento de Origem (Amp / Cab)</label>
          <select id="f-source" class="select" [(ngModel)]="filters.sourceGear" (change)="applyFilters(true)">
            <option value="">Todos os amplificadores</option>
            @for (s of filterOptions()?.sourceGears ?? []; track s) {
              <option [value]="s">{{ s }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-pickup">Tipo de Captação da Guitarra</label>
          <select id="f-pickup" class="select" [(ngModel)]="filters.pickupType" (change)="applyFilters(true)">
            <option value="">Qualquer captação</option>
            @for (p of pickupTypes; track p) {
              <option [value]="p">{{ pickupLabels[p] }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-type">Formato do Ativo</label>
          <select id="f-type" class="select" [(ngModel)]="filters.productType" (change)="applyFilters(true)">
            <option value="">Todos os formatos</option>
            @for (pt of productTypes; track pt) {
              <option [value]="pt">{{ productTypeLabels[pt] }}</option>
            }
          </select>
        </div>
      </aside>

      <section class="results-col">
        <div class="results-toolbar">
          <div class="results-count mono">
            {{ totalElements() }} {{ totalElements() === 1 ? 'timbre encontrado' : 'timbres encontrados' }}
          </div>

          <div class="sort-wrap">
            <label for="sort-sel">Ordenar:</label>
            <select id="sort-sel" class="select select-sm" [(ngModel)]="filters.sort" (change)="applyFilters(true)">
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

          @if (totalPages() > 1) {
            <div class="pager">
              <button class="btn btn-outline btn-sm" (click)="goToPage(currentPage() - 1)" [disabled]="currentPage() === 0">
                ← Anterior
              </button>
              <span class="mono pager-info">Página {{ currentPage() + 1 }} de {{ totalPages() }}</span>
              <button class="btn btn-outline btn-sm" (click)="goToPage(currentPage() + 1)" [disabled]="currentPage() + 1 >= totalPages()">
                Próxima →
              </button>
            </div>
          }
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
      font-size: clamp(22px, 3vw, 30px);
      line-height: 1.18;
      letter-spacing: -0.025em;
      max-width: 720px;
    }
    .hero-copy p {
      margin: 0;
      color: var(--ink-secondary);
      font-size: 14.5px;
      max-width: 680px;
    }
    .search-bar {
      display: flex;
      gap: 8px;
      max-width: 760px;
    }
    .search-input {
      flex: 1;
      height: 42px;
      font-size: 14px;
    }
    .mobile-filter-btn {
      display: inline-flex;
      @media (min-width: 960px) { display: none; }
    }

    .catalog-layout {
      display: grid;
      grid-template-columns: 1fr;
      gap: 22px;
      align-items: start;
      @media (min-width: 960px) {
        grid-template-columns: 260px 1fr;
      }
    }

    .filter-sidebar {
      padding: 18px;
      display: none;
      &.open { display: block; }
      @media (min-width: 960px) {
        display: block;
        position: sticky;
        top: 76px;
      }
    }
    .filter-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
      h2 { margin: 0; font-size: 14px; font-weight: 600; }
    }
    .clear-btn {
      background: transparent;
      border: none;
      color: var(--accent);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      &:hover { text-decoration: underline; }
    }

    .results-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 14px;
    }
    .results-count {
      font-size: 12px;
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
      font-size: 12.5px;
    }

    .tone-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 16px;
      @media (min-width: 640px) { grid-template-columns: repeat(2, 1fr); }
      @media (min-width: 1180px) { grid-template-columns: repeat(3, 1fr); }
    }

    .skeleton {
      height: 310px;
      background: linear-gradient(90deg, var(--bg-subtle) 25%, #ececee 50%, var(--bg-subtle) 75%);
      background-size: 200% 100%;
      animation: shimmer 1.2s infinite linear;
    }
    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
    .pager {
      margin-top: 22px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 14px;
    }
    .pager-info { font-size: 12px; color: var(--ink-muted); }
  `]
})
export class CatalogPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly products = signal<ProductCard[]>([]);
  readonly totalElements = signal(0);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
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
    sort: 'popular',
    page: 0,
    size: 24
  };

  private readonly filters$ = new BehaviorSubject<CatalogSearchParams>({ ...this.filters });

  ngOnInit(): void {
    this.api.getFilterOptions().subscribe(opts => this.filterOptions.set(opts));

    this.filters$.pipe(
      debounceTime(250),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      tap(() => this.loading.set(true)),
      switchMap(f => this.api.searchProducts(f).pipe(catchError(() => of(EMPTY_PAGE)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(page => {
      this.products.set(page.content);
      this.totalElements.set(page.totalElements);
      this.currentPage.set(page.page);
      this.totalPages.set(page.totalPages);
      this.loading.set(false);
    });
  }

  activeFilterCount(): number {
    return [this.filters.genre, this.filters.targetGear, this.filters.sourceGear, this.filters.pickupType, this.filters.productType]
      .filter(Boolean).length;
  }

  applyFilters(resetPage = false): void {
    if (resetPage) {
      this.filters.page = 0;
    }
    this.filters$.next({ ...this.filters });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.filters.page = page;
    this.filters$.next({ ...this.filters });
  }

  clearFilters(): void {
    this.filters = {
      q: '',
      genre: '',
      targetGear: '',
      sourceGear: '',
      pickupType: '',
      productType: '',
      sort: 'popular',
      page: 0,
      size: 24
    };
    this.applyFilters(true);
  }
}
