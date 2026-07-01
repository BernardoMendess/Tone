package com.tone.servico;

import com.tone.controlador.dto.ProdutoRequest;
import com.tone.controlador.dto.UploadUrlRequest;
import com.tone.controlador.dto.UploadUrlResponse;
import com.tone.controlador.excecao.CriadorNaoEncontradoException;
import com.tone.controlador.excecao.ProdutoNaoEncontradoException;
import com.tone.modelo.Produto;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.StatusProduto;
import com.tone.repositorio.ProdutoRepositorio;
import com.tone.repositorio.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProdutoServico {

    private static final int EXPIRACAO_UPLOAD_SEGUNDOS = 900;  // 15 minutos
    private static final int EXPIRACAO_DOWNLOAD_SEGUNDOS = 300; // 5 minutos
    private static final String PREFIXO_OBJETO = "timbres/";

    private final ProdutoRepositorio produtoRepositorio;
    private final UsuarioRepository usuarioRepository;
    private final S3Service s3Service;

    public ProdutoServico(ProdutoRepositorio produtoRepositorio,
                          UsuarioRepository usuarioRepository,
                          S3Service s3Service) {
        this.produtoRepositorio = produtoRepositorio;
        this.usuarioRepository = usuarioRepository;
        this.s3Service = s3Service;
    }

    /**
     * Gera uma Presigned URL de upload para o S3 e retorna a chave do objeto.
     * Embute o e-mail do criador e um UUID no caminho para evitar colisões.
     *
     * @param request      Dados do arquivo (nome, tamanho, mimeType)
     * @param emailCriador E-mail do criador autenticado (obtido do JWT)
     * @return DTO com a URL de upload, a chave do objeto e o tempo de expiração
     */
    public UploadUrlResponse gerarUrlUpload(UploadUrlRequest request, String emailCriador) {
        String uuidArquivo = UUID.randomUUID().toString();
        String objectKey = PREFIXO_OBJETO + emailCriador + "/" + uuidArquivo + "_" + request.getNomeArquivo();

        String presignedUrl = s3Service.gerarUrlUpload(objectKey, request.getMimeType(), EXPIRACAO_UPLOAD_SEGUNDOS);

        return new UploadUrlResponse(presignedUrl, objectKey, EXPIRACAO_UPLOAD_SEGUNDOS);
    }

    /**
     * Persiste os metadados do produto no banco de dados vinculando ao criador autenticado.
     *
     * @param request      Dados do produto (nome, descrição, preço, tags, arquivoUri, videoUrl)
     * @param emailCriador E-mail do criador autenticado (obtido do JWT)
     * @return Entidade Produto persistida
     */
    @Transactional
    public Produto criarProduto(ProdutoRequest request, String emailCriador) {
        Usuario criador = usuarioRepository.encontrarPorEmail(emailCriador)
                .orElseThrow(() -> new CriadorNaoEncontradoException(emailCriador));

        Produto produto = new Produto();
        produto.setCriador(criador);
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        produto.setArquivoUri(request.getArquivoUri());
        produto.setVideoUrl(request.getVideoUrl());

        if (request.getTags() != null) {
            produto.setTags(request.getTags());
        }

        // Status PUBLICADO após envio dos metadados válidos pelo criador
        produto.setStatus(StatusProduto.PUBLICADO);

        return produtoRepositorio.save(produto);
    }

    /**
     * Busca um produto pelo ID e gera uma Presigned URL de download temporária.
     * A verificação de compra deve ser feita antes de chamar este método.
     *
     * @param produtoId ID do produto a ser baixado
     * @return Presigned URL com validade de 5 minutos
     */
    public String gerarUrlDownload(UUID produtoId) {
        Produto produto = produtoRepositorio.findById(produtoId)
                .orElseThrow(() -> new ProdutoNaoEncontradoException(produtoId.toString()));

        return s3Service.gerarUrlDownload(produto.getArquivoUri(), EXPIRACAO_DOWNLOAD_SEGUNDOS);
    }

    /**
     * Busca um produto pelo ID ou lança {@link ProdutoNaoEncontradoException}.
     *
     * @param id ID do produto
     * @return Entidade Produto encontrada
     */
    public Produto obterProduto(UUID id) {
        return produtoRepositorio.findById(id)
                .orElseThrow(() -> new ProdutoNaoEncontradoException(id.toString()));
    }
}
