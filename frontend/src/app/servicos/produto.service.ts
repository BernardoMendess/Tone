import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpRequest, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProdutoRequest, ProdutoResponse, UploadUrlRequest, UploadUrlResponse } from '../modelos/produto.model';

@Injectable({
  providedIn: 'root'
})
export class ProdutoService {
  private readonly apiUrl = 'http://localhost:8080/api/v1/produtos';

  constructor(private http: HttpClient) {}

  solicitarUrlUpload(dados: UploadUrlRequest): Observable<UploadUrlResponse> {
    return this.http.post<UploadUrlResponse>(`${this.apiUrl}/upload-url`, dados, {
      withCredentials: true
    });
  }

  fazerUploadArquivo(url: string, arquivo: File): Observable<HttpEvent<any>> {
    const req = new HttpRequest('PUT', url, arquivo, {
      reportProgress: true,
      headers: new HttpHeaders({
        'Content-Type': arquivo.type
      })
    });

    return this.http.request(req);
  }

  cadastrarProduto(dados: ProdutoRequest): Observable<ProdutoResponse> {
    return this.http.post<ProdutoResponse>(this.apiUrl, dados, {
      withCredentials: true
    });
  }

  listarProdutosPorCriador(criadorId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}?criadorId=${criadorId}`);
  }
}
