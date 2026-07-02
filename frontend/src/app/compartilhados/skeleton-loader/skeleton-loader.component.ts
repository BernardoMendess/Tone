import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="skeleton"
      [class.skeleton--texto]="tipo === 'texto'"
      [class.skeleton--card]="tipo === 'card'"
      [class.skeleton--avatar]="tipo === 'avatar'"
      [class.skeleton--linha]="tipo === 'linha'"
      [style.width]="largura || null"
      [style.height]="altura || null"
      role="status"
      aria-label="Carregando..."
    ></div>
  `,
  styleUrls: ['./skeleton-loader.component.css']
})
export class SkeletonLoaderComponent {
  @Input() tipo: 'texto' | 'card' | 'avatar' | 'linha' = 'linha';
  @Input() largura?: string;
  @Input() altura?: string;
}
