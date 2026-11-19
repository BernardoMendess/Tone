import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AdminOverview,
  CreatorDashboard,
  CreatorProfileDto,
  CreatorStatus,
  FilterOptions,
  LibraryItem,
  NotificationItem,
  OrderDto,
  PaymentMethod,
  PayoutItem,
  PickupType,
  PresignedDownload,
  ProductCard,
  ProductDetail,
  ProductType,
  ProductVersion,
  PublicCreatorProfile,
  ReviewItem
} from '../models';

export interface CatalogSearchParams {
  q?: string;
  genre?: string;
  targetGear?: string;
  sourceGear?: string;
  pickupType?: PickupType | '';
  productType?: ProductType | '';
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // --- Catalog (RF01, RF02, RF11) ---
  searchProducts(filters: CatalogSearchParams): Observable<ProductCard[]> {
    let params = new HttpParams();
    if (filters.q?.trim()) params = params.set('q', filters.q.trim());
    if (filters.genre) params = params.set('genre', filters.genre);
    if (filters.targetGear) params = params.set('targetGear', filters.targetGear);
    if (filters.sourceGear) params = params.set('sourceGear', filters.sourceGear);
    if (filters.pickupType) params = params.set('pickupType', filters.pickupType);
    if (filters.productType) params = params.set('productType', filters.productType);
    if (filters.sort) params = params.set('sort', filters.sort);
    return this.http.get<ProductCard[]>('/api/products', { params });
  }

  getFilterOptions(): Observable<FilterOptions> {
    return this.http.get<FilterOptions>('/api/products/filters');
  }

  getProductDetail(slugOrId: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`/api/products/${slugOrId}`);
  }

  getPublicCreator(slug: string): Observable<PublicCreatorProfile> {
    return this.http.get<PublicCreatorProfile>(`/api/creators/${slug}`);
  }

  // --- Reviews (RF05) ---
  submitReview(productId: string, rating: number, comment: string): Observable<ReviewItem> {
    return this.http.post<ReviewItem>(`/api/products/${productId}/reviews`, { rating, comment });
  }

  // --- Checkout & Orders (RF03, RF13) ---
  checkout(paymentMethod: PaymentMethod, cardLast4?: string): Observable<OrderDto> {
    return this.http.post<OrderDto>('/api/checkout', { paymentMethod, cardLast4 });
  }

  getMyOrders(): Observable<OrderDto[]> {
    return this.http.get<OrderDto[]>('/api/orders');
  }

  simulatePixPayment(orderId: string): Observable<OrderDto> {
    return this.http.post<OrderDto>(`/api/orders/${orderId}/simulate-pix-payment`, {});
  }

  // --- Digital Library & Presigned Downloads (RF04, RNF02) ---
  getLibrary(): Observable<LibraryItem[]> {
    return this.http.get<LibraryItem[]>('/api/library');
  }

  presignDownload(productId: string, versionId?: string): Observable<PresignedDownload> {
    let params = new HttpParams();
    if (versionId) params = params.set('versionId', versionId);
    return this.http.post<PresignedDownload>(`/api/library/downloads/${productId}/presign`, {}, { params });
  }

  // --- Notifications (RF09, RF12) ---
  getNotifications(): Observable<NotificationItem[]> {
    return this.http.get<NotificationItem[]>('/api/notifications');
  }

  markNotificationRead(id: string): Observable<{ read: boolean }> {
    return this.http.patch<{ read: boolean }>(`/api/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>('/api/notifications/read-all', {});
  }

  // --- Creator Studio (RF07, RF08, RF09, RF10, RF11, RF12) ---
  applyCreator(payload: Record<string, string | null>): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>('/api/creator/apply', payload);
  }

  getMyCreatorProfile(): Observable<CreatorProfileDto> {
    return this.http.get<CreatorProfileDto>('/api/creator/profile');
  }

  updateMyCreatorProfile(payload: Record<string, string | null>): Observable<CreatorProfileDto> {
    return this.http.put<CreatorProfileDto>('/api/creator/profile', payload);
  }

  getCreatorProducts(): Observable<ProductCard[]> {
    return this.http.get<ProductCard[]>('/api/creator/products');
  }

  createProduct(formData: FormData): Observable<ProductCard> {
    return this.http.post<ProductCard>('/api/creator/products', formData);
  }

  createBundle(payload: {
    title: string;
    description: string;
    promotionalPrice: number;
    includedProductIds: string[];
    tags?: string;
    videoUrl?: string;
  }): Observable<ProductCard> {
    return this.http.post<ProductCard>('/api/creator/bundles', payload);
  }

  publishNewVersion(productId: string, formData: FormData): Observable<ProductVersion> {
    return this.http.post<ProductVersion>(`/api/creator/products/${productId}/versions`, formData);
  }

  getCreatorDashboard(): Observable<CreatorDashboard> {
    return this.http.get<CreatorDashboard>('/api/creator/dashboard');
  }

  requestPayout(amount: number, pixKey?: string): Observable<PayoutItem> {
    return this.http.post<PayoutItem>('/api/creator/payouts', { amount, pixKey });
  }

  // --- Admin Curation & Split Settings (RF12, RF13) ---
  getAdminOverview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>('/api/admin/overview');
  }

  getAdminCreators(status?: CreatorStatus): Observable<CreatorProfileDto[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<CreatorProfileDto[]>('/api/admin/creators', { params });
  }

  approveCreator(creatorProfileId: string): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>(`/api/admin/creators/${creatorProfileId}/approve`, {});
  }

  rejectCreator(creatorProfileId: string, reason: string): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>(`/api/admin/creators/${creatorProfileId}/reject`, { reason });
  }

  updateTakeRate(takeRatePercent: number): Observable<{ takeRatePercent: number }> {
    return this.http.put<{ takeRatePercent: number }>('/api/admin/settings/take-rate', { takeRatePercent });
  }
}
