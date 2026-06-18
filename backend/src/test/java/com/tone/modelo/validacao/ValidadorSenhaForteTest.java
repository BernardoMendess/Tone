package com.tone.modelo.validacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ValidadorSenhaForte — Testes Unitários")
class ValidadorSenhaForteTest {

    private ValidadorSenhaForte validador;

    @BeforeEach
    void setUp() {
        validador = new ValidadorSenhaForte();
    }

    @Test
    @DisplayName("Senha com todos os critérios deve passar")
    void senhaValida() {
        assertTrue(validador.isValid("Senha@123", null));
    }

    @Test
    @DisplayName("Senha sem maiúscula deve falhar")
    void senhaSemMaiuscula() {
        assertFalse(validador.isValid("senha@123", null));
    }

    @Test
    @DisplayName("Senha sem minúscula deve falhar")
    void senhaSemMinuscula() {
        assertFalse(validador.isValid("SENHA@123", null));
    }

    @Test
    @DisplayName("Senha sem número deve falhar")
    void senhaSemNumero() {
        assertFalse(validador.isValid("Senha@abc", null));
    }

    @Test
    @DisplayName("Senha sem caractere especial deve falhar")
    void senhaSemEspecial() {
        assertFalse(validador.isValid("Senha1234", null));
    }

    @Test
    @DisplayName("Senha com menos de 8 caracteres deve falhar")
    void senhaCurta() {
        assertFalse(validador.isValid("S@1a", null));
    }

    @Test
    @DisplayName("Senha nula deve falhar")
    void senhaNula() {
        assertFalse(validador.isValid(null, null));
    }
}
