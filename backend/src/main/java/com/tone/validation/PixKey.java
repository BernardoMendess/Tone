package com.tone.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PixKeyValidator.class)
public @interface PixKey {
    String message() default "Chave PIX inválida (informe CPF, CNPJ, e-mail, telefone +55 ou chave aleatória UUID).";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
