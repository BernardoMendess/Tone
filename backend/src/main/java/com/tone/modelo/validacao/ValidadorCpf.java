package com.tone.modelo.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida CPFs no formato 000.000.000-00 ou 00000000000.
 * Implementa o algoritmo oficial de dígitos verificadores do CPF brasileiro.
 */
public class ValidadorCpf implements ConstraintValidator<Cpf, String> {

    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        if (cpf == null || cpf.isBlank()) {
            return false;
        }

        // Remove máscara
        String cpfNumerico = cpf.replaceAll("[^0-9]", "");

        if (cpfNumerico.length() != 11) {
            return false;
        }

        // Rejeita sequências repetidas (ex: 111.111.111-11)
        if (cpfNumerico.chars().distinct().count() == 1) {
            return false;
        }

        return validarDigitos(cpfNumerico);
    }

    private boolean validarDigitos(String cpf) {
        int primeiroDigito = calcularDigito(cpf, 9);
        int segundoDigito = calcularDigito(cpf, 10);

        return cpf.charAt(9) - '0' == primeiroDigito
                && cpf.charAt(10) - '0' == segundoDigito;
    }

    private int calcularDigito(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
