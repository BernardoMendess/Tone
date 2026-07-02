import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PerfilCriadorRequisicao, PerfilCriadorResposta, S3UploadPresignResponse } from '../modelos/perfil.model';

@Injectable({
  providedIn: 'root'
})
export class PerfilService {
  private readonly apiUrl = 'http://localhost:8080/api/v1/perfis';

  constructor(private http: HttpClient) {}

  obterMeuPerfil(): Observable<PerfilCriadorResposta> {
    return this.http.get<PerfilCriadorResposta>(`${this.apiUrl}/me`, {
      withCredentials: true
    });
  }

  atualizarMeuPerfil(dados: PerfilCriadorRequisicao): Observable<PerfilCriadorResposta> {
    return this.http.put<PerfilCriadorResposta>(`${this.apiUrl}/me`, dados, {
      withCredentials: true
    });
  }

  obterPerfilPublico(usuarioId: number): Observable<PerfilCriadorResposta> {
    return this.http.get<PerfilCriadorResposta>(`${this.apiUrl}/${usuarioId}`);
  }

  solicitarUrlUpload(mimeType: string): Observable<S3UploadPresignResponse> {
    return this.http.post<S3UploadPresignResponse>(`${this.apiUrl}/upload-imagem`, { mimeType }, {
      withCredentials: true
    });
  }

  fazerUploadFoto(url: string, arquivo: File): Observable<any> {
    return this.http.put(url, arquivo, {
      headers: new HttpHeaders({
        'Content-Type': arquivo.type
      })
    });
  }
}
