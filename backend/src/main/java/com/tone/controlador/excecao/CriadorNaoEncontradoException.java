package com.tone.controlador.excecao;

public class CriadorNaoEncontradoException extends RuntimeException {

    public CriadorNaoEncontradoException(String email) {
        super("Criador não encontrado com o e-mail: " + email);
    }
}
