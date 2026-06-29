package com.tone.seguranca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Argon2Codificador — Testes Unitários")
class Argon2CodificadorTest {

    private Argon2Codificador codificador;

    @BeforeEach
    void setUp() {
        codificador = new Argon2Codificador();
    }

    @Test
    @DisplayName("Encode deve gerar hash diferente do valor original")
    void encodeDeveriaGerarHashDiferente() {
        String senha = "Senha@123";
        String hash = codificador.encode(senha);
        assertNotEquals(senha, hash);
    }

    @Test
    @DisplayName("Matches deve retornar true para senha correta")
    void matchesSenhaCorreta() {
        String senha = "Senha@123";
        String hash = codificador.encode(senha);
        assertTrue(codificador.matches(senha, hash));
    }

    @Test
    @DisplayName("Matches deve retornar false para senha errada")
    void matchesSenhaErrada() {
        String hash = codificador.encode("Senha@123");
        assertFalse(codificador.matches("SenhaErrada@999", hash));
    }

    @Test
    @DisplayName("Hashes do mesmo valor devem ser diferentes (salt aleatório)")
    void hashesDoMesmoValorDevemSerDiferentes() {
        String senha = "Senha@123";
        String hash1 = codificador.encode(senha);
        String hash2 = codificador.encode(senha);
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Matches com senha nula deve retornar false")
    void matchesSenhaNula() {
        String hash = codificador.encode("Senha@123");
        assertFalse(codificador.matches(null, hash));
    }
}
