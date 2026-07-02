import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { PerfilService } from '../../servicos/perfil.service';
import { ProdutoService } from '../../servicos/produto.service';
import { AuthService } from '../../servicos/auth.service';
import { PerfilCriadorResposta } from '../../modelos/perfil.model';
import { Usuario } from '../../modelos/usuario.model';

@Component({
  selector: 'app-pagina-criador',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './pagina-criador.component.html',
  styleUrls: ['./pagina-criador.component.css']
})
export class PaginaCriadorComponent implements OnInit {
  perfil: PerfilCriadorResposta | null = null;
  produtos: any[] = [];
  carregando = true;
  erro = '';
  usuarioLogado: Partial<Usuario> | null = null;

  constructor(
    private route: ActivatedRoute,
    private perfilService: PerfilService,
    private produtoService: ProdutoService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const criadorId = idParam ? Number(idParam) : null;

    if (!criadorId || isNaN(criadorId)) {
      this.erro = 'Identificador de criador inválido.';
      this.carregando = false;
      return;
    }

    // Carrega o usuário logado para verificar se é dono do perfil
    this.authService.obterUsuarioLogado().subscribe({
      next: (usuario) => { this.usuarioLogado = usuario; },
      error: () => { this.usuarioLogado = null; }
    });

    this.carregarDados(criadorId);
  }

  private carregarDados(criadorId: number): void {
    this.carregando = true;
    this.erro = '';

    // 1. Obter informações do perfil do criador
    this.perfilService.obterPerfilPublico(criadorId).subscribe({
      next: (perfil) => {
        this.perfil = perfil;
        
        // 2. Obter portfólio (produtos publicados) do criador
        this.produtoService.listarProdutosPorCriador(criadorId).subscribe({
          next: (produtos) => {
            this.produtos = produtos;
            this.carregando = false;
          },
          error: (err) => {
            console.error('Erro ao carregar portfólio:', err);
            // Mesmo se falhar a listagem de produtos, mostramos o perfil do criador
            this.produtos = [];
            this.carregando = false;
          }
        });
      },
      error: (err) => {
        console.error('Erro ao carregar perfil público:', err);
        this.erro = 'Não foi possível carregar a página deste artista. Verifique se o link está correto.';
        this.carregando = false;
      }
    });
  }

  /** Retorna true se o usuário logado é o dono deste perfil */
  get ehDono(): boolean {
    return !!(this.usuarioLogado && this.perfil && this.usuarioLogado.id === this.perfil.usuarioId);
  }

  obterIniciais(): string {
    if (!this.perfil || !this.perfil.nomeCompleto) return 'A';
    return this.perfil.nomeCompleto
      .split(' ')
      .slice(0, 2)
      .map(n => n[0])
      .join('')
      .toUpperCase();
  }

  temRedesSociais(): boolean {
    if (!this.perfil) return false;
    return !!(
      this.perfil.instagramUrl ||
      this.perfil.youtubeUrl ||
      this.perfil.soundcloudUrl ||
      this.perfil.sitePessoalUrl
    );
  }

  verDetalhesProduto(produtoId: string): void {
    // Redireciona para a página de detalhes do produto
    this.router.navigate(['/produtos', produtoId]);
  }

  voltarHome(): void {
    this.router.navigate(['/']);
  }
}
