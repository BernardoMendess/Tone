package com.tone.servico;

import com.tone.controlador.dto.ProdutoRequest;
import com.tone.controlador.dto.UploadUrlRequest;
import com.tone.controlador.dto.UploadUrlResponse;
import com.tone.controlador.excecao.CriadorNaoEncontradoException;
import com.tone.controlador.excecao.ProdutoNaoEncontradoException;
import com.tone.modelo.Categoria;
import com.tone.modelo.Produto;
import com.tone.modelo.Subcategoria;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.StatusProduto;
import com.tone.repositorio.CategoriaRepositorio;
import com.tone.repositorio.ProdutoRepositorio;
import com.tone.repositorio.SubcategoriaRepositorio;
import com.tone.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ProdutoServico {

    private static final int EXPIRACAO_UPLOAD_SEGUNDOS = 900;  // 15 minutos
    private static final int EXPIRACAO_DOWNLOAD_SEGUNDOS = 300; // 5 minutos
    private static final String PREFIXO_OBJETO = "timbres/";

    private final ProdutoRepositorio produtoRepositorio;
    private final UsuarioRepository usuarioRepository;
    private final S3Service s3Service;
    private final CategoriaRepositorio categoriaRepositorio;
    private final SubcategoriaRepositorio subcategoriaRepositorio;

    public ProdutoServico(ProdutoRepositorio produtoRepositorio,
                          UsuarioRepository usuarioRepository,
                          S3Service s3Service,
                          CategoriaRepositorio categoriaRepositorio,
                          SubcategoriaRepositorio subcategoriaRepositorio) {
        this.produtoRepositorio = produtoRepositorio;
        this.usuarioRepository = usuarioRepository;
        this.s3Service = s3Service;
        this.categoriaRepositorio = categoriaRepositorio;
        this.subcategoriaRepositorio = subcategoriaRepositorio;
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

        // Associar categoria (obrigatória)
        Categoria categoria = categoriaRepositorio.findById(request.getCategoriaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Categoria não encontrada com id: " + request.getCategoriaId()));
        produto.setCategoria(categoria);

        // Associar subcategoria (obrigatória se a categoria possuir subcategorias)
        if (request.getSubcategoriaId() != null) {
            Subcategoria subcategoria = subcategoriaRepositorio.findById(request.getSubcategoriaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Subcategoria não encontrada com id: " + request.getSubcategoriaId()));
            if (!subcategoria.getCategoria().getId().equals(categoria.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A subcategoria não pertence à categoria informada.");
            }
            produto.setSubcategoria(subcategoria);
        } else if (!categoria.getSubcategorias().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A subcategoria é obrigatória para a categoria informada.");
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

    /**
     * Busca todos os produtos de um criador com o status informado e os retorna formatados.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.tone.controlador.dto.ProdutoDetalheResponseDTO> buscarPorCriadorEStatus(Long criadorId, StatusProduto status) {
        return produtoRepositorio.findByCriadorIdAndStatus(criadorId, status).stream()
                .map(produto -> com.tone.controlador.dto.ProdutoDetalheResponseDTO.builder()
                        .id(produto.getId())
                        .nome(produto.getNome())
                        .descricao(produto.getDescricao())
                        .preco(produto.getPreco())
                        .videoUrl(produto.getVideoUrl())
                        .categoriaNome(produto.getCategoria() != null ? produto.getCategoria().getNome() : null)
                        .subcategoriaNome(produto.getSubcategoria() != null ? produto.getSubcategoria().getNome() : null)
                        .tags(produto.getTags())
                        .criadorId(produto.getCriador().getId())
                        .criadorNome(produto.getCriador().getNomeCompleto())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }
}
