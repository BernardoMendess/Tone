import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
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
  PageResponse,
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
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  getPublicConfig(): Observable<{ sandboxEnabled: boolean }> {
    return this.http.get<{ sandboxEnabled: boolean }>('/api/config/public');
  }

  // --- Catalog (RF01, RF02, RF11, A4) ---
  searchProducts(filters: CatalogSearchParams): Observable<PageResponse<ProductCard>> {
    let params = new HttpParams();
    if (filters.q?.trim()) params = params.set('q', filters.q.trim());
    if (filters.genre) params = params.set('genre', filters.genre);
    if (filters.targetGear) params = params.set('targetGear', filters.targetGear);
    if (filters.sourceGear) params = params.set('sourceGear', filters.sourceGear);
    if (filters.pickupType) params = params.set('pickupType', filters.pickupType);
    if (filters.productType) params = params.set('productType', filters.productType);
    if (filters.sort) params = params.set('sort', filters.sort);
    if (filters.page !== undefined) params = params.set('page', String(filters.page));
    if (filters.size !== undefined) params = params.set('size', String(filters.size));
    return this.http.get<PageResponse<ProductCard>>('/api/products', { params });
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

  // --- Checkout & Orders (RF03, RF13, A1) ---
  checkout(paymentMethod: PaymentMethod, idempotencyKey: string): Observable<OrderDto> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<OrderDto>('/api/checkout', { paymentMethod }, { headers });
  }

  getMyOrders(page = 0, size = 20): Observable<PageResponse<OrderDto>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PageResponse<OrderDto>>('/api/orders', { params });
  }

  getOrder(orderId: string): Observable<OrderDto> {
    return this.http.get<OrderDto>(`/api/orders/${orderId}`);
  }

  confirmSandboxPayment(orderId: string): Observable<OrderDto> {
    return this.http.post<OrderDto>(`/api/sandbox/orders/${orderId}/confirm-payment`, {});
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
  getNotifications(page = 0, size = 20): Observable<PageResponse<NotificationItem>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PageResponse<NotificationItem>>('/api/notifications', { params });
  }

  markNotificationRead(id: string): Observable<{ read: boolean }> {
    return this.http.patch<{ read: boolean }>(`/api/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>('/api/notifications/read-all', {});
  }

  // --- Creator Studio (RF07, RF08, RF09, RF10, RF11, RF12, M6) ---
  applyCreator(payload: Record<string, string | null>): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>('/api/creator/apply', payload);
  }

  getMyCreatorProfile(): Observable<CreatorProfileDto> {
    return this.http.get<CreatorProfileDto>('/api/creator/profile');
  }

  updateMyCreatorProfile(payload: Record<string, string | null>): Observable<CreatorProfileDto> {
    return this.http.put<CreatorProfileDto>('/api/creator/profile', payload);
  }

  getCreatorProducts(page = 0, size = 50): Observable<PageResponse<ProductCard>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PageResponse<ProductCard>>('/api/creator/products', { params });
  }

  createProduct(formData: FormData): Observable<ProductCard> {
    return this.http.post<ProductCard>('/api/creator/products', formData);
  }

  updateProduct(productId: string, payload: Record<string, unknown>): Observable<ProductCard> {
    return this.http.patch<ProductCard>(`/api/creator/products/${productId}`, payload);
  }

  deactivateProduct(productId: string): Observable<void> {
    return this.http.delete<void>(`/api/creator/products/${productId}`);
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

  requestPayout(amount: number, idempotencyKey: string): Observable<PayoutItem> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<PayoutItem>('/api/creator/payouts', { amount }, { headers });
  }

  // --- Admin Curation & Split Settings (RF12, RF13, M3, A9) ---
  getAdminOverview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>('/api/admin/overview');
  }

  getAdminCreators(status?: CreatorStatus, page = 0, size = 50): Observable<PageResponse<CreatorProfileDto>> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (status) params = params.set('status', status);
    return this.http.get<PageResponse<CreatorProfileDto>>('/api/admin/creators', { params });
  }

  approveCreator(creatorProfileId: string): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>(`/api/admin/creators/${creatorProfileId}/approve`, {});
  }

  rejectCreator(creatorProfileId: string, reason: string): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>(`/api/admin/creators/${creatorProfileId}/reject`, { reason });
  }

  suspendCreator(creatorProfileId: string, reason: string): Observable<CreatorProfileDto> {
    return this.http.post<CreatorProfileDto>(`/api/admin/creators/${creatorProfileId}/suspend`, { reason });
  }

  refundOrder(orderId: string, reason: string): Observable<OrderDto> {
    return this.http.post<OrderDto>(`/api/admin/orders/${orderId}/refund`, { reason });
  }

  updateTakeRate(takeRatePercent: number): Observable<{ takeRatePercent: number }> {
    return this.http.put<{ takeRatePercent: number }>('/api/admin/settings/take-rate', { takeRatePercent });
  }
}
