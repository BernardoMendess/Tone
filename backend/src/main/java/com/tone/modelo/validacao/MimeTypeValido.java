package com.tone.modelo.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Valida que o MIME type informado é de um arquivo compactado permitido.
 * Tipos aceitos: application/zip, application/x-zip-compressed,
 * application/x-rar-compressed, application/octet-stream.
 */
@Documented
@Constraint(validatedBy = MimeTypeValidoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MimeTypeValido {

    String message() default "Tipo de arquivo não permitido. Envie apenas arquivos compactados (.zip, .rar)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
