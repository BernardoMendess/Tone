import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AudioPlayerService } from '../../core/services/audio-player.service';
import { ToastService } from '../../core/services/toast.service';
import { LibraryItem, OrderDto, PresignedDownload, formatBRL } from '../../core/models';

@Component({
  selector: 'app-library-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="container">
      <div class="page-header">
        <h1>Biblioteca Digital</h1>
        <p>Acesso vitalício aos seus timbres com downloads via Presigned URL temporária segura (RF04 / RF09 / RNF02).</p>
      </div>

      <div class="tabs-bar">
        <button class="tab-btn" [class.active]="tab() === 'assets'" (click)="tab.set('assets')">
          Meus Timbres ({{ items().length }})
        </button>
        <button class="tab-btn" [class.active]="tab() === 'orders'" (click)="tab.set('orders')">
          Histórico de Pedidos ({{ orders().length }})
        </button>
      </div>

      @if (lastPresigned(); as pre) {
        <div class="presign-banner card">
          <div>
            <span class="badge badge-success">PRESIGNED URL GERADA ({{ pre.storageMode }})</span>
            <strong class="mono"> {{ pre.filename }} (v{{ pre.versionNumber }})</strong>
            <div class="muted mono">Expira em: {{ pre.expiresAt | date:'HH:mm:ss' }} · Sem URL pública permanente</div>
          </div>
          <a [href]="pre.downloadUrl" class="btn btn-primary btn-sm" target="_blank" rel="noopener">
            Baixar novamente
          </a>
        </div>
      }

      @if (tab() === 'assets') {
        @if (loading()) {
          <div class="card pad">Carregando biblioteca...</div>
        } @else if (items().length === 0) {
          <div class="empty-state">
            <h3>Sua biblioteca digital está vazia</h3>
            <p>Adquira seu primeiro timbre ou pacote para liberar downloads vitalícios e atualizações gratuitas.</p>
            <a routerLink="/" class="btn btn-primary">Explorar Catálogo</a>
          </div>
        } @else {
          <div class="lib-list">
            @for (item of items(); track item.productId) {
              <article class="lib-card card">
                <div class="lib-main">
                  <div class="lib-badges">
                    <span class="badge">{{ item.productType }}</span>
                    <span class="badge mono">{{ item.targetGear }}</span>
                    @if (item.unlockedViaBundleTitle) {
                      <span class="badge badge-dark">Via Pacote: {{ item.unlockedViaBundleTitle }}</span>
                    }
                    @if (item.hasUpdate) {
                      <span class="badge badge-accent">Nova versão v{{ item.latestVersion }} disponível!</span>
                    }
                  </div>

                  <h3>
                    <a [routerLink]="['/tones', item.slug]">{{ item.title }}</a>
                  </h3>
                  <div class="lib-sub">
                    por <a [routerLink]="['/creators', item.creatorSlug]">{{ item.creatorName }}</a>
                    · Origem: <span class="mono">{{ item.sourceGear }}</span>
                    · Comprado em {{ item.purchasedAt | date:'dd/MM/yyyy' }}
                  </div>

                  @if (item.hasUpdate && item.latestChangelog) {
                    <div class="changelog-alert">
                      <strong>Novidade v{{ item.latestVersion }}:</strong> {{ item.latestChangelog }}
                    </div>
                  }
                </div>

                <div class="lib-controls">
                  @if (item.versions.length > 0) {
                    <select class="select ver-select mono" [(ngModel)]="selectedVersion[item.productId]">
                      @for (v of item.versions; track v.id) {
                        <option [value]="v.id">v{{ v.versionNumber }} — {{ v.originalFilename }}</option>
                      }
                    </select>
                  }

                  <div class="btn-row">
                    @if (item.demoAudioUrl) {
                      <button class="btn btn-outline btn-sm" (click)="playDemo(item)">
                        {{ player.isTrackPlaying(item.productId) ? '❚❚ Pausar' : '▶ Demo' }}
                      </button>
                    }
                    <button
                      class="btn btn-primary btn-sm"
                      [disabled]="downloadingId() === item.productId"
                      (click)="downloadPatch(item)"
                    >
                      {{ downloadingId() === item.productId ? 'Assinando URL...' : '↓ Baixar Patch (URL Segura)' }}
                    </button>
                    <a [routerLink]="['/tones', item.slug]" class="btn btn-ghost btn-sm">Avaliar ★</a>
                  </div>
                </div>
              </article>
            }
          </div>
        }
      } @else {
        @if (orders().length === 0) {
          <div class="empty-state">
            <h3>Nenhum pedido realizado</h3>
          </div>
        } @else {
          <div class="table-wrap">
            <table class="studio-table">
              <thead>
                <tr>
                  <th>Pedido</th>
                  <th>Data</th>
                  <th>Método</th>
                  <th>Itens</th>
                  <th>Total</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (o of orders(); track o.id) {
                  <tr>
                    <td class="mono"><strong>{{ o.orderCode }}</strong></td>
                    <td class="mono">{{ o.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
                    <td class="mono">{{ o.paymentMethod }}</td>
                    <td>{{ o.items.length }} timbre(s)</td>
                    <td class="mono"><strong>{{ fmtPrice(o.totalAmount) }}</strong></td>
                    <td>
                      <span class="badge" [class.badge-success]="o.status === 'PAID'" [class.badge-warning]="o.status === 'PENDING_PAYMENT'">
                        {{ o.status }}
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .pad { padding: 20px; }
    .presign-banner {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 14px 16px;
      margin-bottom: 18px;
      background: var(--success-soft);
      border-color: #bbf7d0;
    }
    .lib-list { display: flex; flex-direction: column; gap: 14px; }
    .lib-card {
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      @media (min-width: 860px) {
        flex-direction: row;
        justify-content: space-between;
        align-items: center;
      }
    }
    .lib-badges { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
    .lib-main h3 { margin: 0 0 4px; font-size: 17px; a:hover { color: var(--accent); } }
    .lib-sub { font-size: 13px; color: var(--ink-muted); a { color: var(--ink-secondary); text-decoration: underline; } }
    .changelog-alert {
      margin-top: 10px;
      padding: 8px 10px;
      background: var(--accent-soft);
      border: 1px solid var(--accent-border);
      border-radius: var(--radius-sm);
      font-size: 12.5px;
      color: var(--ink-secondary);
    }
    .lib-controls {
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-width: 260px;
    }
    .ver-select { font-size: 12px; padding: 7px 10px; }
    .btn-row { display: flex; flex-wrap: wrap; gap: 6px; }
    .muted { font-size: 12px; color: var(--ink-muted); margin-top: 2px; }
  `]
})
export class LibraryPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly player = inject(AudioPlayerService);
  private readonly toast = inject(ToastService);

  readonly tab = signal<'assets' | 'orders'>('assets');
  readonly items = signal<LibraryItem[]>([]);
  readonly orders = signal<OrderDto[]>([]);
  readonly loading = signal(true);
  readonly downloadingId = signal<string | null>(null);
  readonly lastPresigned = signal<PresignedDownload | null>(null);
  readonly fmtPrice = formatBRL;

  selectedVersion: Record<string, string> = {};

  ngOnInit(): void {
    this.api.getLibrary().subscribe({
      next: list => {
        this.items.set(list);
        for (const item of list) {
          if (item.versions.length > 0) {
            this.selectedVersion[item.productId] = item.versions[0].id;
          }
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.api.getMyOrders().subscribe(list => this.orders.set(list));
  }

  playDemo(item: LibraryItem): void {
    if (!item.demoAudioUrl) return;
    this.player.playTrack({
      id: item.productId,
      slug: item.slug,
      title: item.title,
      creatorName: item.creatorName,
      creatorSlug: item.creatorSlug,
      targetGear: item.targetGear,
      sourceGear: item.sourceGear,
      demoAudioUrl: item.demoAudioUrl
    });
  }

  downloadPatch(item: LibraryItem): void {
    this.downloadingId.set(item.productId);
    const versionId = this.selectedVersion[item.productId];
    this.api.presignDownload(item.productId, versionId).subscribe({
      next: res => {
        this.downloadingId.set(null);
        this.lastPresigned.set(res);
        this.toast.show(`URL segura gerada para ${res.filename}. Iniciando download...`, 'success');
        const link = document.createElement('a');
        link.href = res.downloadUrl;
        link.download = res.filename;
        link.target = '_blank';
        document.body.appendChild(link);
        link.click();
        link.remove();
      },
      error: err => {
        this.downloadingId.set(null);
        this.toast.show(err.error?.message || 'Erro ao gerar link temporário de download.', 'error');
      }
    });
  }
}
