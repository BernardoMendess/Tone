import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { ProductCard } from '../models';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly http = inject(HttpClient);
  private readonly _items = signal<ProductCard[]>([]);

  readonly items = this._items.asReadonly();
  readonly ids = computed(() => new Set(this._items().map(i => i.id)));

  refresh(): void {
    this.http.get<ProductCard[]>('/api/wishlist').subscribe({
      next: list => this._items.set(list),
      error: () => this._items.set([])
    });
  }

  toggle(productId: string): Observable<{ wishlisted: boolean }> {
    return this.http.post<{ wishlisted: boolean }>(`/api/wishlist/${productId}/toggle`, {}).pipe(
      tap(() => this.refresh())
    );
  }

  reset(): void {
    this._items.set([]);
  }
}
