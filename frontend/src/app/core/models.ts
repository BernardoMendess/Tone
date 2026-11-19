export type UserRole = 'BUYER' | 'CREATOR' | 'ADMIN';
export type CreatorStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type ProductType = 'PATCH' | 'CAPTURE' | 'IR' | 'BUNDLE';
export type PickupType = 'SINGLE_COIL' | 'HUMBUCKER' | 'P90' | 'ACTIVE' | 'PIEZO' | 'ANY';
export type PaymentMethod = 'PIX' | 'CREDIT_CARD';
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'FAILED' | 'EXPIRED' | 'REFUNDED';
export type SplitStatus = 'SCHEDULED' | 'SETTLED' | 'REFUNDED';
export type PayoutStatus = 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface UserInfo {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  creatorProfileId: string | null;
  creatorSlug: string | null;
  creatorStatus: CreatorStatus | null;
  unreadNotifications: number;
}

export interface AuthResponse {
  token: string;
  user: UserInfo;
}

export interface CreatorSummary {
  id: string;
  displayName: string;
  slug: string;
  avatarUrl: string | null;
  mainGear: string | null;
}

export interface ProductCard {
  id: string;
  title: string;
  slug: string;
  description: string;
  price: number;
  originalBundlePrice: number | null;
  discountPercent: number | null;
  productType: ProductType;
  genre: string;
  targetGear: string;
  sourceGear: string;
  pickupType: PickupType;
  tags: string[];
  demoAudioUrl: string | null;
  demoDurationSeconds: number;
  demoLoudnessLufs: number;
  demoBitrateKbps: number;
  currentVersion: string;
  averageRating: number;
  reviewCount: number;
  salesCount: number;
  includedCount: number;
  creator: CreatorSummary;
  createdAt: string;
}

export interface ProductVersion {
  id: string;
  versionNumber: string;
  changelog: string;
  originalFilename: string;
  fileSizeBytes: number;
  createdAt: string;
}

export interface ReviewItem {
  id: string;
  userId: string;
  userName: string;
  rating: number;
  comment: string;
  verifiedPurchase: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductDetail {
  product: ProductCard;
  videoUrl: string | null;
  versions: ProductVersion[];
  bundleItems: ProductCard[];
  reviews: ReviewItem[];
  ownedByCurrentUser: boolean;
  wishlistedByCurrentUser: boolean;
  canReview: boolean;
}

export interface FilterOptions {
  genres: string[];
  targetGears: string[];
  sourceGears: string[];
  pickupTypes: PickupType[];
  productTypes: ProductType[];
}

export interface PublicCreatorProfile {
  id: string;
  displayName: string;
  slug: string;
  bio: string | null;
  avatarUrl: string | null;
  mainGear: string | null;
  youtubeUrl: string | null;
  instagramUrl: string | null;
  websiteUrl: string | null;
  totalProducts: number;
  totalSales: number;
  averageRating: number;
  products: ProductCard[];
}

export interface CartItem {
  productId: string;
  product: ProductCard;
  addedAt: string;
}

export interface CartDto {
  items: CartItem[];
  count: number;
  subtotal: number;
  takeRatePercent: number;
}

export interface OrderItemDto {
  id: string;
  productId: string;
  productSlug: string;
  title: string;
  creatorName: string;
  purchasedVersion: string;
  latestVersion: string;
  unitPrice: number;
  platformFeeAmount: number;
  creatorNetAmount: number;
}

export interface PaymentSplitDto {
  id: string;
  creatorId: string;
  creatorName: string;
  grossAmount: number;
  platformFeeAmount: number;
  creatorNetAmount: number;
  takeRatePercent: number;
  status: SplitStatus;
  gatewaySplitReference: string;
}

export interface OrderDto {
  id: string;
  orderCode: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  totalAmount: number;
  platformFeeTotal: number;
  creatorNetTotal: number;
  gatewayTransactionId: string;
  pixCopyPaste: string | null;
  pixExpiresAt: string | null;
  paidAt: string | null;
  createdAt: string;
  items: OrderItemDto[];
  splits: PaymentSplitDto[];
}

export interface LibraryItem {
  productId: string;
  slug: string;
  title: string;
  productType: string;
  targetGear: string;
  sourceGear: string;
  creatorName: string;
  creatorSlug: string;
  demoAudioUrl: string | null;
  purchasedVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  latestChangelog: string | null;
  unlockedViaBundleTitle: string | null;
  purchasedAt: string;
  versions: ProductVersion[];
}

export interface PresignedDownload {
  downloadUrl: string;
  filename: string;
  expiresAt: string;
  storageMode: string;
  versionNumber: string;
}

export interface NotificationItem {
  id: string;
  type: 'PATCH_UPDATE' | 'CREATOR_APPROVED' | 'CREATOR_REJECTED' | 'SALE_COMPLETED';
  title: string;
  message: string;
  relatedProductId: string | null;
  read: boolean;
  createdAt: string;
}

export interface CreatorProfileDto {
  id: string;
  userId: string;
  userFullName: string;
  userEmail: string;
  displayName: string;
  slug: string;
  bio: string | null;
  avatarUrl: string | null;
  mainGear: string | null;
  youtubeUrl: string | null;
  instagramUrl: string | null;
  websiteUrl: string | null;
  portfolioDemoUrl: string | null;
  pixKey: string | null;
  status: CreatorStatus;
  rejectionReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export interface SaleRecord {
  orderId: string;
  orderCode: string;
  productTitle: string;
  buyerName: string;
  grossAmount: number;
  platformFeeAmount: number;
  creatorNetAmount: number;
  takeRatePercent: number;
  paidAt: string;
}

export interface MonthlyRevenuePoint {
  month: string;
  gross: number;
  net: number;
  salesCount: number;
}

export interface PayoutItem {
  id: string;
  amount: number;
  pixKey: string;
  status: PayoutStatus;
  requestedAt: string;
  completedAt: string | null;
}

export interface CreatorDashboard {
  totalSalesCount: number;
  activeProductsCount: number;
  grossRevenue: number;
  platformFeeRetained: number;
  netRevenueTotal: number;
  totalWithdrawn: number;
  availableBalance: number;
  currentTakeRatePercent: number;
  monthlySeries: MonthlyRevenuePoint[];
  recentSales: SaleRecord[];
  payouts: PayoutItem[];
}

export interface AdminOverview {
  totalUsers: number;
  approvedCreators: number;
  pendingCreators: number;
  activeProducts: number;
  paidOrdersCount: number;
  gmvTotal: number;
  platformRevenueTotal: number;
  currentTakeRatePercent: number;
  pendingQueue: CreatorProfileDto[];
}

export const PICKUP_LABELS: Record<PickupType, string> = {
  SINGLE_COIL: 'Single Coil',
  HUMBUCKER: 'Humbucker',
  P90: 'P90',
  ACTIVE: 'Ativo (EMG/Fishman)',
  PIEZO: 'Piezo / Acústico',
  ANY: 'Qualquer Captação'
};

export const PRODUCT_TYPE_LABELS: Record<ProductType, string> = {
  PATCH: 'Preset / Patch',
  CAPTURE: 'Captura Neural',
  IR: 'Impulse Response (IR)',
  BUNDLE: 'Pacote (Bundle)'
};

export function formatBRL(value: number | null | undefined): string {
  const num = Number(value ?? 0);
  return num.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function formatDuration(seconds: number | null | undefined): string {
  const s = Math.max(0, Math.floor(seconds ?? 0));
  const mins = Math.floor(s / 60);
  const secs = s % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}
