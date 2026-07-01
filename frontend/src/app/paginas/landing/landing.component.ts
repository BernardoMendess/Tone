import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.css']
})
export class LandingComponent {
  beneficios = [
    {
      icone: 'upload',
      titulo: 'Venda seus presets',
      descricao: 'Publique e comercialize seus timbres diretamente para outros guitarristas.'
    },
    {
      icone: 'share',
      titulo: 'Compartilhe captures',
      descricao: 'Distribua suas capturas de amplificadores para a comunidade global.'
    },
    {
      icone: 'store',
      titulo: 'Marketplace especializado',
      descricao: 'Plataforma focada exclusivamente em ativos de áudio para guitarra.'
    },
    {
      icone: 'download',
      titulo: 'Downloads instantâneos',
      descricao: 'Acesso imediato ao seu conteúdo logo após a confirmação da compra.'
    },
    {
      icone: 'shield',
      titulo: 'Pagamento seguro',
      descricao: 'Transações protegidas com criptografia de nível bancário.'
    },
    {
      icone: 'search',
      titulo: 'Busca rápida',
      descricao: 'Encontre o timbre perfeito com filtros por categoria, gênero e preço.'
    },
  ];

  passos = [
    {
      numero: '01',
      titulo: 'Criar conta',
      descricao: 'Cadastre-se gratuitamente em menos de 2 minutos.',
      icone: 'user'
    },
    {
      numero: '02',
      titulo: 'Publicar produtos',
      descricao: 'Faça upload dos seus ativos com título, descrição e preço.',
      icone: 'upload'
    },
    {
      numero: '03',
      titulo: 'Receber vendas',
      descricao: 'Acompanhe suas vendas e receba pagamentos diretamente.',
      icone: 'currency'
    },
  ];

  categorias = [
    {
      nome: 'Captures',
      descricao: 'Capturas fiéis de amplificadores reais via tecnologia Neural',
      tag: 'NAM · ToneX · QC',
      cor: 'roxo'
    },
    {
      nome: 'Presets',
      descricao: 'Configurações completas para pedaleiras e processadores',
      tag: 'Helix · Kemper · HX',
      cor: 'verde'
    },
    {
      nome: 'Impulse Responses',
      descricao: 'IRs de gabinetes de alta fidelidade para uso em DAWs e hardware',
      tag: '.WAV · 44.1kHz · 48kHz',
      cor: 'misto'
    },
  ];
}
