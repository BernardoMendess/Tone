import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminOverview, CreatorProfileDto, formatBRL } from '../../core/models';

@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h1>Painel Administrativo & Curadoria</h1>
        <p>Validação de novos criadores (RF12) e configuração global do Motor de Split / Take Rate (RF13).</p>
      </div>

      @if (overview(); as ov) {
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-label">GMV Total (Pedidos Pagos)</div>
            <div class="kpi-value">{{ fmtPrice(ov.gmvTotal) }}</div>
            <div class="kpi-sub">{{ ov.paidOrdersCount }} pedidos liquidados</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Receita Plataforma (Comissão)</div>
            <div class="kpi-value accent">{{ fmtPrice(ov.platformRevenueTotal) }}</div>
            <div class="kpi-sub">Take rate atual: {{ ov.currentTakeRatePercent }}%</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Criadores Aprovados</div>
            <div class="kpi-value">{{ ov.approvedCreators }}</div>
            <div class="kpi-sub">{{ ov.activeProducts }} timbres publicados</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Fila de Curadoria</div>
            <div class="kpi-value">{{ ov.pendingCreators }}</div>
            <div class="kpi-sub">Aguardando revisão</div>
          </div>
        </div>

        <div class="admin-grid">
          <!-- RF12: Creator Curation Queue -->
          <section class="card pad">
            <h2>Fila de Aprovação de Criadores (RF12)</h2>
            @if (ov.pendingQueue.length === 0) {
              <p class="muted">Nenhum criador aguardando aprovação no momento.</p>
            } @else {
              <div class="queue-list">
                @for (c of ov.pendingQueue; track c.id) {
                  <article class="queue-card">
                    <div class="qc-head">
                      <div>
                        <h3>{{ c.displayName }}</h3>
                        <div class="mono muted">{{ c.userFullName }} ({{ c.userEmail }}) · Rig: {{ c.mainGear }}</div>
                      </div>
                      <span class="badge badge-warning">PENDENTE</span>
                    </div>
                    <p class="qc-bio">{{ c.bio }}</p>
                    <div class="qc-meta mono">
                      PIX: {{ c.pixKey }}
                      @if (c.portfolioDemoUrl) {
                        · <a [href]="c.portfolioDemoUrl" target="_blank" rel="noopener">Ouvir Portfólio ↗</a>
                      }
                    </div>
                    <div class="qc-actions">
                      <button class="btn btn-primary btn-sm" (click)="approve(c)">✓ Aprovar Criador</button>
                      <button class="btn btn-danger btn-sm" (click)="startReject(c)">✕ Recusar</button>
                    </div>

                    @if (rejectTarget()?.id === c.id) {
                      <div class="reject-box">
                        <input class="input" [(ngModel)]="rejectReason" placeholder="Informe o motivo da recusa..."/>
                        <button class="btn btn-danger btn-sm" (click)="confirmReject(c)">Confirmar Recusa</button>
                      </div>
                    }
                  </article>
                }
              </div>
            }
          </section>

          <!-- RF13: Split Engine Take Rate Settings -->
          <aside class="card pad">
            <h2>Motor de Split (RF13)</h2>
            <p class="muted">
              Define a porcentagem de comissão retida automaticamente pela plataforma no momento do split no gateway de pagamento.
            </p>
            <form (ngSubmit)="saveTakeRate()">
              <div class="field">
                <label>Take Rate da Plataforma (%)</label>
                <input type="number" step="0.5" min="1" max="40" class="input mono" [(ngModel)]="takeRate" name="takeRate" required/>
              </div>
              <div class="split-preview mono">
                <div>Criador recebe: <strong>{{ (100 - takeRate) | number:'1.1-2' }}%</strong></div>
                <div>Plataforma retém: <strong>{{ takeRate | number:'1.1-2' }}%</strong></div>
              </div>
              <button type="submit" class="btn btn-primary full">Atualizar Take Rate</button>
            </form>
          </aside>
        </div>

        <!-- All Creators Directory -->
        <h2 class="mt-24">Todos os Criadores ({{ allCreators().length }})</h2>
        <div class="table-wrap">
          <table class="studio-table">
            <thead>
              <tr>
                <th>Estúdio</th>
                <th>Responsável</th>
                <th>Equipamento</th>
                <th>Chave PIX</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              @for (c of allCreators(); track c.id) {
                <tr>
                  <td><strong>{{ c.displayName }}</strong></td>
                  <td>{{ c.userFullName }} <span class="muted">({{ c.userEmail }})</span></td>
                  <td class="mono">{{ c.mainGear }}</td>
                  <td class="mono">{{ c.pixKey }}</td>
                  <td>
                    <span class="badge"
                          [class.badge-success]="c.status === 'APPROVED'"
                          [class.badge-warning]="c.status === 'PENDING'"
                          [class.badge-danger]="c.status === 'REJECTED'">
                      {{ c.status }}
                    </span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .pad { padding: 20px; }
    .accent { color: var(--accent); }
    .admin-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 20px;
      margin-top: 20px;
      align-items: start;
      @media (min-width: 900px) { grid-template-columns: 1.45fr 0.75fr; }
    }
    h2 { margin: 0 0 14px; font-size: 16px; }
    .mt-24 { margin: 24px 0 12px; }
    .muted { color: var(--ink-muted); font-size: 12.5px; }
    .queue-list { display: flex; flex-direction: column; gap: 12px; }
    .queue-card {
      padding: 16px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      background: var(--bg-subtle);
      h3 { margin: 0 0 4px; font-size: 15.5px; }
    }
    .qc-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
    .qc-bio { margin: 10px 0; font-size: 13.5px; color: var(--ink-secondary); }
    .qc-meta { font-size: 12px; color: var(--ink-muted); margin-bottom: 12px; a { color: var(--accent); text-decoration: underline; } }
    .qc-actions { display: flex; gap: 8px; }
    .reject-box { display: flex; gap: 8px; margin-top: 10px; }
    .split-preview {
      padding: 12px;
      background: var(--bg-subtle);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      font-size: 12.5px;
      margin-bottom: 14px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .full { width: 100%; }
  `]
})
export class AdminPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly overview = signal<AdminOverview | null>(null);
  readonly allCreators = signal<CreatorProfileDto[]>([]);
  readonly rejectTarget = signal<CreatorProfileDto | null>(null);
  readonly fmtPrice = formatBRL;

  takeRate = 15;
  rejectReason = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getAdminOverview().subscribe(ov => {
      this.overview.set(ov);
      this.takeRate = ov.currentTakeRatePercent;
    });
    this.api.getAdminCreators().subscribe(list => this.allCreators.set(list));
  }

  approve(c: CreatorProfileDto): void {
    this.api.approveCreator(c.id).subscribe({
      next: () => {
        this.toast.show(`Criador "${c.displayName}" aprovado!`, 'success');
        this.load();
      },
      error: err => this.toast.show(err.error?.message || 'Erro ao aprovar criador.', 'error')
    });
  }

  startReject(c: CreatorProfileDto): void {
    this.rejectTarget.set(c);
    this.rejectReason = '';
  }

  confirmReject(c: CreatorProfileDto): void {
    if (!this.rejectReason.trim()) {
      this.toast.show('Informe um motivo para a recusa.', 'error');
      return;
    }
    this.api.rejectCreator(c.id, this.rejectReason).subscribe({
      next: () => {
        this.rejectTarget.set(null);
        this.toast.show(`Solicitação de "${c.displayName}" recusada.`);
        this.load();
      },
      error: err => this.toast.show(err.error?.message || 'Erro ao recusar criador.', 'error')
    });
  }

  saveTakeRate(): void {
    this.api.updateTakeRate(Number(this.takeRate)).subscribe({
      next: res => {
        this.toast.show(`Take rate atualizado para ${res.takeRatePercent}%!`, 'success');
        this.load();
      },
      error: err => this.toast.show(err.error?.message || 'Valor inválido.', 'error')
    });
  }
}
