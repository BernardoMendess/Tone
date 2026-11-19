import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { CreatorDashboard, formatBRL } from '../../core/models';

@Component({
  selector: 'app-creator-finance-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (dashboard(); as dash) {
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-label">Vendas Realizadas</div>
          <div class="kpi-value">{{ dash.totalSalesCount }}</div>
          <div class="kpi-sub">{{ dash.activeProductsCount }} produtos ativos</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Receita Bruta</div>
          <div class="kpi-value">{{ fmtPrice(dash.grossRevenue) }}</div>
          <div class="kpi-sub">Taxa ({{ dash.currentTakeRatePercent }}%): {{ fmtPrice(dash.platformFeeRetained) }}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Em Retenção (Garantia)</div>
          <div class="kpi-value">{{ fmtPrice(dash.heldBalance) }}</div>
          <div class="kpi-sub">Liberado após período de segurança</div>
        </div>
        <div class="kpi-card highlight">
          <div class="kpi-label">Saldo Liberado p/ Saque</div>
          <div class="kpi-value accent">{{ fmtPrice(dash.availableBalance) }}</div>
          <div class="kpi-sub">Já sacado/em processo: {{ fmtPrice(dash.totalWithdrawn) }}</div>
        </div>
      </div>

      <div class="fin-grid">
        <div class="card pad">
          <h3>Evolução Mensal (Receita Líquida)</h3>
          @if (dash.monthlySeries.length === 0) {
            <p class="muted-p">Suas vendas aparecerão aqui assim que o primeiro pedido for liquidado.</p>
          } @else {
            <div class="bar-chart">
              @for (pt of dash.monthlySeries; track pt.month) {
                <div class="bar-col">
                  <div class="bar-val mono">{{ fmtPrice(pt.net) }}</div>
                  <div class="bar-track">
                    <div class="bar-fill" [style.height.%]="barPercent(pt.net, dash.monthlySeries)"></div>
                  </div>
                  <div class="bar-lbl mono">{{ pt.month }}</div>
                </div>
              }
            </div>
          }
        </div>

        <div class="card pad">
          <h3>Solicitar Saque via PIX</h3>
          <p class="muted-p">
            Disponível: <strong class="mono">{{ fmtPrice(dash.availableBalance) }}</strong><br/>
            Chave PIX cadastrada: <strong class="mono">{{ registeredPixKey() || 'Não cadastrada' }}</strong>
          </p>
          <form (ngSubmit)="submitPayout()">
            <div class="field">
              <label>Valor do Saque (R$)</label>
              <input type="number" step="0.01" min="10" class="input mono" [(ngModel)]="payoutAmount" name="payoutAmount" required/>
              <span class="hint">Por segurança, saques são enviados exclusivamente para a chave PIX salva no seu perfil.</span>
            </div>
            <button type="submit" class="btn btn-primary" [disabled]="saving() || dash.availableBalance < 10">
              {{ saving() ? 'Processando...' : 'Solicitar Transferência PIX' }}
            </button>
          </form>

          @if (dash.payouts.length > 0) {
            <div class="payout-hist">
              <div class="mono sub-h">HISTÓRICO DE SAQUES</div>
              @for (po of dash.payouts; track po.id) {
                <div class="po-row mono">
                  <span>{{ po.requestedAt | date:'dd/MM/yy' }} · {{ po.status }}</span>
                  <strong>{{ fmtPrice(po.amount) }}</strong>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <h3 class="mt-24">Extrato Detalhado de Vendas & Split</h3>
      <div class="table-wrap">
        <table class="studio-table">
          <thead>
            <tr>
              <th>Pedido</th>
              <th>Data</th>
              <th>Timbre</th>
              <th>Comprador</th>
              <th>Bruto</th>
              <th>Taxa</th>
              <th>Líquido Criador</th>
            </tr>
          </thead>
          <tbody>
            @if (dash.recentSales.length === 0) {
              <tr><td colspan="7" class="center-td">Nenhuma venda registrada ainda.</td></tr>
            }
            @for (s of dash.recentSales; track s.orderId + s.productTitle) {
              <tr>
                <td class="mono">{{ s.orderCode }}</td>
                <td class="mono">{{ s.paidAt | date:'dd/MM/yyyy' }}</td>
                <td><strong>{{ s.productTitle }}</strong></td>
                <td>{{ s.buyerName }}</td>
                <td class="mono">{{ fmtPrice(s.grossAmount) }}</td>
                <td class="mono muted">-{{ fmtPrice(s.platformFeeAmount) }} ({{ s.takeRatePercent }}%)</td>
                <td class="mono"><strong class="green">{{ fmtPrice(s.creatorNetAmount) }}</strong></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .pad { padding: 22px; }
    .muted-p { color: var(--ink-muted); font-size: 13.5px; margin: 0 0 16px; line-height: 1.5; }
    .hint { font-size: 11.5px; color: var(--ink-muted); display: block; margin-top: 4px; }
    .kpi-card.highlight { border-color: var(--accent-border); background: var(--accent-soft); }
    .kpi-value.accent { color: var(--accent); }
    .fin-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 16px;
      margin-top: 18px;
      @media (min-width: 860px) { grid-template-columns: 1.2fr 0.8fr; }
    }
    .bar-chart { display: flex; align-items: flex-end; gap: 16px; height: 190px; padding-top: 20px; }
    .bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; }
    .bar-val { font-size: 11px; color: var(--ink-secondary); margin-bottom: 6px; }
    .bar-track { flex: 1; width: 100%; max-width: 48px; background: var(--bg-subtle); border-radius: 4px; display: flex; align-items: flex-end; overflow: hidden; }
    .bar-fill { width: 100%; background: var(--bg-dark); border-radius: 4px 4px 0 0; min-height: 6px; }
    .bar-lbl { font-size: 11px; color: var(--ink-muted); margin-top: 6px; }
    .payout-hist { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--border); }
    .sub-h { font-size: 10.5px; color: var(--ink-muted); margin-bottom: 8px; }
    .po-row { display: flex; justify-content: space-between; font-size: 12px; padding: 4px 0; }
    .mt-24 { margin: 24px 0 12px; font-size: 16px; }
    .green { color: var(--success); }
    .muted { color: var(--ink-muted); font-size: 12px; }
    .center-td { text-align: center; color: var(--ink-muted); padding: 24px; }
  `]
})
export class CreatorFinanceTabComponent {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly dashboard = input<CreatorDashboard | null>(null);
  readonly registeredPixKey = input<string | null>(null);
  readonly payoutCompleted = output<void>();

  readonly saving = signal(false);
  readonly fmtPrice = formatBRL;

  payoutAmount = 0;
  private idempotencyKey = crypto.randomUUID();

  constructor() {
    effect(() => {
      const d = this.dashboard();
      if (d) {
        this.payoutAmount = d.availableBalance;
      }
    });
  }

  submitPayout(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.requestPayout(Number(this.payoutAmount), this.idempotencyKey).subscribe({
      next: po => {
        this.saving.set(false);
        this.idempotencyKey = crypto.randomUUID();
        this.toast.show(`Saque de ${formatBRL(po.amount)} solicitado para PIX ${po.pixKey}!`, 'success');
        this.payoutCompleted.emit();
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao solicitar saque.', 'error');
      }
    });
  }

  barPercent(val: number, series: { net: number }[]): number {
    const max = Math.max(...series.map(s => s.net), 1);
    return Math.max(8, Math.round((val / max) * 100));
  }
}
