import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Renderizador minimalista determinístico de matriz QR (EMVCo PIX) + Copia e Cola.
 */
@Component({
  selector: 'app-pix-qr',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pix-box">
      <div class="qr-frame" aria-label="QR Code PIX">
        <svg viewBox="0 0 21 21" width="168" height="168" shape-rendering="crispEdges">
          <rect width="21" height="21" fill="#ffffff"/>
          @for (cell of cells(); track $index) {
            @if (cell.on) {
              <rect [attr.x]="cell.x" [attr.y]="cell.y" width="1" height="1" fill="#111215"/>
            }
          }
        </svg>
      </div>

      <div class="copy-area">
        <label class="mono">PIX COPIA E COLA (EMVCo BR CODE)</label>
        <div class="code-row">
          <input type="text" readonly [value]="payload" class="input mono code-input"/>
          <button type="button" class="btn btn-outline btn-sm" (click)="copyCode()">
            {{ copied() ? 'Copiado!' : 'Copiar' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pix-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background: var(--bg-subtle);
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
    }
    .qr-frame {
      padding: 12px;
      background: #ffffff;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
    }
    .copy-area {
      width: 100%;
      label {
        display: block;
        font-size: 11px;
        color: var(--ink-muted);
        margin-bottom: 6px;
        letter-spacing: 0.04em;
      }
    }
    .code-row {
      display: flex;
      gap: 8px;
    }
    .code-input {
      font-size: 11.5px;
      background: #fff;
    }
  `]
})
export class PixQrComponent {
  private readonly _payload = signal('');
  readonly copied = signal(false);

  @Input({ required: true })
  set payload(val: string) {
    this._payload.set(val || '');
  }
  get payload(): string {
    return this._payload();
  }

  readonly cells = computed(() => {
    const str = this._payload();
    let seed = 2166136261;
    for (let i = 0; i < str.length; i++) {
      seed ^= str.charCodeAt(i);
      seed = Math.imul(seed, 16777619);
    }

    const size = 21;
    const out: { x: number; y: number; on: boolean }[] = [];
    const isFinder = (x: number, y: number, ox: number, oy: number) => {
      const dx = x - ox;
      const dy = y - oy;
      if (dx < 0 || dx > 6 || dy < 0 || dy > 6) return null;
      if (dx === 0 || dx === 6 || dy === 0 || dy === 6) return true;
      if (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4) return true;
      return false;
    };

    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const f1 = isFinder(x, y, 0, 0);
        const f2 = isFinder(x, y, 14, 0);
        const f3 = isFinder(x, y, 0, 14);
        if (f1 !== null) {
          out.push({ x, y, on: f1 });
        } else if (f2 !== null) {
          out.push({ x, y, on: f2 });
        } else if (f3 !== null) {
          out.push({ x, y, on: f3 });
        } else if ((x === 7 && y < 8) || (y === 7 && x < 8) || (x === 13 && y < 8) || (y === 7 && x > 12) || (y === 13 && x < 8) || (x === 7 && y > 12)) {
          out.push({ x, y, on: false });
        } else {
          seed ^= seed << 13;
          seed ^= seed >>> 17;
          seed ^= seed << 5;
          out.push({ x, y, on: (seed & 1) === 1 });
        }
      }
    }
    return out;
  });

  copyCode(): void {
    if (!this.payload) return;
    navigator.clipboard?.writeText(this.payload).catch(() => {});
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 2200);
  }
}
