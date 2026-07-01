import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type VarianteAlerta = 'sucesso' | 'erro' | 'aviso' | 'info';

@Component({
  selector: 'app-alerta',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './alerta.component.html',
  styleUrls: ['./alerta.component.css']
})
export class AlertaComponent {
  @Input() tipo: VarianteAlerta = 'info';
  @Input() mensagem = '';
  @Input() icone = true;
}
