package com.tone.controlador.excecao;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ContaInativaException extends RuntimeException {

    public ContaInativaException() {
        super("Sua conta está inativa. Entre em contato com o suporte.");
    }
}
