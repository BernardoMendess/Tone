import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AudioPlayerService } from '../../core/services/audio-player.service';
import { formatDuration } from '../../core/models';

/**
 * RNF05 (Resiliência do Player Global):
 * Montado no shell raiz fora do <router-outlet>, mantendo a reprodução contínua e ininterrupta
 * enquanto o usuário navega entre páginas de catálogo, detalhes, carrinho, biblioteca e estúdio.
 */
@Component({
  selector: 'app-global-player',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    @if (player.currentTrack(); as track) {
      <aside class="player-dock" role="region" aria-label="Player de Demonstração Global">
        <!-- Interactive top seek bar -->
        <div class="seek-rail" (click)="onSeek($event)" title="Clique para avançar ou retroceder">
          <div class="seek-fill" [style.width.%]="player.progressPercent()"></div>
        </div>

        <div class="container player-inner">
          <!-- Controls -->
          <div class="controls">
            <button class="ctrl-btn" [disabled]="!player.hasPrev()" (click)="player.prev()" aria-label="Anterior">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z"/></svg>
            </button>
            <button class="play-btn" (click)="player.togglePlay()" [attr.aria-label]="player.isPlaying() ? 'Pausar' : 'Tocar'">
              @if (player.isLoading()) {
                <span class="spinner"></span>
              } @else if (player.isPlaying()) {
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
              } @else {
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="6,4 20,12 6,20"/></svg>
              }
            </button>
            <button class="ctrl-btn" [disabled]="!player.hasNext()" (click)="player.next()" aria-label="Próximo">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
            </button>
          </div>

          <!-- Track metadata -->
          <div class="track-info">
            <a [routerLink]="['/tones', track.slug]" class="track-title">{{ track.title }}</a>
            <div class="track-sub">
              <span>{{ track.creatorName }}</span>
              <span class="sep">·</span>
              <span class="mono">{{ track.targetGear }}</span>
              <span class="sep hide-sm">·</span>
              <span class="mono hide-sm">{{ track.sourceGear }}</span>
            </div>
          </div>

          <!-- Live Minimalist Spectrum + Time + LUFS telemetry (RNF03 / RNF04) -->
          <div class="telemetry hide-sm">
            <div class="spectrum" aria-hidden="true">
              @for (bar of player.spectrum(); track $index) {
                <span [style.height.%]="bar" [class.active]="player.isPlaying()"></span>
              }
            </div>
            <div class="time-readout mono">
              {{ fmt(player.currentTime()) }} / {{ fmt(player.duration()) }}
            </div>
            <span class="lufs-chip mono" title="Áudio normalizado em -14 LUFS e stream comprimido anti-ripping">
              {{ track.loudnessLufs ?? -14 }} LUFS · {{ track.bitrateKbps ?? 96 }}k
            </span>
          </div>

          <!-- Volume & Close -->
          <div class="right-controls">
            <div class="vol-wrap hide-sm">
              <button class="ctrl-btn" (click)="player.toggleMute()" aria-label="Silenciar">
                @if (player.muted() || player.volume() === 0) {
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/></svg>
                } @else {
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
                }
              </button>
              <input
                type="range"
                min="0"
                max="1"
                step="0.02"
                [value]="player.muted() ? 0 : player.volume()"
                (input)="onVolume($event)"
                class="vol-slider"
                aria-label="Volume"
              />
            </div>

            <button class="ctrl-btn" (click)="player.close()" title="Fechar player" aria-label="Fechar player">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </div>
      </aside>
    }
  `,
  styles: [`
    .player-dock {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: 90;
      background: var(--bg-dark);
      color: var(--ink-inverse);
      border-top: 1px solid #27272a;
      box-shadow: 0 -6px 24px rgba(0, 0, 0, 0.18);
    }
    .seek-rail {
      height: 4px;
      background: #27272a;
      cursor: pointer;
      position: relative;
      &:hover { height: 6px; }
    }
    .seek-fill {
      height: 100%;
      background: var(--accent);
      transition: width 0.1s linear;
    }
    .player-inner {
      height: 64px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
    }
    .controls {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-shrink: 0;
    }
    .play-btn {
      width: 38px;
      height: 38px;
      border-radius: 50%;
      border: none;
      background: var(--accent);
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      &:hover { background: var(--accent-hover); }
    }
    .ctrl-btn {
      width: 30px;
      height: 30px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: #d4d4d8;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      &:hover:not(:disabled) { color: #fff; background: #27272a; }
      &:disabled { opacity: 0.3; cursor: not-allowed; }
    }
    .track-info {
      min-width: 0;
      flex: 1;
    }
    .track-title {
      display: block;
      font-size: 13.5px;
      font-weight: 600;
      color: #fff;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      &:hover { text-decoration: underline; }
    }
    .track-sub {
      font-size: 11.5px;
      color: #a1a1aa;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      .sep { margin: 0 5px; }
    }
    .telemetry {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .spectrum {
      display: flex;
      align-items: flex-end;
      gap: 2px;
      height: 22px;
      width: 72px;
      span {
        flex: 1;
        background: #52525b;
        border-radius: 1px;
        min-height: 3px;
        transition: height 0.08s ease;
        &.active { background: var(--accent); }
      }
    }
    .time-readout {
      font-size: 11.5px;
      color: #d4d4d8;
      min-width: 76px;
      text-align: center;
    }
    .lufs-chip {
      font-size: 10.5px;
      padding: 2px 7px;
      border-radius: 4px;
      background: #1f2127;
      border: 1px solid #30323a;
      color: #a1a1aa;
    }
    .right-controls {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }
    .vol-wrap {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .vol-slider {
      width: 72px;
      accent-color: var(--accent);
      cursor: pointer;
    }
    .spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .hide-sm {
      display: none;
      @media (min-width: 820px) { display: inline-flex; }
    }
  `]
})
export class GlobalPlayerComponent {
  readonly player = inject(AudioPlayerService);
  readonly fmt = formatDuration;

  onSeek(event: MouseEvent): void {
    const el = event.currentTarget as HTMLElement;
    const rect = el.getBoundingClientRect();
    const pct = ((event.clientX - rect.left) / rect.width) * 100;
    this.player.seekToPercent(pct);
  }

  onVolume(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.player.setVolume(parseFloat(input.value));
  }
}
