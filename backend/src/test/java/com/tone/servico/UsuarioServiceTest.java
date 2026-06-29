package com.tone.servico;

import com.tone.controlador.excecao.*;
import com.tone.modelo.TokenVerificacaoEmail;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import com.tone.repositorio.TokenVerificacaoEmailRepository;
import com.tone.repositorio.UsuarioRepository;
import com.tone.seguranca.Argon2Codificador;
import com.tone.seguranca.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService — Testes Unitários")
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TokenVerificacaoEmailRepository tokenRepository;
    @Mock private Argon2Codificador codificador;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuarioBase;

    @BeforeEach
    void setUp() {
        usuarioBase = new Usuario();
        usuarioBase.setNomeCompleto("João Silva");
        usuarioBase.setEmail("joao@example.com");
        usuarioBase.setCpf("529.982.247-25");
        usuarioBase.setSenha("Senha@123");
        usuarioBase.setAtivo(true);
        usuarioBase.setEmailVerificado(true);
    }

    // --- Cadastro ---

    @Test
    @DisplayName("Cadastro com dados válidos deve ter sucesso")
    void cadastroValido() {
        when(usuarioRepository.existePorEmail(anyString())).thenReturn(false);
        when(usuarioRepository.existePorCpf(anyString())).thenReturn(false);
        when(codificador.encode(anyString())).thenReturn("hash_da_senha");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioBase);
        when(tokenRepository.save(any(TokenVerificacaoEmail.class))).thenReturn(null);
        doNothing().when(emailService).enviarEmailVerificacao(any(), anyString());

        assertDoesNotThrow(() -> usuarioService.cadastrar(usuarioBase));

        verify(codificador).encode("Senha@123");
        verify(emailService).enviarEmailVerificacao(any(), anyString());
    }

    @Test
    @DisplayName("Cadastro com e-mail duplicado deve lançar EmailJaCadastradoException")
    void cadastroEmailDuplicado() {
        when(usuarioRepository.existePorEmail("joao@example.com")).thenReturn(true);

        assertThrows(EmailJaCadastradoException.class, () -> usuarioService.cadastrar(usuarioBase));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cadastro com CPF duplicado deve lançar CpfJaCadastradoException")
    void cadastroCpfDuplicado() {
        when(usuarioRepository.existePorEmail(anyString())).thenReturn(false);
        when(usuarioRepository.existePorCpf(anyString())).thenReturn(true);

        assertThrows(CpfJaCadastradoException.class, () -> usuarioService.cadastrar(usuarioBase));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cadastro deve definir tipoPerfil como COMPRADOR")
    void cadastroDeveriDefinirPerfilComprador() {
        when(usuarioRepository.existePorEmail(anyString())).thenReturn(false);
        when(usuarioRepository.existePorCpf(anyString())).thenReturn(false);
        when(codificador.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(tokenRepository.save(any())).thenReturn(null);
        doNothing().when(emailService).enviarEmailVerificacao(any(), anyString());

        usuarioService.cadastrar(usuarioBase);

        assertEquals(TipoPerfil.COMPRADOR, usuarioBase.getTipoPerfil());
    }

    // --- Autenticação ---

    @Test
    @DisplayName("Login com credenciais válidas e e-mail verificado deve retornar token")
    void loginValido() {
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));
        when(codificador.matches("Senha@123", usuarioBase.getSenha())).thenReturn(true);
        when(jwtUtil.gerarToken(usuarioBase)).thenReturn("token_jwt");

        String token = usuarioService.autenticar("joao@example.com", "Senha@123");

        assertEquals("token_jwt", token);
    }

    @Test
    @DisplayName("Login com e-mail inexistente deve lançar CredenciaisInvalidasException")
    void loginEmailInexistente() {
        when(usuarioRepository.encontrarPorEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(CredenciaisInvalidasException.class,
                () -> usuarioService.autenticar("naoexiste@example.com", "Senha@123"));
    }

    @Test
    @DisplayName("Login com senha errada deve lançar CredenciaisInvalidasException")
    void loginSenhaErrada() {
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));
        when(codificador.matches(anyString(), any())).thenReturn(false);
        when(usuarioRepository.save(any())).thenReturn(usuarioBase);

        assertThrows(CredenciaisInvalidasException.class,
                () -> usuarioService.autenticar("joao@example.com", "SenhaErrada@1"));
    }

    @Test
    @DisplayName("Login com e-mail não verificado deve lançar EmailNaoVerificadoException")
    void loginEmailNaoVerificado() {
        usuarioBase.setEmailVerificado(false);
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));
        when(codificador.matches(anyString(), any())).thenReturn(true);

        assertThrows(EmailNaoVerificadoException.class,
                () -> usuarioService.autenticar("joao@example.com", "Senha@123"));
    }

    @Test
    @DisplayName("Login com conta inativa deve lançar ContaInativaException")
    void loginContaInativa() {
        usuarioBase.setAtivo(false);
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));

        assertThrows(ContaInativaException.class,
                () -> usuarioService.autenticar("joao@example.com", "Senha@123"));
    }

    @Test
    @DisplayName("Login com conta bloqueada deve lançar ContaBloqueadaException")
    void loginContaBloqueada() {
        usuarioBase.setBloqueadoAte(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));

        assertThrows(ContaBloqueadaException.class,
                () -> usuarioService.autenticar("joao@example.com", "Senha@123"));
    }

    @Test
    @DisplayName("Após 5 tentativas falhas a conta deve ser bloqueada")
    void contaDeveSerbloqueadaApos5TentativasFalhas() {
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));
        when(codificador.matches(anyString(), any())).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // 4 tentativas ainda não bloqueiam
        for (int i = 0; i < 4; i++) {
            assertThrows(CredenciaisInvalidasException.class,
                    () -> usuarioService.autenticar("joao@example.com", "SenhaErrada@1"));
        }
        assertNull(usuarioBase.getBloqueadoAte(), "Não deve estar bloqueado antes de 5 tentativas");

        // 5ª tentativa deve bloquear
        assertThrows(CredenciaisInvalidasException.class,
                () -> usuarioService.autenticar("joao@example.com", "SenhaErrada@1"));
        assertNotNull(usuarioBase.getBloqueadoAte(), "Deve estar bloqueado após 5 tentativas");
    }

    @Test
    @DisplayName("Login bem-sucedido deve resetar tentativas falhas")
    void loginSucessoDeveresetarTentativas() {
        usuarioBase.setTentativasLoginFalhas(3);
        when(usuarioRepository.encontrarPorEmail("joao@example.com")).thenReturn(Optional.of(usuarioBase));
        when(codificador.matches("Senha@123", usuarioBase.getSenha())).thenReturn(true);
        when(jwtUtil.gerarToken(usuarioBase)).thenReturn("token_jwt");
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        usuarioService.autenticar("joao@example.com", "Senha@123");

        assertEquals(0, usuarioBase.getTentativasLoginFalhas());
    }

    // --- Verificação de E-mail ---

    @Test
    @DisplayName("Verificação com token válido deve ativar o usuário")
    void verificacaoTokenValido() {
        TokenVerificacaoEmail tokenEntidade = new TokenVerificacaoEmail(
                "uuid-valido", usuarioBase, LocalDateTime.now().plusHours(24)
        );
        when(tokenRepository.encontrarPorToken("uuid-valido")).thenReturn(Optional.of(tokenEntidade));
        when(usuarioRepository.save(any())).thenReturn(usuarioBase);

        assertDoesNotThrow(() -> usuarioService.verificarEmail("uuid-valido"));
        assertTrue(usuarioBase.isEmailVerificado());
        verify(tokenRepository).delete(tokenEntidade);
    }

    @Test
    @DisplayName("Verificação com token inválido deve lançar TokenVerificacaoInvalidoException")
    void verificacaoTokenInvalido() {
        when(tokenRepository.encontrarPorToken("token-invalido")).thenReturn(Optional.empty());

        assertThrows(TokenVerificacaoInvalidoException.class,
                () -> usuarioService.verificarEmail("token-invalido"));
    }

    @Test
    @DisplayName("Verificação com token expirado deve lançar TokenVerificacaoInvalidoException")
    void verificacaoTokenExpirado() {
        TokenVerificacaoEmail tokenExpirado = new TokenVerificacaoEmail(
                "uuid-expirado", usuarioBase, LocalDateTime.now().minusHours(1)
        );
        when(tokenRepository.encontrarPorToken("uuid-expirado")).thenReturn(Optional.of(tokenExpirado));

        assertThrows(TokenVerificacaoInvalidoException.class,
                () -> usuarioService.verificarEmail("uuid-expirado"));
        verify(tokenRepository).delete(tokenExpirado);
    }
}
