import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { CartDto } from '../models';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly _cart = signal<CartDto>({ items: [], count: 0, subtotal: 0, takeRatePercent: 15 });

  readonly cart = this._cart.asReadonly();
  readonly count = computed(() => this._cart().count);
  readonly subtotal = computed(() => this._cart().subtotal);
  readonly productIds = computed(() => new Set(this._cart().items.map(i => i.productId)));

  refresh(): void {
    this.http.get<CartDto>('/api/cart').subscribe({
      next: c => this._cart.set(c),
      error: () => this.reset()
    });
  }

  add(productId: string): Observable<CartDto> {
    return this.http.post<CartDto>(`/api/cart/${productId}`, {}).pipe(
      tap(c => this._cart.set(c))
    );
  }

  remove(productId: string): Observable<CartDto> {
    return this.http.delete<CartDto>(`/api/cart/${productId}`).pipe(
      tap(c => this._cart.set(c))
    );
  }

  clear(): Observable<CartDto> {
    return this.http.delete<CartDto>('/api/cart').pipe(
      tap(c => this._cart.set(c))
    );
  }

  reset(): void {
    this._cart.set({ items: [], count: 0, subtotal: 0, takeRatePercent: 15 });
  }
}
