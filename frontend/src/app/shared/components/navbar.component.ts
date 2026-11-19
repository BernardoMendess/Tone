import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { WishlistService } from '../../core/services/wishlist.service';
import { ApiService } from '../../core/services/api.service';
import { NotificationItem } from '../../core/models';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <header class="nav-shell">
      <div class="container nav-inner">
        <a routerLink="/" class="brand" (click)="closeMenus()">
          <span class="brand-mark" aria-hidden="true">
            <i></i><i></i><i></i><i></i>
          </span>
          <span class="brand-name">Tone</span>
          <span class="brand-tag mono">AUDIO MARKET</span>
        </a>

        <nav class="nav-links" [class.open]="mobileMenuOpen()">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" (click)="closeMenus()">Explorar</a>
          @if (auth.isLoggedIn()) {
            <a routerLink="/library" routerLinkActive="active" (click)="closeMenus()">Biblioteca</a>
            <a routerLink="/wishlist" routerLinkActive="active" (click)="closeMenus()">Favoritos</a>
            <a routerLink="/studio" routerLinkActive="active" (click)="closeMenus()">
              Estúdio
              @if (auth.user()?.creatorStatus === 'PENDING') {
                <span class="dot-pending" title="Curadoria pendente"></span>
              }
            </a>
            @if (auth.isAdmin()) {
              <a routerLink="/admin" routerLinkActive="active" (click)="closeMenus()">Curadoria Admin</a>
            }
          }
        </nav>

        <div class="nav-actions">
          @if (auth.isLoggedIn()) {
            <!-- Notifications bell (RF09 patch updates & RF12 creator approval) -->
            <div class="notif-wrap">
              <button class="icon-btn" (click)="toggleNotifications()" aria-label="Notificações">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                </svg>
                @if (auth.unreadNotifications() > 0) {
                  <span class="count-pill">{{ auth.unreadNotifications() }}</span>
                }
              </button>

              @if (notifOpen()) {
                <div class="notif-panel card">
                  <div class="notif-head">
                    <span>Notificações</span>
                    @if (notifications().length > 0) {
                      <button class="link-sm" (click)="markAllRead()">Marcar todas lidas</button>
                    }
                  </div>
                  @if (notifications().length === 0) {
                    <div class="notif-empty">Nenhuma notificação recente.</div>
                  } @else {
                    <div class="notif-list">
                      @for (n of notifications(); track n.id) {
                        <div class="notif-item" [class.unread]="!n.read" (click)="openNotification(n)">
                          <div class="notif-title">{{ n.title }}</div>
                          <div class="notif-msg">{{ n.message }}</div>
                        </div>
                      }
                    </div>
                  }
                </div>
              }
            </div>

            <!-- Cart button -->
            <a routerLink="/checkout" class="icon-btn" aria-label="Carrinho" (click)="closeMenus()">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
              </svg>
              @if (cart.count() > 0) {
                <span class="count-pill accent">{{ cart.count() }}</span>
              }
            </a>

            <div class="user-chip">
              <span class="user-name">{{ auth.user()?.fullName }}</span>
              <button class="btn btn-ghost btn-sm" (click)="logout()">Sair</button>
            </div>
          } @else {
            <a routerLink="/auth" class="btn btn-primary btn-sm" (click)="closeMenus()">Entrar</a>
          }

          <button class="icon-btn mobile-toggle" (click)="mobileMenuOpen.set(!mobileMenuOpen())" aria-label="Menu">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
        </div>
      </div>
    </header>
  `,
  styles: [`
    .nav-shell {
      position: sticky;
      top: 0;
      z-index: 50;
      background: rgba(255, 255, 255, 0.94);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid var(--border);
    }
    .nav-inner {
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      font-weight: 600;
      letter-spacing: -0.02em;
    }
    .brand-mark {
      display: inline-flex;
      align-items: flex-end;
      gap: 2.5px;
      height: 18px;
      padding: 2px 5px;
      background: var(--bg-dark);
      border-radius: 4px;
      i {
        display: block;
        width: 2.5px;
        border-radius: 1px;
        background: #fff;
        &:nth-child(1) { height: 7px; background: var(--accent); }
        &:nth-child(2) { height: 13px; }
        &:nth-child(3) { height: 10px; background: var(--accent); }
        &:nth-child(4) { height: 6px; }
      }
    }
    .brand-name { font-size: 16px; color: var(--ink); }
    .brand-tag {
      display: none;
      font-size: 10px;
      color: var(--ink-muted);
      padding: 2px 6px;
      border: 1px solid var(--border);
      border-radius: 4px;
      @media (min-width: 768px) { display: inline-block; }
    }

    .nav-links {
      display: none;
      align-items: center;
      gap: 6px;
      a {
        position: relative;
        padding: 6px 11px;
        border-radius: 6px;
        font-size: 13.5px;
        font-weight: 500;
        color: var(--ink-muted);
        transition: all 0.15s;
        &:hover { color: var(--ink); background: var(--bg-subtle); }
        &.active { color: var(--ink); background: var(--bg-subtle); }
      }
      @media (min-width: 900px) { display: flex; }
      &.open {
        display: flex;
        flex-direction: column;
        align-items: stretch;
        position: absolute;
        top: 60px;
        left: 0;
        right: 0;
        background: var(--bg-surface);
        border-bottom: 1px solid var(--border);
        padding: 12px 16px;
        box-shadow: var(--shadow-md);
      }
    }
    .dot-pending {
      display: inline-block;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--warning);
      margin-left: 4px;
      vertical-align: middle;
    }

    .nav-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .icon-btn {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 6px;
      border: 1px solid transparent;
      background: transparent;
      color: var(--ink-secondary);
      cursor: pointer;
      &:hover { background: var(--bg-subtle); color: var(--ink); }
    }
    .count-pill {
      position: absolute;
      top: 2px;
      right: 2px;
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      border-radius: 99px;
      background: var(--bg-dark);
      color: #fff;
      font-size: 10px;
      font-family: var(--font-mono);
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      &.accent { background: var(--accent); }
    }
    .user-chip {
      display: none;
      align-items: center;
      gap: 8px;
      padding-left: 8px;
      border-left: 1px solid var(--border);
      @media (min-width: 768px) { display: flex; }
    }
    .user-name {
      font-size: 13px;
      font-weight: 500;
      color: var(--ink-secondary);
      max-width: 130px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .mobile-toggle {
      @media (min-width: 900px) { display: none; }
    }

    .notif-wrap { position: relative; }
    .notif-panel {
      position: absolute;
      right: 0;
      top: 44px;
      width: 320px;
      max-width: calc(100vw - 32px);
      max-height: 400px;
      overflow-y: auto;
      z-index: 80;
      box-shadow: var(--shadow-md);
    }
    .notif-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 14px;
      border-bottom: 1px solid var(--border);
      font-size: 13px;
      font-weight: 600;
    }
    .link-sm {
      background: none;
      border: none;
      color: var(--accent);
      font-size: 12px;
      cursor: pointer;
    }
    .notif-empty {
      padding: 20px 14px;
      text-align: center;
      font-size: 13px;
      color: var(--ink-muted);
    }
    .notif-item {
      padding: 10px 14px;
      border-bottom: 1px solid var(--border);
      cursor: pointer;
      &:hover { background: var(--bg-subtle); }
      &.unread { background: var(--accent-soft); }
      &:last-child { border-bottom: none; }
    }
    .notif-title { font-size: 12.5px; font-weight: 600; margin-bottom: 2px; }
    .notif-msg { font-size: 12px; color: var(--ink-secondary); line-height: 1.4; }
  `]
})
export class NavbarComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly cart = inject(CartService);
  private readonly wishlist = inject(WishlistService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly mobileMenuOpen = signal(false);
  readonly notifOpen = signal(false);
  readonly notifications = signal<NotificationItem[]>([]);

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) {
      this.auth.refreshMe().subscribe({ error: () => {} });
      this.cart.refresh();
      this.wishlist.refresh();
    }
  }

  toggleNotifications(): void {
    const next = !this.notifOpen();
    this.notifOpen.set(next);
    if (next) {
      this.api.getNotifications().subscribe(page => this.notifications.set(page.content));
    }
  }

  openNotification(n: NotificationItem): void {
    if (!n.read) {
      this.api.markNotificationRead(n.id).subscribe(() => {
        this.notifications.update(list => list.map(i => i.id === n.id ? { ...i, read: true } : i));
        this.auth.refreshMe().subscribe();
      });
    }
    this.notifOpen.set(false);
    if (n.type === 'PATCH_UPDATE') {
      this.router.navigate(['/library']);
    } else if (n.type === 'CREATOR_APPROVED' || n.type === 'SALE_COMPLETED') {
      this.router.navigate(['/studio']);
    }
  }

  markAllRead(): void {
    this.api.markAllNotificationsRead().subscribe(() => {
      this.notifications.update(list => list.map(i => ({ ...i, read: true })));
      this.auth.refreshMe().subscribe();
    });
  }

  closeMenus(): void {
    this.mobileMenuOpen.set(false);
    this.notifOpen.set(false);
  }

  logout(): void {
    this.auth.logout();
    this.cart.reset();
    this.wishlist.reset();
    this.closeMenus();
    this.router.navigate(['/']);
  }
}
