package com.tone.controlador.excecao;

public class EmailNaoVerificadoException extends RuntimeException {
    public EmailNaoVerificadoException() {
        super("E-mail não verificado. Por favor, verifique sua caixa de entrada.");
    }
}
