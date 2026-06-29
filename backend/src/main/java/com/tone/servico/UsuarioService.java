package com.tone.servico;

import com.tone.controlador.excecao.*;
import com.tone.modelo.TokenVerificacaoEmail;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import com.tone.repositorio.TokenVerificacaoEmailRepository;
import com.tone.repositorio.UsuarioRepository;
import com.tone.seguranca.Argon2Codificador;
import com.tone.seguranca.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Contém toda a lógica de negócio relacionada ao usuário:
 * cadastro, autenticação, verificação de e-mail e account lockout.
 */
@Service
public class UsuarioService {

    private static final int MAX_TENTATIVAS_FALHAS = 5;
    private static final int MINUTOS_BLOQUEIO = 15;

    private final UsuarioRepository usuarioRepository;
    private final TokenVerificacaoEmailRepository tokenRepository;
    private final Argon2Codificador codificador;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            TokenVerificacaoEmailRepository tokenRepository,
            Argon2Codificador codificador,
            JwtUtil jwtUtil,
            EmailService emailService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.codificador = codificador;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @Transactional
    public Usuario cadastrar(Usuario usuario) {
        if (usuarioRepository.existePorEmail(usuario.getEmail())) {
            throw new EmailJaCadastradoException(usuario.getEmail());
        }

        if (usuarioRepository.existePorCpf(usuario.getCpf())) {
            throw new CpfJaCadastradoException(usuario.getCpf());
        }

        usuario.setSenha(codificador.encode(usuario.getSenha()));
        usuario.setTipoPerfil(TipoPerfil.COMPRADOR);
        usuario.setEmailVerificado(false);

        Usuario usuarioSalvo = usuarioRepository.save(usuario);

        String token = UUID.randomUUID().toString();
        TokenVerificacaoEmail tokenVerificacao = new TokenVerificacaoEmail(
                token,
                usuarioSalvo,
                LocalDateTime.now().plusHours(24)
        );
        tokenRepository.save(tokenVerificacao);

        emailService.enviarEmailVerificacao(usuarioSalvo, token);

        return usuarioSalvo;
    }

    @Transactional
    public String autenticar(String email, String senha) {
        // Usar mensagem genérica para não revelar se o e-mail existe ou não
        Usuario usuario = usuarioRepository.encontrarPorEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        // Verificar bloqueio por tentativas excessivas
        if (usuario.estaBloqueado()) {
            long minutosRestantes = ChronoUnit.MINUTES.between(LocalDateTime.now(), usuario.getBloqueadoAte()) + 1;
            throw new ContaBloqueadaException(minutosRestantes);
        }

        // Verificar conta ativa
        if (!usuario.isAtivo()) {
            throw new ContaInativaException();
        }

        // Verificar senha — em caso de falha, registrar tentativa e potencialmente bloquear
        if (!codificador.matches(senha, usuario.getSenha())) {
            registrarTentativaFalha(usuario);
            throw new CredenciaisInvalidasException();
        }

        if (!usuario.isEmailVerificado()) {
            throw new EmailNaoVerificadoException();
        }

        // Login bem-sucedido: resetar contador de falhas
        resetarTentativasFalhas(usuario);

        return jwtUtil.gerarToken(usuario);
    }

    @Transactional
    public void verificarEmail(String token) {
        TokenVerificacaoEmail tokenVerificacao = tokenRepository.encontrarPorToken(token)
                .orElseThrow(TokenVerificacaoInvalidoException::new);

        if (tokenVerificacao.estaExpirado()) {
            tokenRepository.delete(tokenVerificacao);
            throw new TokenVerificacaoInvalidoException();
        }

        Usuario usuario = tokenVerificacao.getUsuario();
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);

        tokenRepository.delete(tokenVerificacao);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.encontrarPorEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    // --- Métodos privados de lockout ---

    private void registrarTentativaFalha(Usuario usuario) {
        int novasTentativas = usuario.getTentativasLoginFalhas() + 1;
        usuario.setTentativasLoginFalhas(novasTentativas);

        if (novasTentativas >= MAX_TENTATIVAS_FALHAS) {
            usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEIO));
            usuario.setTentativasLoginFalhas(0);
        }

        usuarioRepository.save(usuario);
    }

    private void resetarTentativasFalhas(Usuario usuario) {
        if (usuario.getTentativasLoginFalhas() > 0 || usuario.getBloqueadoAte() != null) {
            usuario.setTentativasLoginFalhas(0);
            usuario.setBloqueadoAte(null);
            usuarioRepository.save(usuario);
        }
    }
}
