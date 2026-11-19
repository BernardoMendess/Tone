import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  CreatorDashboard,
  CreatorProfileDto,
  PICKUP_LABELS,
  PickupType,
  ProductCard,
  ProductType,
  formatBRL
} from '../../core/models';

@Component({
  selector: 'app-creator-studio-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="container">
      <div class="page-header">
        <h1>Estúdio do Criador</h1>
        <p>Publique patches, gerencie versões, monte bundles promocionais e acompanhe seus ganhos líquidos (RF07–RF11).</p>
      </div>

      @if (loading()) {
        <div class="card pad">Carregando estúdio...</div>
      } @else if (!profile() || profile()?.status === 'REJECTED') {
        <!-- RF12: Creator Application Form -->
        <section class="card pad narrow">
          @if (profile()?.status === 'REJECTED') {
            <div class="reject-alert">
              <strong>Solicitação anterior recusada pela curadoria:</strong> {{ profile()?.rejectionReason }}
            </div>
          }
          <h2>Torne-se um Criador Curado no Tone</h2>
          <p class="muted-p">
            Para garantir excelência sonora na plataforma, todos os novos criadores passam por curadoria antes de publicar produtos (RF12).
          </p>

          <form (ngSubmit)="submitApplication()">
            <div class="field">
              <label>Nome do Estúdio / Marca *</label>
              <input class="input" [(ngModel)]="appForm.displayName" name="displayName" required placeholder="Ex.: Studio Valve Tones"/>
            </div>
            <div class="field">
              <label>Biografia e Especialidade *</label>
              <textarea class="textarea" [(ngModel)]="appForm.bio" name="bio" required placeholder="Conte sobre sua experiência, amplificadores e processo de captação..."></textarea>
            </div>
            <div class="grid-2">
              <div class="field">
                <label>Equipamento Principal *</label>
                <input class="input" [(ngModel)]="appForm.mainGear" name="mainGear" required placeholder="Helix, Quad Cortex, Kemper..."/>
              </div>
              <div class="field">
                <label>Chave PIX para Recebimento (Split/Saque) *</label>
                <input class="input" [(ngModel)]="appForm.pixKey" name="pixKey" required placeholder="CPF, e-mail ou chave aleatória"/>
              </div>
            </div>
            <div class="grid-2">
              <div class="field">
                <label>Link de Demonstração / Portfólio</label>
                <input class="input" [(ngModel)]="appForm.portfolioDemoUrl" name="portfolioDemoUrl" placeholder="https://youtube.com/..."/>
              </div>
              <div class="field">
                <label>Instagram</label>
                <input class="input" [(ngModel)]="appForm.instagramUrl" name="instagramUrl" placeholder="https://instagram.com/..."/>
              </div>
            </div>
            <button type="submit" class="btn btn-primary" [disabled]="saving()">
              {{ saving() ? 'Enviando...' : 'Enviar para Fila de Curadoria' }}
            </button>
          </form>
        </section>
      } @else if (profile()?.status === 'PENDING') {
        <!-- RF12: Pending Curation Notice -->
        <section class="card pad narrow center">
          <span class="badge badge-warning">EM ANÁLISE NA FILA DE CURADORIA (RF12)</span>
          <h2>Recebemos sua candidatura, {{ profile()?.displayName }}!</h2>
          <p class="muted-p">
            Nossa equipe administrativa está validando seu perfil e equipamentos. Assim que aprovado, você receberá uma notificação e o painel completo de publicação será liberado automaticamente.
          </p>
        </section>
      } @else {
        <!-- APPROVED CREATOR STUDIO -->
        <div class="tabs-bar">
          <button class="tab-btn" [class.active]="tab() === 'finance'" (click)="tab.set('finance')">Dashboard Financeiro</button>
          <button class="tab-btn" [class.active]="tab() === 'products'" (click)="tab.set('products')">Produtos & Versões ({{ myProducts().length }})</button>
          <button class="tab-btn" [class.active]="tab() === 'bundles'" (click)="tab.set('bundles')">Criar Pacote (Bundle)</button>
          <button class="tab-btn" [class.active]="tab() === 'profile'" (click)="tab.set('profile')">Perfil Público</button>
        </div>

        <!-- TAB 1: RF10 Financial Dashboard -->
        @if (tab() === 'finance' && dashboard(); as dash) {
          <div class="kpi-grid">
            <div class="kpi-card">
              <div class="kpi-label">Vendas Realizadas</div>
              <div class="kpi-value">{{ dash.totalSalesCount }}</div>
              <div class="kpi-sub">{{ dash.activeProductsCount }} produtos ativos</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">Receita Bruta</div>
              <div class="kpi-value">{{ fmtPrice(dash.grossRevenue) }}</div>
              <div class="kpi-sub">Total transacionado</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">Taxa Retida ({{ dash.currentTakeRatePercent }}%)</div>
              <div class="kpi-value">{{ fmtPrice(dash.platformFeeRetained) }}</div>
              <div class="kpi-sub">Take rate automático via split</div>
            </div>
            <div class="kpi-card highlight">
              <div class="kpi-label">Saldo Líquido p/ Saque</div>
              <div class="kpi-value accent">{{ fmtPrice(dash.availableBalance) }}</div>
              <div class="kpi-sub">Total já sacado: {{ fmtPrice(dash.totalWithdrawn) }}</div>
            </div>
          </div>

          <div class="fin-grid">
            <!-- Monthly Bar Chart + Withdrawal -->
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
              <p class="muted-p">Saldo liberado: <strong class="mono">{{ fmtPrice(dash.availableBalance) }}</strong></p>
              <form (ngSubmit)="submitPayout()">
                <div class="field">
                  <label>Valor do Saque (R$)</label>
                  <input type="number" step="0.01" min="10" class="input mono" [(ngModel)]="payoutAmount" name="payoutAmount" required/>
                </div>
                <div class="field">
                  <label>Chave PIX Destino</label>
                  <input class="input" [(ngModel)]="payoutPixKey" name="payoutPixKey" required/>
                </div>
                <button type="submit" class="btn btn-primary" [disabled]="saving() || dash.availableBalance < 10">
                  Transferir via PIX
                </button>
              </form>

              @if (dash.payouts.length > 0) {
                <div class="payout-hist">
                  <div class="mono sub-h">HISTÓRICO DE SAQUES</div>
                  @for (po of dash.payouts; track po.id) {
                    <div class="po-row mono">
                      <span>{{ po.requestedAt | date:'dd/MM/yy' }} · {{ po.pixKey }}</span>
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

        <!-- TAB 2: RF07 Publish Product + RF09 Version Updates -->
        @if (tab() === 'products') {
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
                    <label>Link de Vídeo Opcional (YouTube/Reels)</label>
                    <input class="input" [(ngModel)]="prodForm.videoUrl" name="videoUrl" placeholder="https://youtube.com/watch?v=..."/>
                  </div>
                </div>
                <div class="grid-2">
                  <div class="field">
                    <label>Arquivo Binário do Patch (.hlx, .kipr, .wav IR, .zip) *</label>
                    <input type="file" class="input" (change)="onPatchFileChange($event)" required/>
                    <span class="hint">Armazenado em cofre privado (RNF02 — sem URL pública).</span>
                  </div>
                  <div class="field">
                    <label>Áudio de Demonstração (.wav / .mp3) *</label>
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
                @for (p of myProducts(); track p.id) {
                  <div class="prod-item">
                    <div class="pi-top">
                      <div>
                        <a [routerLink]="['/tones', p.slug]" class="pi-title">{{ p.title }}</a>
                        <div class="mono muted">{{ p.productType }} · {{ p.targetGear }} · v{{ p.currentVersion }} · {{ p.salesCount }} vendas</div>
                      </div>
                      <strong class="mono">{{ fmtPrice(p.price) }}</strong>
                    </div>
                    @if (p.productType !== 'BUNDLE') {
                      <button class="btn btn-outline btn-sm" (click)="openVersionModal(p)">
                        ↑ Publicar Nova Versão (notifica compradores)
                      </button>
                    }
                  </div>
                }
              </div>

              @if (versionTarget(); as vt) {
                <div class="version-box">
                  <h4>Nova versão para: {{ vt.title }} (atual: v{{ vt.currentVersion }})</h4>
                  <form (ngSubmit)="submitNewVersion(vt.id)">
                    <div class="field">
                      <label>Número da Nova Versão *</label>
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
                      <button type="submit" class="btn btn-primary btn-sm" [disabled]="saving()">Lançar & Notificar Compradores</button>
                      <button type="button" class="btn btn-ghost btn-sm" (click)="versionTarget.set(null)">Cancelar</button>
                    </div>
                  </form>
                </div>
              }
            </section>
          </div>
        }

        <!-- TAB 3: RF08 Create Promotional Bundle -->
        @if (tab() === 'bundles') {
          <section class="card pad narrow">
            <h2>Criar Pacote Promocional — Bundle (RF08)</h2>
            <p class="muted-p">Agrupe 2 ou mais dos seus timbres individuais sob um único produto com preço promocional.</p>

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
                      <input type="checkbox" [checked]="selectedBundleIds().has(p.id)" (change)="toggleBundleItem(p.id)"/>
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

              <button type="submit" class="btn btn-accent" [disabled]="saving() || selectedBundleIds().size < 2">
                Publicar Pacote Promocional
              </button>
            </form>
          </section>
        }

        <!-- TAB 4: RF11 Public Profile Management -->
        @if (tab() === 'profile' && profile(); as prof) {
          <section class="card pad narrow">
            <div class="prof-head">
              <h2>Gestão do Perfil Público (RF11)</h2>
              <a [routerLink]="['/creators', prof.slug]" class="btn btn-outline btn-sm">Ver Página Pública ↗</a>
            </div>
            <form (ngSubmit)="saveProfile()">
              <div class="grid-2">
                <div class="field">
                  <label>Nome de Exibição / Estúdio *</label>
                  <input class="input" [(ngModel)]="profForm.displayName" name="pName" required/>
                </div>
                <div class="field">
                  <label>Slug da URL (/creators/slug) *</label>
                  <input class="input mono" [(ngModel)]="profForm.slug" name="pSlug" required/>
                </div>
              </div>
              <div class="field">
                <label>Biografia</label>
                <textarea class="textarea" [(ngModel)]="profForm.bio" name="pBio"></textarea>
              </div>
              <div class="grid-2">
                <div class="field">
                  <label>Equipamento Principal</label>
                  <input class="input" [(ngModel)]="profForm.mainGear" name="pGear"/>
                </div>
                <div class="field">
                  <label>Chave PIX para Repasses *</label>
                  <input class="input" [(ngModel)]="profForm.pixKey" name="pPix" required/>
                </div>
              </div>
              <div class="grid-2">
                <div class="field">
                  <label>YouTube</label>
                  <input class="input" [(ngModel)]="profForm.youtubeUrl" name="pYt"/>
                </div>
                <div class="field">
                  <label>Instagram</label>
                  <input class="input" [(ngModel)]="profForm.instagramUrl" name="pIg"/>
                </div>
              </div>
              <div class="field">
                <label>Website</label>
                <input class="input" [(ngModel)]="profForm.websiteUrl" name="pWeb"/>
              </div>
              <button type="submit" class="btn btn-primary" [disabled]="saving()">Salvar Alterações</button>
            </form>
          </section>
        }
      }
    </div>
  `,
  styles: [`
    .pad { padding: 22px; }
    .narrow { max-width: 720px; }
    .center { text-align: center; }
    .muted-p { color: var(--ink-muted); font-size: 14px; margin: 0 0 18px; }
    .grid-2 { display: grid; grid-template-columns: 1fr; gap: 12px; @media (min-width: 640px) { grid-template-columns: 1fr 1fr; } }
    .reject-alert {
      padding: 12px 14px;
      background: var(--danger-soft);
      border: 1px solid #fecaca;
      color: var(--danger);
      border-radius: var(--radius-sm);
      margin-bottom: 16px;
      font-size: 13.5px;
    }
    .kpi-card.highlight { border-color: var(--accent-border); background: var(--accent-soft); }
    .kpi-value.accent { color: var(--accent); }
    .fin-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 16px;
      margin-top: 18px;
      @media (min-width: 860px) { grid-template-columns: 1.2fr 0.8fr; }
    }
    .bar-chart {
      display: flex;
      align-items: flex-end;
      gap: 16px;
      height: 190px;
      padding-top: 20px;
    }
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
    }
    .pi-top { display: flex; justify-content: space-between; gap: 8px; }
    .pi-title { font-weight: 600; font-size: 14px; &:hover { color: var(--accent); } }
    .version-box {
      margin-top: 16px;
      padding: 16px;
      background: var(--bg-subtle);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
      h4 { margin: 0 0 12px; font-size: 14px; }
    }
    .btn-row { display: flex; gap: 8px; }

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
    .prof-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; h2 { margin: 0; } }
  `]
})
export class CreatorStudioPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly tab = signal<'finance' | 'products' | 'bundles' | 'profile'>('finance');
  readonly profile = signal<CreatorProfileDto | null>(null);
  readonly dashboard = signal<CreatorDashboard | null>(null);
  readonly myProducts = signal<ProductCard[]>([]);
  readonly versionTarget = signal<ProductCard | null>(null);
  readonly selectedBundleIds = signal<Set<string>>(new Set());

  readonly individualProducts = computed(() => this.myProducts().filter(p => p.productType !== 'BUNDLE'));
  readonly bundleSum = computed(() => {
    const ids = this.selectedBundleIds();
    return this.individualProducts().filter(p => ids.has(p.id)).reduce((s, p) => s + p.price, 0);
  });

  readonly pickupLabels = PICKUP_LABELS;
  readonly pickupTypes: PickupType[] = ['SINGLE_COIL', 'HUMBUCKER', 'P90', 'ACTIVE', 'PIEZO', 'ANY'];
  readonly fmtPrice = formatBRL;

  appForm = { displayName: '', bio: '', mainGear: '', pixKey: '', portfolioDemoUrl: '', instagramUrl: '' };
  profForm = { displayName: '', slug: '', bio: '', mainGear: '', pixKey: '', youtubeUrl: '', instagramUrl: '', websiteUrl: '' };
  prodForm = {
    title: '', description: '', price: 49.9, productType: 'PATCH' as ProductType,
    genre: 'Rock', targetGear: 'Line 6 Helix', sourceGear: 'Marshall JCM800',
    pickupType: 'HUMBUCKER' as PickupType, tags: '', videoUrl: ''
  };
  bundleForm = { title: '', description: '', promotionalPrice: 89.9 };
  verForm = { versionNumber: '', changelog: '' };

  payoutAmount = 0;
  payoutPixKey = '';

  private patchFile: File | null = null;
  private demoFile: File | null = null;
  private versionFile: File | null = null;

  ngOnInit(): void {
    this.loadStudio();
  }

  loadStudio(): void {
    this.loading.set(true);
    this.api.getMyCreatorProfile().subscribe({
      next: prof => {
        this.profile.set(prof);
        this.profForm = {
          displayName: prof.displayName,
          slug: prof.slug,
          bio: prof.bio || '',
          mainGear: prof.mainGear || '',
          pixKey: prof.pixKey || '',
          youtubeUrl: prof.youtubeUrl || '',
          instagramUrl: prof.instagramUrl || '',
          websiteUrl: prof.websiteUrl || ''
        };
        this.payoutPixKey = prof.pixKey || '';
        if (prof.status === 'APPROVED') {
          this.loadApprovedData();
        } else {
          this.loading.set(false);
        }
      },
      error: () => {
        this.profile.set(null);
        this.loading.set(false);
      }
    });
  }

  private loadApprovedData(): void {
    this.api.getCreatorDashboard().subscribe({
      next: d => {
        this.dashboard.set(d);
        this.payoutAmount = d.availableBalance;
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.api.getCreatorProducts().subscribe(list => this.myProducts.set(list));
  }

  submitApplication(): void {
    this.saving.set(true);
    this.api.applyCreator(this.appForm).subscribe({
      next: prof => {
        this.profile.set(prof);
        this.saving.set(false);
        this.auth.refreshMe().subscribe();
        this.toast.show('Candidatura enviada para a fila de curadoria!', 'success');
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao enviar solicitação.', 'error');
      }
    });
  }

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
    fd.append('videoUrl', this.prodForm.videoUrl);
    fd.append('patchFile', this.patchFile);
    fd.append('demoAudioFile', this.demoFile);

    this.saving.set(true);
    this.api.createProduct(fd).subscribe({
      next: created => {
        this.saving.set(false);
        this.myProducts.update(list => [created, ...list]);
        this.toast.show(`Timbre "${created.title}" publicado com áudio normalizado em -14 LUFS!`, 'success');
        this.prodForm.title = '';
        this.prodForm.description = '';
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
        this.api.getCreatorProducts().subscribe(list => this.myProducts.set(list));
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao publicar nova versão.', 'error');
      }
    });
  }

  toggleBundleItem(productId: string): void {
    const next = new Set(this.selectedBundleIds());
    if (next.has(productId)) next.delete(productId);
    else next.add(productId);
    this.selectedBundleIds.set(next);
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
      includedProductIds: Array.from(this.selectedBundleIds())
    }).subscribe({
      next: b => {
        this.saving.set(false);
        this.myProducts.update(list => [b, ...list]);
        this.selectedBundleIds.set(new Set());
        this.bundleForm = { title: '', description: '', promotionalPrice: 89.9 };
        this.toast.show(`Pacote "${b.title}" criado com sucesso!`, 'success');
        this.tab.set('products');
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao criar pacote.', 'error');
      }
    });
  }

  saveProfile(): void {
    this.saving.set(true);
    this.api.updateMyCreatorProfile(this.profForm).subscribe({
      next: updated => {
        this.profile.set(updated);
        this.saving.set(false);
        this.toast.show('Perfil público atualizado!', 'success');
      },
      error: err => {
        this.saving.set(false);
        this.toast.show(err.error?.message || 'Erro ao atualizar perfil.', 'error');
      }
    });
  }

  submitPayout(): void {
    this.saving.set(true);
    this.api.requestPayout(Number(this.payoutAmount), this.payoutPixKey).subscribe({
      next: po => {
        this.saving.set(false);
        this.toast.show(`Saque de ${formatBRL(po.amount)} enviado para PIX ${po.pixKey}!`, 'success');
        this.loadApprovedData();
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
