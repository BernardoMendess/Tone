import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { PerfilService } from '../../servicos/perfil.service';
import { AuthService } from '../../servicos/auth.service';
import { PerfilCriadorResposta, PerfilCriadorRequisicao } from '../../modelos/perfil.model';

@Component({
  selector: 'app-editar-perfil',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './editar-perfil.component.html',
  styleUrls: ['./editar-perfil.component.css']
})
export class EditarPerfilComponent implements OnInit {
  perfilForm: FormGroup;
  arquivoSelecionado: File | null = null;
  previewUrl: string | null = null;
  perfilAtual: PerfilCriadorResposta | null = null;
  nomeUsuario = '';
  
  statusEnvio: 'IDLE' | 'SOLICITANDO_URL' | 'FAZENDO_UPLOAD' | 'SALVANDO_PERFIL' = 'IDLE';
  mensagemErro = '';
  mensagemSucesso = '';

  constructor(
    private fb: FormBuilder,
    private perfilService: PerfilService,
    private authService: AuthService,
    private router: Router
  ) {
    // Regex simples para validar se inicia com http:// ou https://
    const urlPattern = /^(https?:\/\/).+$/;

    this.perfilForm = this.fb.group({
      biografia: ['', [Validators.maxLength(1000)]],
      instagramUrl: ['', [Validators.pattern(urlPattern)]],
      youtubeUrl: ['', [Validators.pattern(urlPattern)]],
      soundcloudUrl: ['', [Validators.pattern(urlPattern)]],
      sitePessoalUrl: ['', [Validators.pattern(urlPattern)]]
    });
  }

  ngOnInit(): void {
    // 1. Obter dados do usuário logado (para exibir iniciais se não tiver foto)
    this.authService.obterUsuarioLogado().subscribe({
      next: (usuario) => {
        this.nomeUsuario = usuario.nomeCompleto || '';
      }
    });

    // 2. Carregar dados do perfil
    this.perfilService.obterMeuPerfil().subscribe({
      next: (perfil) => {
        this.perfilAtual = perfil;
        this.perfilForm.patchValue({
          biografia: perfil.biografia || '',
          instagramUrl: perfil.instagramUrl || '',
          youtubeUrl: perfil.youtubeUrl || '',
          soundcloudUrl: perfil.soundcloudUrl || '',
          sitePessoalUrl: perfil.sitePessoalUrl || ''
        });
      },
      error: (err) => {
        this.mensagemErro = 'Não foi possível carregar os dados do seu perfil.';
        console.error(err);
      }
    });
  }

  get caracteresRestantes(): number {
    const bio = this.perfilForm.get('biografia')?.value || '';
    return 1000 - bio.length;
  }

  obterIniciais(): string {
    if (!this.nomeUsuario) return 'PC';
    return this.nomeUsuario
      .split(' ')
      .slice(0, 2)
      .map(n => n[0])
      .join('')
      .toUpperCase();
  }

  obterUrlCompletaFoto(): string | null {
    return this.perfilAtual?.fotoPerfilUrl || null;
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0] as File;
    if (file) {
      // Validar tamanho (5MB)
      if (file.size > 5 * 1024 * 1024) {
        this.mensagemErro = 'A imagem excede o limite permitido de 5MB.';
        this.arquivoSelecionado = null;
        this.previewUrl = null;
        return;
      }

      this.mensagemErro = '';
      this.arquivoSelecionado = file;

      // Gerar preview local
      const reader = new FileReader();
      reader.onload = () => {
        this.previewUrl = reader.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  obterTextoBotao(): string {
    switch (this.statusEnvio) {
      case 'SOLICITANDO_URL': return 'Solicitando permissão...';
      case 'FAZENDO_UPLOAD': return 'Enviando imagem...';
      case 'SALVANDO_PERFIL': return 'Salvando perfil...';
      default: return 'Salvar Alterações';
    }
  }

  cancelar(): void {
    this.router.navigate(['/']);
  }

  onSubmit(): void {
    if (this.perfilForm.invalid || this.statusEnvio !== 'IDLE') {
      return;
    }

    this.mensagemErro = '';
    this.mensagemSucesso = '';

    const formValue = this.perfilForm.value;
    const dadosPerfil: PerfilCriadorRequisicao = {
      biografia: formValue.biografia || '',
      instagramUrl: formValue.instagramUrl || '',
      youtubeUrl: formValue.youtubeUrl || '',
      soundcloudUrl: formValue.soundcloudUrl || '',
      sitePessoalUrl: formValue.sitePessoalUrl || '',
      fotoPerfilUrl: this.perfilAtual?.fotoPerfilUrl || '' // Mantém o anterior por padrão se não houver upload
    };

    if (this.arquivoSelecionado) {
      // Executa o upload antes de salvar o perfil
      this.statusEnvio = 'SOLICITANDO_URL';
      this.perfilService.solicitarUrlUpload(this.arquivoSelecionado.type).subscribe({
        next: (resS3) => {
          this.statusEnvio = 'FAZENDO_UPLOAD';
          
          this.perfilService.fazerUploadFoto(resS3.uploadUrl, this.arquivoSelecionado!).subscribe({
            next: () => {
              this.statusEnvio = 'SALVANDO_PERFIL';
              dadosPerfil.fotoPerfilUrl = resS3.fotoPerfilUrl; // nova key do S3

              this.salvarDadosPerfil(dadosPerfil);
            },
            error: (err) => {
              this.statusEnvio = 'IDLE';
              this.mensagemErro = 'Erro ao enviar a foto de perfil para o servidor de armazenamento.';
              console.error(err);
            }
          });
        },
        error: (err) => {
          this.statusEnvio = 'IDLE';
          this.mensagemErro = err.error?.message || 'Erro ao gerar credenciais de upload de imagem.';
          console.error(err);
        }
      });
    } else {
      // Apenas atualiza dados textuais
      this.statusEnvio = 'SALVANDO_PERFIL';
      this.salvarDadosPerfil(dadosPerfil);
    }
  }

  private salvarDadosPerfil(dados: PerfilCriadorRequisicao): void {
    this.perfilService.atualizarMeuPerfil(dados).subscribe({
      next: (perfilAtualizado) => {
        this.statusEnvio = 'IDLE';
        this.perfilAtual = perfilAtualizado;
        this.arquivoSelecionado = null;
        this.previewUrl = null;
        this.mensagemSucesso = 'Perfil atualizado com sucesso!';
        
        // Limpar feedback de sucesso depois de 3 segundos
        setTimeout(() => this.mensagemSucesso = '', 3000);
      },
      error: (err) => {
        this.statusEnvio = 'IDLE';
        this.mensagemErro = err.error?.message || 'Ocorreu um erro ao atualizar os dados do seu perfil.';
        console.error(err);
      }
    });
  }
}
