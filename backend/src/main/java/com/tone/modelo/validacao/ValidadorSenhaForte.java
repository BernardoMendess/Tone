package com.tone.modelo.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida se a senha atende aos critérios de segurança:
 * - Mínimo 8 caracteres
 * - Pelo menos 1 letra maiúscula
 * - Pelo menos 1 letra minúscula
 * - Pelo menos 1 número
 * - Pelo menos 1 caractere especial
 */
public class ValidadorSenhaForte implements ConstraintValidator<SenhaForte, String> {

    private static final String REGEX_MAIUSCULA = ".*[A-Z].*";
    private static final String REGEX_MINUSCULA = ".*[a-z].*";
    private static final String REGEX_NUMERO = ".*[0-9].*";
    private static final String REGEX_ESPECIAL = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*";

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext context) {
        if (senha == null || senha.isBlank()) {
            return false;
        }

        return senha.length() >= 8
                && senha.matches(REGEX_MAIUSCULA)
                && senha.matches(REGEX_MINUSCULA)
                && senha.matches(REGEX_NUMERO)
                && senha.matches(REGEX_ESPECIAL);
    }
}
