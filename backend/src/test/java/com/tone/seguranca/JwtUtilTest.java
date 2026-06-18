package com.tone.seguranca;

import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil — Testes Unitários")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "segredo", "CHAVE_SECRETA_TEMPORARIA_DESENVOLVIMENTO_TONE_2024");
        ReflectionTestUtils.setField(jwtUtil, "expiracao", 86400000L);
    }

    private Usuario criarUsuario() {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 1L);
        usuario.setEmail("joao@example.com");
        usuario.setTipoPerfil(TipoPerfil.COMPRADOR);
        return usuario;
    }

    @Test
    @DisplayName("Token gerado deve ser válido")
    void tokenGeradoDeveSerValido() {
        Usuario usuario = criarUsuario();
        String token = jwtUtil.gerarToken(usuario);
        assertTrue(jwtUtil.validarToken(token));
    }

    @Test
    @DisplayName("Token inválido deve retornar false")
    void tokenInvalidoDeveRetornarFalse() {
        assertFalse(jwtUtil.validarToken("token.invalido.aqui"));
    }

    @Test
    @DisplayName("Deve extrair e-mail corretamente do token")
    void deveExtrairEmailDoToken() {
        Usuario usuario = criarUsuario();
        String token = jwtUtil.gerarToken(usuario);
        assertEquals("joao@example.com", jwtUtil.extrairEmail(token));
    }

    @Test
    @DisplayName("Deve extrair tipo de perfil corretamente do token")
    void deveExtrairTipoPerfilDoToken() {
        Usuario usuario = criarUsuario();
        String token = jwtUtil.gerarToken(usuario);
        assertEquals("COMPRADOR", jwtUtil.extrairTipoPerfil(token));
    }

    @Test
    @DisplayName("Token expirado deve retornar false")
    void tokenExpiradoDeveRetornarFalse() {
        JwtUtil jwtExpirado = new JwtUtil();
        ReflectionTestUtils.setField(jwtExpirado, "segredo", "CHAVE_SECRETA_TEMPORARIA_DESENVOLVIMENTO_TONE_2024");
        ReflectionTestUtils.setField(jwtExpirado, "expiracao", -1000L);

        String token = jwtExpirado.gerarToken(criarUsuario());
        assertFalse(jwtExpirado.validarToken(token));
    }
}
