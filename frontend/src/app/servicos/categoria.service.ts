import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SubcategoriaRequest {
  nome: string;
}

export interface CategoriaRequest {
  nome: string;
  subcategorias: SubcategoriaRequest[];
}

export interface SubcategoriaResponse {
  id: number;
  nome: string;
}

export interface CategoriaResponse {
  id: number;
  nome: string;
  subcategorias: SubcategoriaResponse[];
}

@Injectable({
  providedIn: 'root'
})
export class CategoriaService {
  private readonly apiUrl = 'http://localhost:8080/api/categorias';

  constructor(private http: HttpClient) {}

  criarCategoria(request: CategoriaRequest): Observable<CategoriaResponse> {
    return this.http.post<CategoriaResponse>(this.apiUrl, request, {
      withCredentials: true
    });
  }
}
