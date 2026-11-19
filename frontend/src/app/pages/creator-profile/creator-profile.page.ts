import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { PublicCreatorProfile } from '../../core/models';
import { ToneCardComponent } from '../../shared/components/tone-card.component';

@Component({
  selector: 'app-creator-profile-page',
  standalone: true,
  imports: [CommonModule, RouterLink, ToneCardComponent],
  template: `
    <div class="container creator-wrap">
      <a routerLink="/" class="back-link">← Voltar ao catálogo</a>

      @if (loading()) {
        <div class="card pad">Carregando perfil do criador...</div>
      } @else {
        @if (profile(); as c) {
          <section class="profile-hero card">
            <div class="avatar">{{ initials(c.displayName) }}</div>
            <div class="info">
              <div class="top-row">
                <h1>{{ c.displayName }}</h1>
                <span class="badge badge-success">Criador Verificado</span>
              </div>
              <p class="bio">{{ c.bio }}</p>
              @if (c.mainGear) {
                <div class="gear mono">Rig Principal: {{ c.mainGear }}</div>
              }
              <div class="links">
                @if (c.youtubeUrl) { <a [href]="c.youtubeUrl" target="_blank" rel="noopener" class="btn btn-outline btn-sm">YouTube ↗</a> }
                @if (c.instagramUrl) { <a [href]="c.instagramUrl" target="_blank" rel="noopener" class="btn btn-outline btn-sm">Instagram ↗</a> }
                @if (c.websiteUrl) { <a [href]="c.websiteUrl" target="_blank" rel="noopener" class="btn btn-outline btn-sm">Website ↗</a> }
              </div>
            </div>

            <div class="stats mono">
              <div><strong>{{ c.totalProducts }}</strong><span>Timbres</span></div>
              <div><strong>{{ c.totalSales }}</strong><span>Vendas</span></div>
              <div><strong>{{ c.averageRating > 0 ? (c.averageRating | number:'1.1-1') : '—' }}</strong><span>Nota ★</span></div>
            </div>
          </section>

          <h2 class="section-title">Portfólio de Timbres ({{ c.products.length }})</h2>
          <div class="portfolio-grid">
            @for (p of c.products; track p.id) {
              <app-tone-card [product]="p" [siblingProducts]="c.products"></app-tone-card>
            }
          </div>
        } @else {
          <div class="empty-state">
            <h3>Criador não encontrado</h3>
            <a routerLink="/" class="btn btn-primary">Voltar ao catálogo</a>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .creator-wrap { padding-top: 22px; }
    .back-link { display: inline-block; font-size: 13px; color: var(--ink-muted); margin-bottom: 14px; }
    .pad { padding: 20px; }
    .profile-hero {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 18px;
      @media (min-width: 820px) {
        flex-direction: row;
        align-items: flex-start;
      }
    }
    .avatar {
      width: 68px;
      height: 68px;
      border-radius: 12px;
      background: var(--bg-dark);
      color: #fff;
      font-family: var(--font-mono);
      font-size: 22px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .info { flex: 1; }
    .top-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; h1 { margin: 0; font-size: 24px; } }
    .bio { margin: 8px 0 10px; color: var(--ink-secondary); max-width: 680px; }
    .gear { font-size: 12px; color: var(--ink-muted); margin-bottom: 12px; }
    .links { display: flex; flex-wrap: wrap; gap: 8px; }
    .stats {
      display: flex;
      gap: 20px;
      padding: 12px 16px;
      background: var(--bg-subtle);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      div { text-align: center; }
      strong { display: block; font-size: 18px; }
      span { font-size: 11px; color: var(--ink-muted); text-transform: uppercase; }
    }
    .section-title { margin: 26px 0 14px; font-size: 18px; font-weight: 600; }
    .portfolio-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 16px;
      @media (min-width: 720px) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      @media (min-width: 1100px) { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    }
  `]
})
export class CreatorProfilePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);

  readonly profile = signal<PublicCreatorProfile | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const slug = params.get('slug');
      if (!slug) return;
      this.loading.set(true);
      this.api.getPublicCreator(slug).subscribe({
        next: data => { this.profile.set(data); this.loading.set(false); },
        error: () => { this.profile.set(null); this.loading.set(false); }
      });
    });
  }

  initials(name: string): string {
    return name.split(/\s+/).slice(0, 2).map(s => s[0]).join('').toUpperCase();
  }
}
