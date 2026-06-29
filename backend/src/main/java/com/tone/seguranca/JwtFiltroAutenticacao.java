package com.tone.seguranca;

import com.tone.controlador.AutenticacaoController;
import com.tone.modelo.Usuario;
import com.tone.repositorio.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Filtro JWT que intercepta cada requisição, extrai o token do cookie HttpOnly,
 * valida-o e verifica se o usuário ainda existe e está ativo no banco de dados.
 */
@Component
public class JwtFiltroAutenticacao extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;

    public JwtFiltroAutenticacao(JwtUtil jwtUtil, UsuarioRepository usuarioRepository) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extrairTokenDoCookie(request);

        if (token != null && jwtUtil.validarToken(token)) {
            String email = jwtUtil.extrairEmail(token);

            // Valida se o usuário ainda existe e está ativo no banco — tokens de contas
            // deletadas ou desativadas são automaticamente rejeitados.
            Optional<Usuario> usuarioOpt = usuarioRepository.encontrarPorEmail(email);

            if (usuarioOpt.isPresent() && usuarioOpt.get().isAtivo()) {
                String tipoPerfil = jwtUtil.extrairTipoPerfil(token);

                UsernamePasswordAuthenticationToken autenticacao = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + tipoPerfil))
                );

                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extrairTokenDoCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(c -> AutenticacaoController.NOME_COOKIE_TOKEN.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
