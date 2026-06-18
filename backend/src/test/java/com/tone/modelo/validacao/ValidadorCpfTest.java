package com.tone.modelo.validacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ValidadorCpf — Testes Unitários")
class ValidadorCpfTest {

    private ValidadorCpf validador;

    @BeforeEach
    void setUp() {
        validador = new ValidadorCpf();
    }

    @Test
    @DisplayName("CPF válido sem máscara deve passar")
    void cpfValidoSemMascara() {
        assertTrue(validador.isValid("52998224725", null));
    }

    @Test
    @DisplayName("CPF válido com máscara deve passar")
    void cpfValidoComMascara() {
        assertTrue(validador.isValid("529.982.247-25", null));
    }

    @Test
    @DisplayName("CPF inválido deve falhar")
    void cpfInvalido() {
        assertFalse(validador.isValid("12345678901", null));
    }

    @Test
    @DisplayName("CPF com todos dígitos iguais deve falhar")
    void cpfComDigitosIguais() {
        assertFalse(validador.isValid("111.111.111-11", null));
    }

    @Test
    @DisplayName("CPF nulo deve falhar")
    void cpfNulo() {
        assertFalse(validador.isValid(null, null));
    }

    @Test
    @DisplayName("CPF em branco deve falhar")
    void cpfEmBranco() {
        assertFalse(validador.isValid("   ", null));
    }

    @Test
    @DisplayName("CPF com tamanho errado deve falhar")
    void cpfTamanhoErrado() {
        assertFalse(validador.isValid("123456789", null));
    }
}
