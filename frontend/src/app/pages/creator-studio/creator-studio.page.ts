import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { CreatorDashboard, CreatorProfileDto, ProductCard } from '../../core/models';
import { CreatorApplicationFormComponent } from './creator-application-form.component';
import { CreatorFinanceTabComponent } from './creator-finance-tab.component';
import { CreatorProductsTabComponent } from './creator-products-tab.component';
import { CreatorBundlesTabComponent } from './creator-bundles-tab.component';
import { CreatorProfileTabComponent } from './creator-profile-tab.component';

@Component({
  selector: 'app-creator-studio-page',
  standalone: true,
  imports: [
    CommonModule,
    CreatorApplicationFormComponent,
    CreatorFinanceTabComponent,
    CreatorProductsTabComponent,
    CreatorBundlesTabComponent,
    CreatorProfileTabComponent
  ],
  template: `
    <div class="container">
      <div class="page-header">
        <h1>Estúdio do Criador</h1>
        <p>Publique patches, gerencie versões, monte bundles promocionais e acompanhe seus ganhos líquidos (RF07–RF11).</p>
      </div>

      @if (loading()) {
        <div class="card pad">Carregando estúdio...</div>
      } @else if (!profile() || profile()?.status === 'REJECTED') {
        <app-creator-application-form
          [rejectedReason]="profile()?.status === 'REJECTED' ? (profile()?.rejectionReason ?? null) : null"
          (applied)="profile.set($event)"
        ></app-creator-application-form>
      } @else if (profile()?.status === 'PENDING') {
        <section class="card pad narrow center">
          <span class="badge badge-warning">EM ANÁLISE NA FILA DE CURADORIA (RF12)</span>
          <h2>Recebemos sua candidatura, {{ profile()?.displayName }}!</h2>
          <p class="muted-p">
            Nossa equipe administrativa está validando seu perfil e equipamentos. Assim que aprovado, você receberá uma notificação e o painel completo será liberado automaticamente.
          </p>
        </section>
      } @else if (profile()?.status === 'SUSPENDED') {
        <section class="card pad narrow center">
          <span class="badge badge-danger">CONTA DE CRIADOR SUSPENSA</span>
          <h2>Seu perfil está temporariamente suspenso</h2>
          <p class="muted-p">{{ profile()?.rejectionReason || 'Entre em contato com a equipe de curadoria para mais detalhes.' }}</p>
        </section>
      } @else {
        <div class="tabs-bar">
          <button class="tab-btn" [class.active]="tab() === 'finance'" (click)="tab.set('finance')">Dashboard Financeiro</button>
          <button class="tab-btn" [class.active]="tab() === 'products'" (click)="tab.set('products')">Produtos & Versões ({{ myProducts().length }})</button>
          <button class="tab-btn" [class.active]="tab() === 'bundles'" (click)="tab.set('bundles')">Criar Pacote (Bundle)</button>
          <button class="tab-btn" [class.active]="tab() === 'profile'" (click)="tab.set('profile')">Perfil Público</button>
        </div>

        @if (tab() === 'finance') {
          <app-creator-finance-tab
            [dashboard]="dashboard()"
            [registeredPixKey]="profile()?.pixKey ?? null"
            (payoutCompleted)="loadApprovedData()"
          ></app-creator-finance-tab>
        }

        @if (tab() === 'products') {
          <app-creator-products-tab
            [products]="myProducts()"
            (productsChanged)="refreshProducts()"
          ></app-creator-products-tab>
        }

        @if (tab() === 'bundles') {
          <app-creator-bundles-tab
            [products]="myProducts()"
            (bundleCreated)="onBundleCreated()"
          ></app-creator-bundles-tab>
        }

        @if (tab() === 'profile') {
          <app-creator-profile-tab
            [profile]="profile()"
            (profileUpdated)="profile.set($event)"
          ></app-creator-profile-tab>
        }
      }
    </div>
  `,
  styles: [`
    .pad { padding: 22px; }
    .narrow { max-width: 720px; }
    .center { text-align: center; }
    .muted-p { color: var(--ink-muted); font-size: 14px; margin: 0 0 18px; }
  `]
})
export class CreatorStudioPage implements OnInit {
  private readonly api = inject(ApiService);

  readonly loading = signal(true);
  readonly tab = signal<'finance' | 'products' | 'bundles' | 'profile'>('finance');
  readonly profile = signal<CreatorProfileDto | null>(null);
  readonly dashboard = signal<CreatorDashboard | null>(null);
  readonly myProducts = signal<ProductCard[]>([]);

  ngOnInit(): void {
    this.loadStudio();
  }

  loadStudio(): void {
    this.loading.set(true);
    this.api.getMyCreatorProfile().subscribe({
      next: prof => {
        this.profile.set(prof);
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

  loadApprovedData(): void {
    this.api.getCreatorDashboard().subscribe({
      next: d => {
        this.dashboard.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.refreshProducts();
  }

  refreshProducts(): void {
    this.api.getCreatorProducts().subscribe(page => this.myProducts.set(page.content));
  }

  onBundleCreated(): void {
    this.refreshProducts();
    this.tab.set('products');
  }
}
