export type TipoPerfil = 'COMPRADOR' | 'CRIADOR' | 'ADMIN';

export interface Usuario {
  id: number;
  nomeCompleto: string;
  email: string;
  cpf: string;
  tipoPerfil: TipoPerfil;
  dataCriacao: string;
}
