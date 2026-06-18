package com.tone.controlador.excecao;

public class TokenVerificacaoInvalidoException extends RuntimeException {
    public TokenVerificacaoInvalidoException() {
        super("Token de verificação inválido ou expirado");
    }
}
