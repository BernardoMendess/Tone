package com.tone.controlador;

import com.tone.controlador.dto.CadastroRequisicao;
import com.tone.controlador.dto.LoginRequisicao;
import com.tone.controlador.dto.LoginResposta;
import com.tone.controlador.dto.UsuarioResposta;
import com.tone.modelo.Usuario;
import com.tone.servico.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final UsuarioService usuarioService;

    public AutenticacaoController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
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
    public ResponseEntity<LoginResposta> login(@Valid @RequestBody LoginRequisicao requisicao) {
        String token = usuarioService.autenticar(requisicao.email(), requisicao.senha());
        Usuario usuario = usuarioService.buscarPorEmail(requisicao.email());

        UsuarioResposta usuarioResposta = new UsuarioResposta(
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail(),
                usuario.getCpf(),
                usuario.getTipoPerfil(),
                usuario.getDataCriacao()
        );

        return ResponseEntity.ok(new LoginResposta(token, usuarioResposta));
    }

    @GetMapping("/verificar-email")
    public ResponseEntity<Map<String, String>> verificarEmail(@RequestParam String token) {
        usuarioService.verificarEmail(token);
        return ResponseEntity.ok(Map.of("mensagem", "E-mail verificado com sucesso! Agora você pode fazer login."));
    }
}
