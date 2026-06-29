package com.tone.controlador;

import com.tone.controlador.dto.CadastroRequisicao;
import com.tone.controlador.dto.LoginRequisicao;
import com.tone.controlador.dto.LoginResposta;
import com.tone.controlador.dto.UsuarioResposta;
import com.tone.modelo.Usuario;
import com.tone.seguranca.JwtUtil;
import com.tone.servico.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    public static final String NOME_COOKIE_TOKEN = "tone_token";

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    @Value("${spring.profiles.active:dev}")
    private String perfilAtivo;

    public AutenticacaoController(UsuarioService usuarioService, JwtUtil jwtUtil) {
        this.usuarioService = usuarioService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<Map<String, String>> cadastrar(@Valid @RequestBody CadastroRequisicao requisicao) {
        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(requisicao.nomeCompleto());
        usuario.setEmail(requisicao.email());
        usuario.setCpf(requisicao.cpf());
        usuario.setSenha(requisicao.senha());

        usuarioService.cadastrar(usuario);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("mensagem", "Cadastro realizado! Verifique seu e-mail para ativar sua conta."));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResposta> login(
            @Valid @RequestBody LoginRequisicao requisicao,
            HttpServletResponse response
    ) {
        String token = usuarioService.autenticar(requisicao.email(), requisicao.senha());
        Usuario usuario = usuarioService.buscarPorEmail(requisicao.email());

        // Token enviado via HttpOnly Cookie — nunca exposto no body da resposta
        ResponseCookie cookie = criarCookieToken(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        UsuarioResposta usuarioResposta = new UsuarioResposta(
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getTipoPerfil(),
                usuario.getDataCriacao()
        );

        return ResponseEntity.ok(new LoginResposta(usuarioResposta));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResposta> obterUsuarioLogado(HttpServletRequest request) {
        String token = extrairTokenDoCookie(request);

        if (token == null || !jwtUtil.validarToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = jwtUtil.extrairEmail(token);
        Usuario usuario = usuarioService.buscarPorEmail(email);

        UsuarioResposta resposta = new UsuarioResposta(
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getTipoPerfil(),
                usuario.getDataCriacao()
        );

        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        // Sobrescreve o cookie com maxAge=0 para forçar a remoção no browser
        ResponseCookie cookie = ResponseCookie.from(NOME_COOKIE_TOKEN, "")
                .httpOnly(true)
                .secure(!perfilAtivo.equals("dev"))
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("mensagem", "Logout realizado com sucesso."));
    }

    @GetMapping("/verificar-email")
    public ResponseEntity<Map<String, String>> verificarEmail(@RequestParam String token) {
        usuarioService.verificarEmail(token);
        return ResponseEntity.ok(Map.of("mensagem", "E-mail verificado com sucesso! Agora você pode fazer login."));
    }

    private ResponseCookie criarCookieToken(String token) {
        // Em dev o Secure=false pois não há HTTPS em localhost
        boolean emProducao = !perfilAtivo.equals("dev");

        return ResponseCookie.from(NOME_COOKIE_TOKEN, token)
                .httpOnly(true)
                .secure(emProducao)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(1))
                .build();
    }

    private String extrairTokenDoCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(c -> NOME_COOKIE_TOKEN.equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
