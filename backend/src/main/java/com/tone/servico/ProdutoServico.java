package com.tone.servico;

import com.tone.controlador.dto.ProdutoRequest;
import com.tone.modelo.Produto;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.StatusProduto;
import com.tone.repositorio.ProdutoRepositorio;
import com.tone.repositorio.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoServico {

    private final ProdutoRepositorio produtoRepositorio;
    private final UsuarioRepository usuarioRepository;

    public ProdutoServico(ProdutoRepositorio produtoRepositorio, UsuarioRepository usuarioRepository) {
        this.produtoRepositorio = produtoRepositorio;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Produto criarProduto(ProdutoRequest request, String emailCriador) {
        Usuario criador = usuarioRepository.findByEmail(emailCriador)
                .orElseThrow(() -> new IllegalArgumentException("Criador não encontrado"));

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

        produto.setStatus(StatusProduto.PUBLICADO); // Conforme requisito, assume PUBLICADO após envio dos metadados válidos.

        return produtoRepositorio.save(produto);
    }

    public Produto obterProduto(java.util.UUID id) {
        return produtoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));
    }
}
