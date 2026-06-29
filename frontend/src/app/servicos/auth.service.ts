import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CadastroRequisicao, LoginRequisicao, LoginResposta } from '../modelos/auth.model';
import { Usuario } from '../modelos/usuario.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly apiUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {}

  cadastrar(dados: CadastroRequisicao): Observable<{ mensagem: string }> {
    return this.http.post<{ mensagem: string }>(`${this.apiUrl}/cadastrar`, dados, {
      withCredentials: true,
    });
  }

  /**
   * Realiza o login. O backend seta o JWT como HttpOnly Cookie automaticamente.
   * O token nunca é acessível via JavaScript — isso previne ataques XSS.
   */
  login(dados: LoginRequisicao): Observable<LoginResposta> {
    return this.http.post<LoginResposta>(`${this.apiUrl}/login`, dados, {
      withCredentials: true,
    });
  }

  /**
   * Remove o cookie de autenticação no servidor (maxAge=0).
   */
  logout(): Observable<{ mensagem: string }> {
    return this.http.post<{ mensagem: string }>(`${this.apiUrl}/logout`, {}, {
      withCredentials: true,
    });
  }

  /**
   * Verifica se o usuário está autenticado consultando o endpoint /me no servidor.
   * Como o token está em HttpOnly Cookie, não é possível verificar no frontend sem consultar o backend.
   */
  obterUsuarioLogado(): Observable<Partial<Usuario>> {
    return this.http.get<Partial<Usuario>>(`${this.apiUrl}/me`, {
      withCredentials: true,
    });
  }

  verificarEmail(token: string): Observable<{ mensagem: string }> {
    return this.http.get<{ mensagem: string }>(`${this.apiUrl}/verificar-email`, {
      params: { token },
      withCredentials: true,
    });
  }
}
