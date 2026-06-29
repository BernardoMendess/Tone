package com.tone.modelo.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Implementação do validador para {@link MimeTypeValido}.
 * Permite apenas tipos MIME de arquivos compactados.
 */
public class MimeTypeValidoValidator implements ConstraintValidator<MimeTypeValido, String> {

    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/octet-stream"
    );

    @Override
    public boolean isValid(String mimeType, ConstraintValidatorContext context) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        return TIPOS_PERMITIDOS.contains(mimeType.toLowerCase().trim());
    }
}
