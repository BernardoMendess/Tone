import { Component } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { ProdutoService } from '../../servicos/produto.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-cadastro-produto',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cadastro-produto.component.html',
  styleUrls: ['./cadastro-produto.component.css']
})
export class CadastroProdutoComponent {
  produtoForm: FormGroup;
  arquivoSelecionado: File | null = null;
  progressoUpload = 0;
  statusEnvio: 'IDLE' | 'SOLICITANDO_URL' | 'FAZENDO_UPLOAD' | 'SALVANDO_PRODUTO' | 'SUCESSO' | 'ERRO' = 'IDLE';
  mensagemErro = '';
  tagsDisponiveis = ['ROCK', 'METAL', 'NEO_SOUL', 'BLUES', 'POP', 'WORSHIP', 'JAZZ', 'AMBIENT'];
  tagsSelecionadas: string[] = [];

  constructor(
    private fb: FormBuilder,
    private produtoService: ProdutoService,
    private router: Router
  ) {
    this.produtoForm = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(150)]],
      descricao: ['', [Validators.required]],
      preco: [0, [Validators.required, Validators.min(0)]],
      videoUrl: ['']
    });
  }

  toggleTag(tag: string) {
    const index = this.tagsSelecionadas.indexOf(tag);
    if (index === -1) {
      this.tagsSelecionadas.push(tag);
    } else {
      this.tagsSelecionadas.splice(index, 1);
    }
  }

  onFileSelected(event: any) {
    const arquivo = event.target.files[0] as File;
    if (arquivo) {
      if (arquivo.size > 1048576) {
        this.mensagemErro = 'O arquivo excede o limite de 1MB.';
        this.arquivoSelecionado = null;
        return;
      }
      this.mensagemErro = '';
      this.arquivoSelecionado = arquivo;
    }
  }

  onSubmit() {
    if (this.produtoForm.invalid || !this.arquivoSelecionado || this.tagsSelecionadas.length === 0) {
      this.mensagemErro = 'Preencha todos os campos obrigatórios, selecione pelo menos uma tag e um arquivo.';
      return;
    }

    this.statusEnvio = 'SOLICITANDO_URL';
    
    // Passo 1: Solicitar URL
    this.produtoService.solicitarUrlUpload({
      nomeArquivo: this.arquivoSelecionado.name,
      tamanhoBytes: this.arquivoSelecionado.size,
      mimeType: this.arquivoSelecionado.type
    }).subscribe({
      next: (resUrl) => {
        this.statusEnvio = 'FAZENDO_UPLOAD';
        
        // Passo 2: Fazer o upload para S3
        this.produtoService.fazerUploadArquivo(resUrl.uploadUrl, this.arquivoSelecionado!).subscribe({
          next: (event) => {
            if (event.type === HttpEventType.UploadProgress) {
              if (event.total) {
                this.progressoUpload = Math.round(100 * (event.loaded / event.total));
              }
            } else if (event.type === HttpEventType.Response) {
              
              // Passo 3: Salvar metadados do produto
              this.statusEnvio = 'SALVANDO_PRODUTO';
              const dadosProduto = {
                ...this.produtoForm.value,
                tags: this.tagsSelecionadas,
                arquivoUri: resUrl.arquivoUri
              };

              this.produtoService.cadastrarProduto(dadosProduto).subscribe({
                next: () => {
                  this.statusEnvio = 'SUCESSO';
                  setTimeout(() => this.router.navigate(['/']), 2000);
                },
                error: (err) => {
                  this.statusEnvio = 'ERRO';
                  this.mensagemErro = 'Erro ao salvar produto.';
                  console.error(err);
                }
              });
            }
          },
          error: (err) => {
            this.statusEnvio = 'ERRO';
            this.mensagemErro = 'Erro ao fazer upload do arquivo.';
            console.error(err);
          }
        });
      },
      error: (err) => {
        this.statusEnvio = 'ERRO';
        this.mensagemErro = err.error?.message || 'Erro ao solicitar URL de upload.';
      }
    });
  }
}
