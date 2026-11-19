import { Injectable, computed, signal } from '@angular/core';

export interface AudioTrack {
  id: string;
  slug: string;
  title: string;
  creatorName: string;
  creatorSlug: string;
  targetGear: string;
  sourceGear: string;
  price?: number;
  demoAudioUrl: string;
  loudnessLufs?: number;
  bitrateKbps?: number;
}

/**
 * RNF05 (Resiliência do Player Global):
 * Serviço singleton em nível de raiz que mantém o elemento HTMLAudioElement + Web Audio API
 * DynamicsCompressorNode (RNF03 normalização de volume consistente) + AnalyserNode ativos
 * continuamente enquanto o usuário navega entre todas as rotas da plataforma.
 */
@Injectable({ providedIn: 'root' })
export class AudioPlayerService {
  private readonly audio = new Audio();
  private audioCtx: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private rafId: number | null = null;

  readonly currentTrack = signal<AudioTrack | null>(null);
  readonly queue = signal<AudioTrack[]>([]);
  readonly isPlaying = signal(false);
  readonly isLoading = signal(false);
  readonly currentTime = signal(0);
  readonly duration = signal(0);
  readonly volume = signal(0.85);
  readonly muted = signal(false);
  readonly spectrum = signal<number[]>(Array(18).fill(14));

  readonly progressPercent = computed(() => {
    const d = this.duration();
    if (!d || !isFinite(d)) return 0;
    return Math.min(100, Math.max(0, (this.currentTime() / d) * 100));
  });

  readonly hasNext = computed(() => {
    const q = this.queue();
    const cur = this.currentTrack();
    if (!cur || q.length <= 1) return false;
    const idx = q.findIndex(t => t.id === cur.id);
    return idx >= 0 && idx < q.length - 1;
  });

  readonly hasPrev = computed(() => {
    const q = this.queue();
    const cur = this.currentTrack();
    if (!cur || q.length <= 1) return false;
    const idx = q.findIndex(t => t.id === cur.id);
    return idx > 0;
  });

  constructor() {
    this.audio.crossOrigin = 'anonymous';
    this.audio.preload = 'metadata';
    this.audio.volume = this.volume();

    this.audio.addEventListener('timeupdate', () => {
      this.currentTime.set(this.audio.currentTime || 0);
    });
    this.audio.addEventListener('loadedmetadata', () => {
      this.duration.set(isFinite(this.audio.duration) ? this.audio.duration : 0);
      this.isLoading.set(false);
    });
    this.audio.addEventListener('waiting', () => this.isLoading.set(true));
    this.audio.addEventListener('playing', () => {
      this.isLoading.set(false);
      this.isPlaying.set(true);
      this.startVisualizer();
    });
    this.audio.addEventListener('pause', () => {
      this.isPlaying.set(false);
      this.stopVisualizer();
    });
    this.audio.addEventListener('ended', () => {
      this.isPlaying.set(false);
      this.stopVisualizer();
      if (this.hasNext()) {
        this.next();
      }
    });
    this.audio.addEventListener('error', () => {
      this.isLoading.set(false);
      this.isPlaying.set(false);
      this.stopVisualizer();
    });
  }

  isTrackActive(trackId: string): boolean {
    return this.currentTrack()?.id === trackId;
  }

  isTrackPlaying(trackId: string): boolean {
    return this.currentTrack()?.id === trackId && this.isPlaying();
  }

  playTrack(track: AudioTrack, trackList?: AudioTrack[]): void {
    if (trackList && trackList.length > 0) {
      this.queue.set(trackList.filter(t => !!t.demoAudioUrl));
    }

    if (this.currentTrack()?.id === track.id) {
      this.togglePlay();
      return;
    }

    this.initWebAudioStage();
    this.currentTrack.set(track);
    this.currentTime.set(0);
    this.duration.set(0);
    this.isLoading.set(true);

    this.audio.src = track.demoAudioUrl;
    this.audio.load();
    this.audio.play().catch(() => {
      this.isLoading.set(false);
      this.isPlaying.set(false);
    });
  }

  togglePlay(): void {
    if (!this.currentTrack()) return;
    this.initWebAudioStage();
    if (this.audioCtx?.state === 'suspended') {
      this.audioCtx.resume();
    }
    if (this.audio.paused) {
      this.audio.play().catch(() => {});
    } else {
      this.audio.pause();
    }
  }

  seekToPercent(percent: number): void {
    const d = this.duration();
    if (!d || !isFinite(d)) return;
    const target = Math.max(0, Math.min(d, (percent / 100) * d));
    this.audio.currentTime = target;
    this.currentTime.set(target);
  }

  next(): void {
    const q = this.queue();
    const cur = this.currentTrack();
    if (!cur) return;
    const idx = q.findIndex(t => t.id === cur.id);
    if (idx >= 0 && idx < q.length - 1) {
      this.playTrack(q[idx + 1]);
    }
  }

  prev(): void {
    const q = this.queue();
    const cur = this.currentTrack();
    if (!cur) return;
    const idx = q.findIndex(t => t.id === cur.id);
    if (idx > 0) {
      this.playTrack(q[idx - 1]);
    }
  }

  setVolume(val: number): void {
    const clamped = Math.max(0, Math.min(1, val));
    this.volume.set(clamped);
    this.audio.volume = clamped;
    if (clamped > 0 && this.muted()) {
      this.muted.set(false);
      this.audio.muted = false;
    }
  }

  toggleMute(): void {
    const next = !this.muted();
    this.muted.set(next);
    this.audio.muted = next;
  }

  close(): void {
    this.audio.pause();
    this.audio.removeAttribute('src');
    this.currentTrack.set(null);
    this.isPlaying.set(false);
    this.currentTime.set(0);
    this.duration.set(0);
    this.stopVisualizer();
  }

  /**
   * RNF03: Conecta Web Audio DynamicsCompressorNode como malha de segurança de loudness uniforme
   * e AnalyserNode para o medidor visual minimalista.
   */
  private initWebAudioStage(): void {
    if (this.audioCtx) {
      if (this.audioCtx.state === 'suspended') {
        this.audioCtx.resume().catch(() => {});
      }
      return;
    }
    try {
      const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!Ctx) return;
      this.audioCtx = new Ctx();
      const source = this.audioCtx.createMediaElementSource(this.audio);
      const compressor = this.audioCtx.createDynamicsCompressor();
      compressor.threshold.value = -16;
      compressor.knee.value = 18;
      compressor.ratio.value = 3;
      compressor.attack.value = 0.005;
      compressor.release.value = 0.12;

      this.analyser = this.audioCtx.createAnalyser();
      this.analyser.fftSize = 64;

      source.connect(compressor);
      compressor.connect(this.analyser);
      this.analyser.connect(this.audioCtx.destination);
    } catch {
      // Fallback to direct HTML5 audio playback if Web Audio is unavailable
    }
  }

  private startVisualizer(): void {
    if (!this.analyser || this.rafId !== null) return;
    const bins = new Uint8Array(this.analyser.frequencyBinCount);
    const loop = () => {
      if (!this.isPlaying() || !this.analyser) {
        this.rafId = null;
        return;
      }
      this.analyser.getByteFrequencyData(bins);
      const bars: number[] = [];
      for (let i = 0; i < 18; i++) {
        const val = bins[i] ?? 0;
        bars.push(Math.max(12, Math.round((val / 255) * 100)));
      }
      this.spectrum.set(bars);
      this.rafId = requestAnimationFrame(loop);
    };
    this.rafId = requestAnimationFrame(loop);
  }

  private stopVisualizer(): void {
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.spectrum.set(Array(18).fill(14));
  }
}
