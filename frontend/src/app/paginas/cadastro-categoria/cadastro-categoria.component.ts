import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule } from '@angular/forms';
import { CategoriaService } from '../../servicos/categoria.service';

@Component({
  selector: 'app-cadastro-categoria',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cadastro-categoria.component.html',
  styleUrls: ['./cadastro-categoria.component.css']
})
export class CadastroCategoriaComponent implements OnInit {
  categoriaForm!: FormGroup;
  carregando = false;
  mensagemSucesso = '';
  mensagemErro = '';

  constructor(
    private fb: FormBuilder,
    private categoriaService: CategoriaService
  ) {}

  ngOnInit(): void {
    this.categoriaForm = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(100)]],
      subcategorias: this.fb.array([])
    });
  }

  get subcategorias(): FormArray {
    return this.categoriaForm.get('subcategorias') as FormArray;
  }

  adicionarSubcategoria(): void {
    this.subcategorias.push(this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(100)]]
    }));
  }

  removerSubcategoria(index: number): void {
    this.subcategorias.removeAt(index);
  }

  salvar(): void {
    if (this.categoriaForm.invalid) {
      this.mensagemErro = 'Preencha todos os campos obrigatórios.';
      return;
    }

    this.carregando = true;
    this.mensagemSucesso = '';
    this.mensagemErro = '';

    this.categoriaService.criarCategoria(this.categoriaForm.value).subscribe({
      next: () => {
        this.mensagemSucesso = 'Categoria cadastrada com sucesso!';
        this.carregando = false;
        this.categoriaForm.reset();
        this.subcategorias.clear();
      },
      error: (err) => {
        this.carregando = false;
        this.mensagemErro = err.error?.mensagem || 'Erro ao cadastrar categoria.';
      }
    });
  }
}
