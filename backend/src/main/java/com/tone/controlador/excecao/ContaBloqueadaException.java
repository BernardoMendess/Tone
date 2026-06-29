package com.tone.controlador.excecao;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ContaBloqueadaException extends RuntimeException {

    public ContaBloqueadaException(long minutosRestantes) {
        super("Conta temporariamente bloqueada por excesso de tentativas. Tente novamente em " + minutosRestantes + " minuto(s).");
    }
}
