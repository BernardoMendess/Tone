package com.tone.controlador.excecao;

public class CategoriaJaCadastradaException extends RuntimeException {
    public CategoriaJaCadastradaException(String mensagem) {
        super(mensagem);
    }
}
