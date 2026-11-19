import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WishlistService } from '../../core/services/wishlist.service';
import { ToneCardComponent } from '../../shared/components/tone-card.component';

@Component({
  selector: 'app-wishlist-page',
  standalone: true,
  imports: [CommonModule, RouterLink, ToneCardComponent],
  template: `
    <div class="container">
      <div class="page-header">
        <h1>Favoritos (Wishlist)</h1>
        <p>Timbres salvos para comparação de áudio e compras futuras (RF06).</p>
      </div>

      @if (wishlist.items().length === 0) {
        <div class="empty-state">
          <h3>Nenhum timbre salvo nos favoritos</h3>
          <p>Clique no ícone de coração nos cartões do catálogo para montar sua seleção.</p>
          <a routerLink="/" class="btn btn-primary">Explorar Catálogo</a>
        </div>
      } @else {
        <div class="wish-grid">
          @for (p of wishlist.items(); track p.id) {
            <app-tone-card [product]="p" [siblingProducts]="wishlist.items()"></app-tone-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .wish-grid {
      display: grid;
      grid-template-columns: 1fr;
      gap: 16px;
      @media (min-width: 700px) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      @media (min-width: 1100px) { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    }
  `]
})
export class WishlistPage implements OnInit {
  readonly wishlist = inject(WishlistService);

  ngOnInit(): void {
    this.wishlist.refresh();
  }
}
