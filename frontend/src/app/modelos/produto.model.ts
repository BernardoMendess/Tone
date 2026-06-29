export interface ProdutoRequest {
  nome: string;
  descricao: string;
  preco: number;
  tags: string[];
  arquivoUri: string;
  videoUrl?: string;
  categoriaId: number;
  subcategoriaId?: number;
}

export interface UploadUrlRequest {
  nomeArquivo: string;
  tamanhoBytes: number;
  mimeType: string;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  arquivoUri: string;
  expiraEmSegundos: number;
}

export interface ProdutoResponse {
  id: string;
  status: string;
  mensagem: string;
}
