import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { LoginResposta } from '../modelos/auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const API = 'http://localhost:8080/api/auth';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('deve chamar POST /cadastrar com os dados corretos', () => {
    const dados = { nomeCompleto: 'João', email: 'j@j.com', cpf: '000.000.000-00', senha: 'Senha@1' };
    service.cadastrar(dados).subscribe();
    const req = httpMock.expectOne(`${API}/cadastrar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dados);
    req.flush({ mensagem: 'ok' });
  });

  it('deve chamar POST /login e salvar token no localStorage', () => {
    const dados = { email: 'j@j.com', senha: 'Senha@1' };
    const resposta: LoginResposta = {
      token: 'meu.token.jwt',
      usuario: { id: 1, nomeCompleto: 'João', email: 'j@j.com', cpf: '000', tipoPerfil: 'COMPRADOR', dataCriacao: '' },
    };

    service.login(dados).subscribe();
    const req = httpMock.expectOne(`${API}/login`);
    expect(req.request.method).toBe('POST');
    req.flush(resposta);

    expect(localStorage.getItem('tone_token')).toBe('meu.token.jwt');
  });

  it('deve chamar GET /verificar-email com o token correto', () => {
    service.verificarEmail('uuid-teste').subscribe();
    const req = httpMock.expectOne(`${API}/verificar-email?token=uuid-teste`);
    expect(req.request.method).toBe('GET');
    req.flush({ mensagem: 'ok' });
  });

  it('estaAutenticado deve retornar false quando não há token', () => {
    expect(service.estaAutenticado()).toBeFalse();
  });

  it('logout deve remover o token do localStorage', () => {
    localStorage.setItem('tone_token', 'token_qualquer');
    service.logout();
    expect(localStorage.getItem('tone_token')).toBeNull();
  });
});
