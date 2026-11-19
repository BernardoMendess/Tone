import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, take, takeWhile, timer } from 'rxjs';
import { CartService } from '../../core/services/cart.service';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { OrderDto, PaymentMethod, formatBRL } from '../../core/models';
import { PixQrComponent } from '../../shared/components/pix-qr.component';

@Component({
  selector: 'app-checkout-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PixQrComponent],
  template: `
    <div class="container">
      <div class="page-header">
        <h1>Carrinho & Checkout</h1>
        <p>Pagamento instantâneo com suporte prioritário a PIX e split automático em tempo real (RF03 / RF13).</p>
      </div>

      @if (activeOrder(); as order) {
        <section class="order-stage card">
          @if (order.status === 'PAID') {
            <div class="paid-banner">
              <span class="badge badge-success">PAGAMENTO CONFIRMADO · {{ order.orderCode }}</span>
              <h2>Seus timbres já foram liberados na Biblioteca Digital!</h2>
              <p>O split de pagamento foi liquidado automaticamente entre os criadores e a plataforma.</p>
              <div class="paid-actions">
                <a routerLink="/library" class="btn btn-accent btn-lg">Ir para Minha Biblioteca Digital</a>
                <a routerLink="/" class="btn btn-outline">Continuar Explorando</a>
              </div>
            </div>
          } @else if (order.status === 'EXPIRED' || order.status === 'FAILED' || order.status === 'CANCELLED') {
            <div class="paid-banner">
              <span class="badge badge-danger">{{ order.status }} · {{ order.orderCode }}</span>
              <h2>Este pagamento não foi concluído</h2>
              <p>O prazo do PIX expirou ou o pagamento falhou. Você pode tentar novamente gerando um novo pedido.</p>
              <div class="paid-actions">
                <button class="btn btn-primary" (click)="resetOrder()">Voltar ao Carrinho</button>
              </div>
            </div>
          } @else {
            <div class="pix-stage">
              <div class="pix-left">
                <span class="badge badge-accent">AGUARDANDO CONFIRMAÇÃO · {{ order.orderCode }}</span>
                <h2>Pague {{ fmtPrice(order.totalAmount) }} via {{ order.paymentMethod === 'PIX' ? 'PIX' : 'Cartão' }}</h2>
                <p>
                  @if (order.paymentMethod === 'PIX') {
                    Escaneie o QR Code ou copie o código EMVCo abaixo. Esta página atualiza automaticamente assim que o webhook confirmar o pagamento.
                  } @else {
                    Aguardando autorização da operadora via webhook seguro. Esta página atualiza automaticamente.
                  }
                </p>

                @if (order.pixCopyPaste) {
                  <app-pix-qr [payload]="order.pixCopyPaste"></app-pix-qr>
                }

                @if (sandboxEnabled()) {
                  <div class="sandbox-box">
                    <div class="mono sb-label">AMBIENTE SANDBOX DEV (WEBHOOK HMAC ASSINADO)</div>
                    <button class="btn btn-accent btn-lg full" (click)="confirmSandbox(order.id)" [disabled]="confirming()">
                      {{ confirming() ? 'Processando Webhook...' : 'Confirmar Pagamento Agora (Simular Webhook)' }}
                    </button>
                  </div>
                }
              </div>

              <div class="pix-right">
                <h3>Resumo do Pedido & Motor de Split</h3>
                @for (item of order.items; track item.id) {
                  <div class="mini-row">
                    <span>{{ item.title }}</span>
                    <strong class="mono">{{ fmtPrice(item.unitPrice) }}</strong>
                  </div>
                }
                <div class="split-box">
                  <div class="mono sb-label">SPLIT AUTOMÁTICO CONFIGURADO NO GATEWAY (RF13)</div>
                  @for (s of order.splits; track s.id) {
                    <div class="split-item">
                      <div>
                        <strong>{{ s.creatorName }}</strong>
                        <div class="mono muted">Ref: {{ s.gatewaySplitReference }}</div>
                      </div>
                      <div class="mono right">
                        <div>Criador: {{ fmtPrice(s.creatorNetAmount) }}</div>
                        <div class="muted">Taxa ({{ s.takeRatePercent }}%): {{ fmtPrice(s.platformFeeAmount) }}</div>
                      </div>
                    </div>
                  }
                </div>
              </div>
            </div>
          }
        </section>
      } @else if (cart.count() === 0) {
        <div class="empty-state">
          <h3>Seu carrinho está vazio</h3>
          <p>Explore o catálogo, ouça as demonstrações e adicione timbres para finalizar a compra.</p>
          <a routerLink="/" class="btn btn-primary">Explorar Timbres</a>
        </div>
      } @else {
        <div class="checkout-grid">
          <section class="card items-box">
            @for (ci of cart.cart().items; track ci.productId) {
              <div class="cart-row">
                <div class="cart-info">
                  <a [routerLink]="['/tones', ci.product.slug]" class="c-title">{{ ci.product.title }}</a>
                  <div class="c-meta mono">
                    {{ ci.product.creator.displayName }} · {{ ci.product.targetGear }} · v{{ ci.product.currentVersion }}
                  </div>
                </div>
                <div class="cart-right">
                  <strong class="mono">{{ fmtPrice(ci.product.price) }}</strong>
                  <button class="btn btn-ghost btn-sm" (click)="removeItem(ci.productId)">Remover</button>
                </div>
              </div>
            }
          </section>

          <aside class="card summary-box">
            <h2>Pagamento</h2>

            <div class="method-list">
              <label class="method-option" [class.selected]="paymentMethod === 'PIX'">
                <input type="radio" name="pm" value="PIX" [(ngModel)]="paymentMethod"/>
                <div>
                  <strong>PIX Instantâneo <span class="badge badge-accent">PRIORITÁRIO</span></strong>
                  <div class="muted">Liberação imediata via QR Code / Copia e Cola</div>
                </div>
              </label>

              <label class="method-option" [class.selected]="paymentMethod === 'CREDIT_CARD'">
                <input type="radio" name="pm" value="CREDIT_CARD" [(ngModel)]="paymentMethod"/>
                <div>
                  <strong>Cartão de Crédito</strong>
                  <div class="muted">Confirmação segura via webhook do gateway</div>
                </div>
              </label>
            </div>

            <div class="totals">
              <div class="t-row">
                <span>Itens ({{ cart.count() }})</span>
                <span class="mono">{{ fmtPrice(cart.subtotal()) }}</span>
              </div>
              <div class="t-row muted">
                <span>Split criadores ({{ 100 - cart.cart().takeRatePercent }}% / {{ cart.cart().takeRatePercent }}%)</span>
                <span class="mono">Automático</span>
              </div>
              <div class="t-row total">
                <span>Total</span>
                <strong class="mono">{{ fmtPrice(cart.subtotal()) }}</strong>
              </div>
            </div>

            <button class="btn btn-accent btn-lg full" (click)="placeOrder()" [disabled]="placing()">
              {{ placing() ? 'Gerando cobrança...' : (paymentMethod === 'PIX' ? 'Gerar PIX e Finalizar' : 'Gerar Cobrança no Cartão') }}
            </button>
          </aside>
        </div>
      }
    </div>
  `,
  styles: [`
    .checkout-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 20px;
      align-items: start;
      @media (min-width: 900px) {
        grid-template-columns: 1fr 360px;
      }
    }
    .items-box { overflow: hidden; }
    .cart-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 16px 18px;
      border-bottom: 1px solid var(--border);
      &:last-child { border-bottom: none; }
    }
    .c-title { font-weight: 600; font-size: 15px; display: block; &:hover { color: var(--accent); } }
    .c-meta { font-size: 12px; color: var(--ink-muted); margin-top: 3px; }
    .cart-right { display: flex; align-items: center; gap: 10px; }

    .summary-box { padding: 20px; h2 { margin: 0 0 14px; font-size: 16px; } }
    .method-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
    .method-option {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
      cursor: pointer;
      &.selected { border-color: var(--bg-dark); background: var(--bg-subtle); }
      strong { font-size: 13.5px; display: flex; align-items: center; gap: 6px; }
    }
    .totals {
      padding-top: 14px;
      border-top: 1px solid var(--border);
      margin-bottom: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .t-row { display: flex; justify-content: space-between; font-size: 13.5px; }
    .t-row.total { font-size: 17px; padding-top: 8px; border-top: 1px solid var(--border); }
    .full { width: 100%; }
    .muted { color: var(--ink-muted); font-size: 12px; }

    .order-stage { padding: 24px; }
    .pix-stage {
      display: grid;
      grid-template-columns: 1fr;
      gap: 24px;
      @media (min-width: 860px) { grid-template-columns: 1.1fr 0.9fr; }
    }
    .pix-left h2, .paid-banner h2 { margin: 10px 0 6px; font-size: 22px; }
    .pix-left p, .paid-banner p { margin: 0 0 16px; color: var(--ink-secondary); font-size: 14px; }
    .sandbox-box {
      margin-top: 16px;
      padding: 14px;
      border: 1px dashed var(--accent-border);
      background: var(--accent-soft);
      border-radius: var(--radius-sm);
    }
    .sb-label { font-size: 10.5px; color: var(--ink-muted); margin-bottom: 8px; letter-spacing: 0.04em; }
    .mini-row {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid var(--border);
      font-size: 13.5px;
    }
    .split-box {
      margin-top: 16px;
      padding: 14px;
      background: var(--bg-subtle);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }
    .split-item {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      padding: 8px 0;
      border-bottom: 1px solid var(--border);
      font-size: 12.5px;
      &:last-child { border-bottom: none; }
      .right { text-align: right; }
    }
    .paid-banner { text-align: center; padding: 20px 10px; }
    .paid-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; }
  `]
})
export class CheckoutPage implements OnInit {
  readonly cart = inject(CartService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeOrder = signal<OrderDto | null>(null);
  readonly placing = signal(false);
  readonly confirming = signal(false);
  readonly sandboxEnabled = signal(false);
  readonly fmtPrice = formatBRL;

  paymentMethod: PaymentMethod = 'PIX';
  private idempotencyKey = crypto.randomUUID();

  ngOnInit(): void {
    this.cart.refresh();
    this.api.getPublicConfig().subscribe({
      next: cfg => this.sandboxEnabled.set(!!cfg.sandboxEnabled),
      error: () => this.sandboxEnabled.set(false)
    });
  }

  removeItem(productId: string): void {
    this.idempotencyKey = crypto.randomUUID();
    this.cart.remove(productId).subscribe();
  }

  resetOrder(): void {
    this.activeOrder.set(null);
    this.idempotencyKey = crypto.randomUUID();
    this.cart.refresh();
  }

  placeOrder(): void {
    if (this.placing()) return;
    this.placing.set(true);
    this.api.checkout(this.paymentMethod, this.idempotencyKey).subscribe({
      next: order => {
        this.activeOrder.set(order);
        this.placing.set(false);
        this.idempotencyKey = crypto.randomUUID();
        this.cart.refresh();
        if (order.status === 'PENDING_PAYMENT') {
          this.watchPaymentStatus(order.id);
        } else if (order.status === 'PAID') {
          this.toast.show('Compra confirmada! Timbres liberados na sua Biblioteca.', 'success');
        }
      },
      error: err => {
        this.placing.set(false);
        this.toast.show(err.error?.message || 'Erro ao processar checkout.', 'error');
      }
    });
  }

  confirmSandbox(orderId: string): void {
    if (this.confirming()) return;
    this.confirming.set(true);
    this.api.confirmSandboxPayment(orderId).subscribe({
      next: order => {
        this.activeOrder.set(order);
        this.confirming.set(false);
        this.toast.show('Pagamento confirmado via webhook! Split liquidado e biblioteca atualizada.', 'success');
      },
      error: err => {
        this.confirming.set(false);
        this.toast.show(err.error?.message || 'Erro ao confirmar pagamento sandbox.', 'error');
      }
    });
  }

  private watchPaymentStatus(orderId: string): void {
    timer(3000, 3000).pipe(
      switchMap(() => this.api.getOrder(orderId).pipe(catchError(() => of(null)))),
      takeWhile(order => order === null || order.status === 'PENDING_PAYMENT', true),
      take(200),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(order => {
      if (!order) return;
      this.activeOrder.set(order);
      if (order.status === 'PAID') {
        this.toast.show('Pagamento confirmado! Seus timbres já estão liberados na Biblioteca.', 'success');
      } else if (order.status === 'EXPIRED') {
        this.toast.show('O código PIX expirou. Gere um novo pedido.', 'error');
      }
    });
  }
}
