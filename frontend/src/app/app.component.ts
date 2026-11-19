import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar.component';
import { GlobalPlayerComponent } from './shared/components/global-player.component';
import { AudioPlayerService } from './core/services/audio-player.service';
import { ToastService } from './core/services/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, GlobalPlayerComponent],
  template: `
    <app-navbar></app-navbar>

    <main class="main-shell" [class.with-player]="!!player.currentTrack()">
      <router-outlet></router-outlet>
    </main>

    <!-- Persistent global audio player (RNF05) -->
    <app-global-player></app-global-player>

    <!-- Toast Notifications -->
    <div class="toast-stack" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast-item" [class]="t.type" (click)="toast.dismiss(t.id)">
          <span>{{ t.text }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .main-shell {
      min-height: calc(100vh - 60px);
      padding-bottom: 48px;
      &.with-player {
        padding-bottom: 112px;
      }
    }
    .toast-stack {
      position: fixed;
      top: 72px;
      right: 16px;
      z-index: 120;
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-width: 340px;
    }
    .toast-item {
      padding: 11px 14px;
      border-radius: var(--radius-sm);
      background: var(--bg-dark);
      color: #fff;
      font-size: 13px;
      box-shadow: var(--shadow-md);
      cursor: pointer;
      border-left: 3px solid var(--accent);
      &.success { border-left-color: #22c55e; }
      &.error { border-left-color: #ef4444; }
    }
  `]
})
export class AppComponent {
  readonly player = inject(AudioPlayerService);
  readonly toast = inject(ToastService);
}
