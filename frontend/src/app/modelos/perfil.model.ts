export interface PerfilCriadorRequisicao {
  biografia?: string;
  fotoPerfilUrl?: string;
  instagramUrl?: string;
  youtubeUrl?: string;
  soundcloudUrl?: string;
  sitePessoalUrl?: string;
}

export interface PerfilCriadorResposta {
  id: number;
  usuarioId: number;
  nomeCompleto: string;
  biografia?: string;
  fotoPerfilUrl?: string;
  instagramUrl?: string;
  youtubeUrl?: string;
  soundcloudUrl?: string;
  sitePessoalUrl?: string;
}

export interface S3UploadPresignResponse {
  uploadUrl: string;
  fotoPerfilUrl: string;
}
