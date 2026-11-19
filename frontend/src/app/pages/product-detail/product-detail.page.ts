import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ApiService } from '../../core/services/api.service';
import { AudioPlayerService } from '../../core/services/audio-player.service';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { WishlistService } from '../../core/services/wishlist.service';
import { ToastService } from '../../core/services/toast.service';
import {
  PICKUP_LABELS,
  PRODUCT_TYPE_LABELS,
  ProductDetail,
  formatBRL,
  formatDuration
} from '../../core/models';

@Component({
  selector: 'app-product-detail-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="container detail-wrap">
      <a routerLink="/" class="back-link">← Voltar ao catálogo</a>

      @if (loading()) {
        <div class="card pad">Carregando especificações do timbre...</div>
      } @else {
        @if (detail(); as d) {
        <div class="detail-grid">
          <!-- Main column: Audio Player Deck + Rig Specs + Bundle + Versions + Reviews -->
          <div class="main-col">
            <!-- Audio Preview Deck (RF02 + RNF03 + RNF04) -->
            <section class="audio-deck card">
              <div class="deck-top">
                <div class="badges">
                  <span class="badge" [class.badge-accent]="d.product.productType === 'BUNDLE'">
                    {{ typeLabels[d.product.productType] }}
                  </span>
                  <span class="badge">{{ d.product.genre }}</span>
                  <span class="badge mono">v{{ d.product.currentVersion }}</span>
                  @if (d.product.discountPercent) {
                    <span class="badge badge-success">Economize {{ d.product.discountPercent }}%</span>
                  }
                </div>
                <h1>{{ d.product.title }}</h1>
                <div class="creator-byline">
                  Criado por <a [routerLink]="['/creators', d.product.creator.slug]">{{ d.product.creator.displayName }}</a>
                  @if (d.product.reviewCount > 0) {
                    <span> · ★ {{ d.product.averageRating | number:'1.1-1' }} ({{ d.product.reviewCount }} avaliações)</span>
                  }
                </div>
              </div>

              <div class="deck-player">
                <button
                  class="deck-play-btn"
                  [class.active]="player.isTrackPlaying(d.product.id)"
                  (click)="playMain()"
                >
                  @if (player.isTrackPlaying(d.product.id)) {
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
                  } @else {
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,4 20,12 6,20"/></svg>
                  }
                </button>

                <div class="deck-wave" (click)="seekOnDeck($event)">
                  @for (h of bars; track $index) {
                    <span
                      class="wbar"
                      [style.height.%]="h"
                      [class.lit]="isBarLit(d.product.id, $index)"
                    ></span>
                  }
                </div>

                <div class="deck-meta mono">
                  <div>
                    {{ player.isTrackActive(d.product.id) ? fmtDur(player.currentTime()) : '0:00' }} / {{ fmtDur(d.product.demoDurationSeconds) }}
                  </div>
                  <div class="lufs-note">{{ d.product.demoLoudnessLufs }} LUFS · Preview protegido ({{ d.product.demoBitrateKbps }}k)</div>
                </div>
              </div>
            </section>

            <!-- Technical Signal Chain Specs (RNF01) -->
            <section class="card pad">
              <h2>Especificações Técnicas do Rig</h2>
              <div class="rig-grid mono">
                <div class="rig-cell">
                  <span class="label">Equipamento de Destino</span>
                  <strong>{{ d.product.targetGear }}</strong>
                </div>
                <div class="rig-cell">
                  <span class="label">Amplificador / Origem</span>
                  <strong>{{ d.product.sourceGear }}</strong>
                </div>
                <div class="rig-cell">
                  <span class="label">Captação Recomendada</span>
                  <strong>{{ pickupLabels[d.product.pickupType] }}</strong>
                </div>
                <div class="rig-cell">
                  <span class="label">Gênero Musical</span>
                  <strong>{{ d.product.genre }}</strong>
                </div>
              </div>

              <div class="desc-block">
                <h3>Sobre o Timbre</h3>
                <p>{{ d.product.description }}</p>
              </div>

              @if (d.product.tags.length > 0) {
                <div class="tag-row">
                  @for (t of d.product.tags; track t) {
                    <span class="badge">#{{ t }}</span>
                  }
                </div>
              }
            </section>

            <!-- Optional Video Demo (RF07) -->
            @if (embedVideoUrl(); as vUrl) {
              <section class="card pad">
                <h2>Demonstração em Vídeo</h2>
                <div class="video-frame">
                  <iframe [src]="vUrl" frameborder="0" allowfullscreen title="Vídeo de demonstração"></iframe>
                </div>
              </section>
            } @else if (d.videoUrl) {
              <section class="card pad">
                <h2>Demonstração em Vídeo</h2>
                <a [href]="d.videoUrl" target="_blank" rel="noopener" class="btn btn-outline">
                  Abrir vídeo de demonstração externo ↗
                </a>
              </section>
            }

            <!-- Included Bundle Items (RF08) -->
            @if (d.bundleItems.length > 0) {
              <section class="card pad">
                <h2>Patches Incluídos neste Pacote ({{ d.bundleItems.length }})</h2>
                <div class="bundle-list">
                  @for (item of d.bundleItems; track item.id) {
                    <div class="bundle-row">
                      <button class="mini-play" (click)="playSubItem(item)">
                        {{ player.isTrackPlaying(item.id) ? '❚❚' : '▶' }}
                      </button>
                      <div class="bundle-info">
                        <a [routerLink]="['/tones', item.slug]" class="b-title">{{ item.title }}</a>
                        <div class="b-sub mono">{{ item.targetGear }} · {{ item.sourceGear }} · v{{ item.currentVersion }}</div>
                      </div>
                      <span class="mono muted">{{ fmtPrice(item.price) }}</span>
                    </div>
                  }
                </div>
              </section>
            }

            <!-- Version History & Changelog (RF09) -->
            @if (d.versions.length > 0) {
              <section class="card pad">
                <h2>Histórico de Versões do Patch</h2>
                <div class="version-list">
                  @for (v of d.versions; track v.id) {
                    <div class="version-item">
                      <div class="v-head">
                        <span class="badge badge-dark">v{{ v.versionNumber }}</span>
                        <span class="mono v-file">{{ v.originalFilename }}</span>
                        <span class="mono muted">{{ (v.fileSizeBytes / 1024) | number:'1.1-1' }} KB</span>
                      </div>
                      <p class="v-log">{{ v.changelog }}</p>
                    </div>
                  }
                </div>
              </section>
            }

            <!-- Verified Purchase Reviews (RF05) -->
            <section class="card pad">
              <div class="rev-head">
                <h2>Avaliações de Compradores Verificados ({{ d.reviews.length }})</h2>
              </div>

              @if (d.canReview) {
                <form class="review-form" (ngSubmit)="submitReview(d.product.id)">
                  <div class="star-picker">
                    <span>Sua nota:</span>
                    @for (s of [1,2,3,4,5]; track s) {
                      <button type="button" class="star-btn" [class.on]="s <= reviewRating" (click)="reviewRating = s">★</button>
                    }
                  </div>
                  <textarea
                    class="textarea"
                    [(ngModel)]="reviewComment"
                    name="comment"
                    placeholder="Compartilhe como este timbre se comportou no seu rig..."
                  ></textarea>
                  <button type="submit" class="btn btn-primary btn-sm" [disabled]="submittingReview()">
                    {{ submittingReview() ? 'Publicando...' : 'Publicar Avaliação Verificada' }}
                  </button>
                </form>
              } @else {
                <div class="verified-note">
                  Apenas compradores com compra verificada deste timbre podem publicar avaliações (RF05).
                </div>
              }

              @if (d.reviews.length === 0) {
                <p class="muted">Ainda não há avaliações para este timbre.</p>
              } @else {
                <div class="reviews-list">
                  @for (r of d.reviews; track r.id) {
                    <article class="review-card">
                      <div class="rc-top">
                        <strong>{{ r.userName }}</strong>
                        <span class="badge badge-success">Compra verificada</span>
                        <span class="mono stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5 - r.rating) }}</span>
                      </div>
                      <p>{{ r.comment }}</p>
                    </article>
                  }
                </div>
              }
            </section>
          </div>

          <!-- Purchase Sticky Sidebar -->
          <aside class="buy-sidebar card pad">
            @if (d.product.originalBundlePrice) {
              <div class="old-p mono">De {{ fmtPrice(d.product.originalBundlePrice) }}</div>
            }
            <div class="buy-price mono">{{ fmtPrice(d.product.price) }}</div>
            <p class="pix-hint">Pagamento instantâneo via PIX ou Cartão · Entrega imediata</p>

            @if (d.ownedByCurrentUser) {
              <a routerLink="/library" class="btn btn-accent btn-lg full">
                Acessar na Biblioteca Digital
              </a>
            } @else {
              <div class="buy-actions">
                <button class="btn btn-accent btn-lg full" (click)="buyNow(d.product.id)">
                  Comprar Agora (PIX)
                </button>
                <button class="btn btn-outline full" (click)="addToCart(d.product.id)">
                  {{ cart.productIds().has(d.product.id) ? 'Já está no Carrinho' : 'Adicionar ao Carrinho' }}
                </button>
              </div>
            }

            <button class="btn btn-ghost full wish-action" (click)="toggleWish(d.product.id)">
              {{ wishlist.ids().has(d.product.id) ? '♥ Salvo nos Favoritos' : '♡ Salvar nos Favoritos' }}
            </button>

            <ul class="guarantees">
              <li>Download vitalício via URL temporária segura (S3 Presigned)</li>
              <li>Atualizações futuras do criador liberadas gratuitamente</li>
              <li>Arquivo binário original sem compressão</li>
            </ul>
          </aside>
        </div>
        }
      }
    </div>
  `,
  styles: [`
    .detail-wrap { padding-top: 22px; }
    .back-link {
      display: inline-block;
      font-size: 13px;
      color: var(--ink-muted);
      margin-bottom: 16px;
      &:hover { color: var(--ink); }
    }
    .detail-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 20px;
      align-items: start;
      @media (min-width: 960px) {
        grid-template-columns: 1fr 330px;
        gap: 24px;
      }
    }
    .main-col { display: flex; flex-direction: column; gap: 18px; }
    .pad { padding: 20px; }

    .audio-deck { overflow: hidden; }
    .deck-top {
      padding: 20px;
      h1 { margin: 10px 0 6px; font-size: 24px; letter-spacing: -0.025em; }
    }
    .badges { display: flex; flex-wrap: wrap; gap: 6px; }
    .creator-byline {
      font-size: 13.5px;
      color: var(--ink-muted);
      a { color: var(--ink); font-weight: 500; text-decoration: underline; }
    }
    .deck-player {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 14px;
      padding: 16px 20px;
      background: var(--bg-dark);
      color: #fff;
    }
    .deck-play-btn {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      border: none;
      background: var(--accent);
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      flex-shrink: 0;
      &:hover { background: var(--accent-hover); }
    }
    .deck-wave {
      flex: 1;
      min-width: 180px;
      height: 44px;
      display: flex;
      align-items: center;
      gap: 3px;
      cursor: pointer;
    }
    .wbar {
      flex: 1;
      background: #3f3f46;
      border-radius: 2px;
      min-height: 5px;
      &.lit { background: var(--accent); }
    }
    .deck-meta {
      font-size: 12px;
      text-align: right;
      color: #e4e4e7;
    }
    .lufs-note { font-size: 10.5px; color: #a1a1aa; margin-top: 2px; }

    h2 { margin: 0 0 14px; font-size: 16px; font-weight: 600; }
    h3 { margin: 16px 0 6px; font-size: 14px; font-weight: 600; }

    .rig-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }
    .rig-cell {
      padding: 12px;
      background: var(--bg-subtle);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      .label { display: block; font-size: 10.5px; color: var(--ink-muted); text-transform: uppercase; margin-bottom: 4px; }
      strong { font-size: 13px; color: var(--ink); }
    }
    .desc-block p { margin: 0; color: var(--ink-secondary); white-space: pre-line; }
    .tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 14px; }

    .video-frame {
      position: relative;
      padding-bottom: 56.25%;
      height: 0;
      border-radius: var(--radius-sm);
      overflow: hidden;
      background: #000;
      iframe { position: absolute; inset: 0; width: 100%; height: 100%; }
    }

    .bundle-list { display: flex; flex-direction: column; gap: 8px; }
    .bundle-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }
    .mini-play {
      width: 30px;
      height: 30px;
      border-radius: 50%;
      border: 1px solid var(--border-strong);
      background: var(--bg-surface);
      cursor: pointer;
      font-size: 11px;
      &:hover { background: var(--bg-dark); color: #fff; }
    }
    .bundle-info { flex: 1; min-width: 0; }
    .b-title { font-weight: 600; font-size: 13.5px; &:hover { color: var(--accent); } }
    .b-sub { font-size: 11.5px; color: var(--ink-muted); }

    .version-list { display: flex; flex-direction: column; gap: 12px; }
    .version-item {
      padding-bottom: 12px;
      border-bottom: 1px solid var(--border);
      &:last-child { border-bottom: none; padding-bottom: 0; }
    }
    .v-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 6px; }
    .v-file { font-size: 12.5px; font-weight: 500; }
    .v-log { margin: 0; font-size: 13.5px; color: var(--ink-secondary); }

    .review-form {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 14px;
      background: var(--bg-subtle);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      margin-bottom: 18px;
    }
    .star-picker { display: flex; align-items: center; gap: 4px; font-size: 13px; }
    .star-btn {
      background: none;
      border: none;
      font-size: 22px;
      color: #d4d4d8;
      cursor: pointer;
      padding: 0 2px;
      &.on { color: var(--accent); }
    }
    .verified-note {
      font-size: 13px;
      color: var(--ink-muted);
      padding: 10px 12px;
      background: var(--bg-subtle);
      border-radius: var(--radius-sm);
      margin-bottom: 16px;
    }
    .reviews-list { display: flex; flex-direction: column; gap: 12px; }
    .review-card {
      padding: 12px 14px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      p { margin: 8px 0 0; font-size: 13.5px; color: var(--ink-secondary); }
    }
    .rc-top { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; font-size: 13px; }
    .stars { color: var(--accent); margin-left: auto; }

    .buy-sidebar {
      @media (min-width: 960px) { position: sticky; top: 76px; }
    }
    .old-p { font-size: 13px; color: var(--ink-muted); text-decoration: line-through; }
    .buy-price { font-size: 28px; font-weight: 600; margin-bottom: 4px; }
    .pix-hint { margin: 0 0 16px; font-size: 12.5px; color: var(--ink-muted); }
    .buy-actions { display: flex; flex-direction: column; gap: 8px; }
    .full { width: 100%; }
    .wish-action { margin-top: 8px; }
    .guarantees {
      margin: 18px 0 0;
      padding: 16px 0 0 18px;
      border-top: 1px solid var(--border);
      font-size: 12.5px;
      color: var(--ink-secondary);
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .muted { color: var(--ink-muted); font-size: 12.5px; }
  `]
})
export class ProductDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly player = inject(AudioPlayerService);
  readonly cart = inject(CartService);
  readonly wishlist = inject(WishlistService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly detail = signal<ProductDetail | null>(null);
  readonly loading = signal(true);
  readonly submittingReview = signal(false);
  readonly embedVideoUrl = signal<SafeResourceUrl | null>(null);

  readonly pickupLabels = PICKUP_LABELS;
  readonly typeLabels = PRODUCT_TYPE_LABELS;
  readonly fmtPrice = formatBRL;
  readonly fmtDur = formatDuration;

  readonly bars = [24, 42, 68, 52, 84, 72, 92, 46, 78, 60, 88, 50, 96, 66, 40, 76, 58, 86, 48, 70, 38, 62, 44, 30];

  reviewRating = 5;
  reviewComment = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const slug = params.get('slug');
      if (slug) this.loadDetail(slug);
    });
  }

  loadDetail(slug: string): void {
    this.loading.set(true);
    this.api.getProductDetail(slug).subscribe({
      next: d => {
        this.detail.set(d);
        this.embedVideoUrl.set(this.toYoutubeEmbed(d.videoUrl));
        this.loading.set(false);
      },
      error: () => {
        this.toast.show('Timbre não encontrado.', 'error');
        this.router.navigate(['/']);
      }
    });
  }

  playMain(): void {
    const p = this.detail()?.product;
    if (!p?.demoAudioUrl) return;
    this.player.playTrack({
      id: p.id,
      slug: p.slug,
      title: p.title,
      creatorName: p.creator.displayName,
      creatorSlug: p.creator.slug,
      targetGear: p.targetGear,
      sourceGear: p.sourceGear,
      price: p.price,
      demoAudioUrl: p.demoAudioUrl,
      loudnessLufs: p.demoLoudnessLufs,
      bitrateKbps: p.demoBitrateKbps
    });
  }

  playSubItem(p: ProductDetail['product']): void {
    if (!p.demoAudioUrl) return;
    this.player.playTrack({
      id: p.id,
      slug: p.slug,
      title: p.title,
      creatorName: p.creator.displayName,
      creatorSlug: p.creator.slug,
      targetGear: p.targetGear,
      sourceGear: p.sourceGear,
      price: p.price,
      demoAudioUrl: p.demoAudioUrl,
      loudnessLufs: p.demoLoudnessLufs,
      bitrateKbps: p.demoBitrateKbps
    });
  }

  seekOnDeck(event: MouseEvent): void {
    const p = this.detail()?.product;
    if (!p) return;
    if (!this.player.isTrackActive(p.id)) {
      this.playMain();
      return;
    }
    const el = event.currentTarget as HTMLElement;
    const rect = el.getBoundingClientRect();
    const pct = ((event.clientX - rect.left) / rect.width) * 100;
    this.player.seekToPercent(pct);
  }

  isBarLit(productId: string, idx: number): boolean {
    if (!this.player.isTrackActive(productId)) return false;
    return ((idx + 1) / this.bars.length) * 100 <= this.player.progressPercent();
  }

  addToCart(productId: string): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/auth'], { queryParams: { redirect: this.router.url } });
      return;
    }
    this.cart.add(productId).subscribe({
      next: () => this.toast.show('Adicionado ao carrinho!', 'success'),
      error: err => this.toast.show(err.error?.message || 'Erro ao adicionar ao carrinho.', 'error')
    });
  }

  buyNow(productId: string): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/auth'], { queryParams: { redirect: this.router.url } });
      return;
    }
    if (this.cart.productIds().has(productId)) {
      this.router.navigate(['/checkout']);
      return;
    }
    this.cart.add(productId).subscribe({
      next: () => this.router.navigate(['/checkout']),
      error: err => this.toast.show(err.error?.message || 'Erro ao iniciar compra.', 'error')
    });
  }

  toggleWish(productId: string): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/auth']);
      return;
    }
    this.wishlist.toggle(productId).subscribe({
      next: res => this.toast.show(res.wishlisted ? 'Salvo nos favoritos.' : 'Removido dos favoritos.')
    });
  }

  submitReview(productId: string): void {
    this.submittingReview.set(true);
    this.api.submitReview(productId, this.reviewRating, this.reviewComment).subscribe({
      next: () => {
        this.toast.show('Avaliação verificada publicada!', 'success');
        this.reviewComment = '';
        this.submittingReview.set(false);
        const slug = this.detail()?.product.slug;
        if (slug) this.loadDetail(slug);
      },
      error: err => {
        this.toast.show(err.error?.message || 'Não foi possível enviar avaliação.', 'error');
        this.submittingReview.set(false);
      }
    });
  }

  private toYoutubeEmbed(url: string | null): SafeResourceUrl | null {
    if (!url) return null;
    const match = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([A-Za-z0-9_-]{6,})/);
    if (!match) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${match[1]}`);
  }
}
