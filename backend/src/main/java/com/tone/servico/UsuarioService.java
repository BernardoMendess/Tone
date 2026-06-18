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
import java.util.UUID;

/**
 * Contém toda a lógica de negócio relacionada ao usuário:
 * cadastro, autenticação e verificação de e-mail.
 */
@Service
public class UsuarioService {

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

    @Transactional(readOnly = true)
    public String autenticar(String email, String senha) {
        Usuario usuario = usuarioRepository.encontrarPorEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!codificador.matches(senha, usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        if (!usuario.isEmailVerificado()) {
            throw new EmailNaoVerificadoException();
        }

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
}
