package com.tone.exception;

import java.util.UUID;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String aggregate, UUID id, Enum<?> from, Enum<?> to) {
        super(String.format("Transição inválida em %s(%s): %s -> %s", aggregate, id, from, to));
    }
}
