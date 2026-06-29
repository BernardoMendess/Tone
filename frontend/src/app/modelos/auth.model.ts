import { Usuario } from './usuario.model';

export interface CadastroRequisicao {
  nomeCompleto: string;
  email: string;
  cpf: string;
  senha: string;
}

export interface LoginRequisicao {
  email: string;
  senha: string;
}

export interface LoginResposta {
  usuario: Usuario;
}
