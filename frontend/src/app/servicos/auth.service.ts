import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { CadastroRequisicao, LoginRequisicao, LoginResposta } from '../modelos/auth.model';
import { Usuario } from '../modelos/usuario.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly apiUrl = 'http://localhost:8080/api/auth';
  private readonly TOKEN_KEY = 'tone_token';

  constructor(private http: HttpClient) {}

  cadastrar(dados: CadastroRequisicao): Observable<{ mensagem: string }> {
    return this.http.post<{ mensagem: string }>(`${this.apiUrl}/cadastrar`, dados);
  }

  login(dados: LoginRequisicao): Observable<LoginResposta> {
    return this.http.post<LoginResposta>(`${this.apiUrl}/login`, dados).pipe(
      tap((resposta) => this.salvarToken(resposta.token))
    );
  }

  verificarEmail(token: string): Observable<{ mensagem: string }> {
    return this.http.get<{ mensagem: string }>(`${this.apiUrl}/verificar-email`, {
      params: { token },
    });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }

  estaAutenticado(): boolean {
    const token = this.obterToken();
    if (!token) return false;

    try {
      const payload = this.decodificarToken(token);
      const agora = Math.floor(Date.now() / 1000);
      return payload.exp > agora;
    } catch {
      return false;
    }
  }

  obterToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  obterUsuarioLogado(): Partial<Usuario> | null {
    const token = this.obterToken();
    if (!token) return null;

    try {
      const payload = this.decodificarToken(token);
      return {
        email: payload.sub,
        id: payload.id,
        tipoPerfil: payload.tipoPerfil,
      };
    } catch {
      return null;
    }
  }

  private salvarToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  private decodificarToken(token: string): any {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  }
}
