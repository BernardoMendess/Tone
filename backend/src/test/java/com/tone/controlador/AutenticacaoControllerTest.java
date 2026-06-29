package com.tone.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.controlador.excecao.*;
import com.tone.controlador.dto.CadastroRequisicao;
import com.tone.controlador.dto.LoginRequisicao;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import com.tone.seguranca.ConfiguracaoSeguranca;
import com.tone.seguranca.JwtUtil;
import com.tone.servico.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AutenticacaoController.class)
@Import(ConfiguracaoSeguranca.class)
@DisplayName("AutenticacaoController — Testes de Integração")
    class AutenticacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private com.tone.seguranca.JwtFiltroAutenticacao jwtFiltro;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private com.tone.seguranca.Argon2Codificador argon2Codificador;

    @MockBean
    private com.tone.repositorio.UsuarioRepository usuarioRepository;

    // --- Cadastro ---

    @Test
    @DisplayName("POST /cadastrar com dados válidos deve retornar 201")
    void cadastrarValido() throws Exception {
        CadastroRequisicao requisicao = new CadastroRequisicao(
                "João Silva", "joao@example.com", "529.982.247-25", "Senha@123"
        );

        Usuario usuarioMock = new Usuario();
        usuarioMock.setNomeCompleto("João Silva");
        when(usuarioService.cadastrar(Usuario.class.newInstance())).thenReturn(usuarioMock);

        mockMvc.perform(post("/api/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    @DisplayName("POST /cadastrar com e-mail inválido deve retornar 400")
    void cadastrarEmailInvalido() throws Exception {
        CadastroRequisicao requisicao = new CadastroRequisicao(
                "João Silva", "emailinvalido", "529.982.247-25", "Senha@123"
        );

        mockMvc.perform(post("/api/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /cadastrar com e-mail duplicado deve retornar 409")
    void cadastrarEmailDuplicado() throws Exception {
        CadastroRequisicao requisicao = new CadastroRequisicao(
                "João Silva", "joao@example.com", "529.982.247-25", "Senha@123"
        );
        when(usuarioService.cadastrar(any())).thenThrow(new EmailJaCadastradoException("joao@example.com"));

        mockMvc.perform(post("/api/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isConflict());
    }

    // --- Login ---

    @Test
    @DisplayName("POST /login com credenciais válidas deve retornar 200 e setar HttpOnly Cookie")
    void loginValido() throws Exception {
        LoginRequisicao requisicao = new LoginRequisicao("joao@example.com", "Senha@123");

        Usuario usuarioMock = new Usuario();
        usuarioMock.setNomeCompleto("João Silva");
        usuarioMock.setEmail("joao@example.com");
        usuarioMock.setCpf("529.982.247-25");
        usuarioMock.setTipoPerfil(TipoPerfil.COMPRADOR);

        when(usuarioService.autenticar("joao@example.com", "Senha@123")).thenReturn("token_jwt");
        when(usuarioService.buscarPorEmail("joao@example.com")).thenReturn(usuarioMock);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isOk())
                // Token NÃO deve aparecer no body
                .andExpect(jsonPath("$.token").doesNotExist())
                // Cookie HttpOnly deve estar presente
                .andExpect(header().string("Set-Cookie", containsString("tone_token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    @DisplayName("POST /login com credenciais inválidas deve retornar 401")
    void loginCredenciaisInvalidas() throws Exception {
        LoginRequisicao requisicao = new LoginRequisicao("joao@example.com", "SenhaErrada@1");
        when(usuarioService.autenticar(anyString(), anyString()))
                .thenThrow(new CredenciaisInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login com e-mail não verificado deve retornar 403")
    void loginEmailNaoVerificado() throws Exception {
        LoginRequisicao requisicao = new LoginRequisicao("joao@example.com", "Senha@123");
        when(usuarioService.autenticar(anyString(), anyString()))
                .thenThrow(new EmailNaoVerificadoException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /login com conta bloqueada deve retornar 429")
    void loginContaBloqueada() throws Exception {
        LoginRequisicao requisicao = new LoginRequisicao("joao@example.com", "Senha@123");
        when(usuarioService.autenticar(anyString(), anyString()))
                .thenThrow(new ContaBloqueadaException(10));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isTooManyRequests());
    }

    // --- Logout ---

    @Test
    @DisplayName("POST /logout deve retornar 200 e limpar o cookie")
    void logout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").exists())
                .andExpect(header().string("Set-Cookie", containsString("tone_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    // --- Verificar E-mail ---

    @Test
    @DisplayName("GET /verificar-email com token válido deve retornar 200")
    void verificarEmailValido() throws Exception {
        doNothing().when(usuarioService).verificarEmail("uuid-valido");

        mockMvc.perform(get("/api/auth/verificar-email").param("token", "uuid-valido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    @DisplayName("GET /verificar-email com token inválido deve retornar 400")
    void verificarEmailTokenInvalido() throws Exception {
        doThrow(new TokenVerificacaoInvalidoException())
                .when(usuarioService).verificarEmail("token-invalido");

        mockMvc.perform(get("/api/auth/verificar-email").param("token", "token-invalido"))
                .andExpect(status().isBadRequest());
    }
}
