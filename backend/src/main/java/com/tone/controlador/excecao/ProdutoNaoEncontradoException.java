package com.tone.controlador.excecao;

public class ProdutoNaoEncontradoException extends RuntimeException {

    public ProdutoNaoEncontradoException(String id) {
        super("Produto não encontrado com o ID: " + id);
    }
}
