import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import {
  PICKUP_LABELS,
  PRODUCT_TYPE_LABELS,
  ProductCard,
  formatBRL,
  formatDuration
} from '../../core/models';
import { AudioPlayerService, AudioTrack } from '../../core/services/audio-player.service';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { WishlistService } from '../../core/services/wishlist.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-tone-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <article class="tone-card card" [class.playing]="player.isTrackActive(product.id)">
      <!-- Top Audio Strip (RF02 + RNF01: Audio First) -->
      <div class="audio-strip">
        <button
          class="play-trigger"
          [class.active]="player.isTrackPlaying(product.id)"
          [disabled]="!product.demoAudioUrl"
          (click)="onPlay()"
          [attr.aria-label]="player.isTrackPlaying(product.id) ? 'Pausar demo' : 'Ouvir demo'"
        >
          @if (player.isTrackPlaying(product.id)) {
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
          } @else {
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,4 20,12 6,20"/></svg>
          }
        </button>

        <!-- Minimalist static/live waveform preview -->
        <div class="wave-bars" (click)="onPlay()" title="Clique para ouvir demonstração">
          @for (h of barHeights; track $index) {
            <span
              class="bar"
              [style.height.%]="h"
              [class.lit]="isBarLit($index)"
              [class.anim]="player.isTrackPlaying(product.id)"
              [style.animation-delay.ms]="$index * 45"
            ></span>
          }
        </div>

        <span class="dur mono">{{ fmtDur(product.demoDurationSeconds) }}</span>

        <button
          class="wish-btn"
          [class.saved]="wishlist.ids().has(product.id)"
          (click)="onToggleWishlist()"
          [title]="wishlist.ids().has(product.id) ? 'Remover dos favoritos' : 'Salvar nos favoritos'"
          aria-label="Favoritar"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" [attr.fill]="wishlist.ids().has(product.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.8">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
          </svg>
        </button>
      </div>

      <!-- Content & Technical Rig Specs -->
      <div class="card-body">
        <div class="meta-row">
          <span class="badge" [class.badge-accent]="product.productType === 'BUNDLE'">
            {{ typeLabels[product.productType] }}
            @if (product.productType === 'BUNDLE' && product.includedCount > 0) {
              · {{ product.includedCount }} itens
            }
          </span>
          <span class="badge">{{ product.genre }}</span>
          @if (product.discountPercent) {
            <span class="badge badge-success">-{{ product.discountPercent }}%</span>
          }
        </div>

        <a [routerLink]="['/tones', product.slug]" class="tone-title">{{ product.title }}</a>

        <div class="creator-line">
          por <a [routerLink]="['/creators', product.creator.slug]" class="creator-link">{{ product.creator.displayName }}</a>
          <span class="sep">·</span>
          <span class="mono">v{{ product.currentVersion }}</span>
        </div>

        <dl class="spec-grid mono">
          <div>
            <dt>Destino</dt>
            <dd>{{ product.targetGear }}</dd>
          </div>
          <div>
            <dt>Origem</dt>
            <dd>{{ product.sourceGear }}</dd>
          </div>
          <div>
            <dt>Captação</dt>
            <dd>{{ pickupLabels[product.pickupType] }}</dd>
          </div>
        </dl>
      </div>

      <!-- Footer: Rating + Price + CTA -->
      <div class="card-footer">
        <div class="price-block">
          @if (product.originalBundlePrice) {
            <span class="old-price mono">{{ fmtPrice(product.originalBundlePrice) }}</span>
          }
          <span class="price mono">{{ fmtPrice(product.price) }}</span>
          @if (product.reviewCount > 0) {
            <span class="rating mono">★ {{ product.averageRating | number:'1.1-1' }} ({{ product.reviewCount }})</span>
          }
        </div>

        <div class="footer-actions">
          @if (cart.productIds().has(product.id)) {
            <a routerLink="/checkout" class="btn btn-outline btn-sm">No Carrinho</a>
          } @else {
            <button class="btn btn-primary btn-sm" (click)="onAddToCart()">+ Carrinho</button>
          }
        </div>
      </div>
    </article>
  `,
  styles: [`
    .tone-card {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
      &:hover { border-color: var(--border-strong); box-shadow: var(--shadow-md); }
      &.playing { border-color: var(--accent); }
    }
    .audio-strip {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 14px;
      background: var(--bg-subtle);
      border-bottom: 1px solid var(--border);
    }
    .play-trigger {
      width: 38px;
      height: 38px;
      border-radius: 50%;
      border: none;
      background: var(--bg-dark);
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      cursor: pointer;
      transition: background 0.15s;
      &:hover:not(:disabled) { background: var(--accent); }
      &.active { background: var(--accent); }
    }
    .wave-bars {
      flex: 1;
      height: 28px;
      display: flex;
      align-items: center;
      gap: 2.5px;
      cursor: pointer;
    }
    .bar {
      flex: 1;
      background: #d4d4d8;
      border-radius: 2px;
      min-height: 4px;
      transition: background 0.15s;
      &.lit { background: var(--accent); }
      &.anim { animation: eqBounce 0.85s ease-in-out infinite; }
    }
    .dur {
      font-size: 11px;
      color: var(--ink-muted);
      flex-shrink: 0;
    }
    .wish-btn {
      width: 30px;
      height: 30px;
      border: none;
      background: transparent;
      color: var(--ink-muted);
      border-radius: 6px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      &:hover { background: var(--border); color: var(--ink); }
      &.saved { color: var(--accent); }
    }

    .card-body {
      padding: 14px;
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .meta-row {
      display: flex;
      flex-wrap: wrap;
      gap: 5px;
    }
    .tone-title {
      font-size: 15.5px;
      font-weight: 600;
      color: var(--ink);
      letter-spacing: -0.015em;
      line-height: 1.3;
      &:hover { color: var(--accent); }
    }
    .creator-line {
      font-size: 12.5px;
      color: var(--ink-muted);
      .creator-link {
        color: var(--ink-secondary);
        font-weight: 500;
        &:hover { text-decoration: underline; }
      }
      .sep { margin: 0 5px; }
    }
    .spec-grid {
      margin: 6px 0 0;
      padding-top: 10px;
      border-top: 1px solid var(--border);
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 8px;
      font-size: 11px;
      dt {
        color: var(--ink-muted);
        text-transform: uppercase;
        font-size: 9.5px;
        letter-spacing: 0.04em;
        margin-bottom: 2px;
      }
      dd {
        margin: 0;
        color: var(--ink-secondary);
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .card-footer {
      padding: 12px 14px;
      border-top: 1px solid var(--border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      background: var(--bg-surface);
    }
    .price-block {
      display: flex;
      flex-direction: column;
      line-height: 1.2;
    }
    .old-price {
      font-size: 11px;
      color: var(--ink-muted);
      text-decoration: line-through;
    }
    .price {
      font-size: 16px;
      font-weight: 600;
      color: var(--ink);
    }
    .rating {
      font-size: 11px;
      color: var(--ink-muted);
      margin-top: 2px;
    }
  `]
})
export class ToneCardComponent {
  @Input({ required: true }) product!: ProductCard;
  @Input() siblingProducts: ProductCard[] = [];

  readonly player = inject(AudioPlayerService);
  readonly cart = inject(CartService);
  readonly wishlist = inject(WishlistService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly pickupLabels = PICKUP_LABELS;
  readonly typeLabels = PRODUCT_TYPE_LABELS;
  readonly fmtPrice = formatBRL;
  readonly fmtDur = formatDuration;

  readonly barHeights = [26, 48, 72, 42, 85, 60, 95, 55, 78, 38, 66, 88, 52, 74, 44, 62, 34, 48];

  isBarLit(index: number): boolean {
    if (!this.player.isTrackActive(this.product.id)) return false;
    const pct = this.player.progressPercent();
    return ((index + 1) / this.barHeights.length) * 100 <= pct;
  }

  onPlay(): void {
    if (!this.product.demoAudioUrl) return;
    const toTrack = (p: ProductCard): AudioTrack => ({
      id: p.id,
      slug: p.slug,
      title: p.title,
      creatorName: p.creator.displayName,
      creatorSlug: p.creator.slug,
      targetGear: p.targetGear,
      sourceGear: p.sourceGear,
      price: p.price,
      demoAudioUrl: p.demoAudioUrl!,
      loudnessLufs: p.demoLoudnessLufs,
      bitrateKbps: p.demoBitrateKbps
    });
    const queue = this.siblingProducts.filter(p => !!p.demoAudioUrl).map(toTrack);
    this.player.playTrack(toTrack(this.product), queue.length > 0 ? queue : undefined);
  }

  onAddToCart(): void {
    if (!this.auth.isLoggedIn()) {
      this.toast.show('Faça login para adicionar timbres ao carrinho.');
      this.router.navigate(['/auth']);
      return;
    }
    this.cart.add(this.product.id).subscribe({
      next: () => this.toast.show(`"${this.product.title}" adicionado ao carrinho.`, 'success'),
      error: err => this.toast.show(err.error?.message || 'Não foi possível adicionar ao carrinho.', 'error')
    });
  }

  onToggleWishlist(): void {
    if (!this.auth.isLoggedIn()) {
      this.toast.show('Faça login para salvar favoritos.');
      this.router.navigate(['/auth']);
      return;
    }
    this.wishlist.toggle(this.product.id).subscribe({
      next: res => this.toast.show(
        res.wishlisted ? 'Salvo na sua lista de favoritos.' : 'Removido dos favoritos.',
        'info'
      ),
      error: () => this.toast.show('Erro ao atualizar favoritos.', 'error')
    });
  }
}
