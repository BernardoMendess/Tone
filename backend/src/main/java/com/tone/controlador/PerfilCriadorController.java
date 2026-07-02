package com.tone.controlador;

import com.tone.controlador.dto.PerfilCriadorRequisicaoDTO;
import com.tone.controlador.dto.PerfilCriadorRespostaDTO;
import com.tone.controlador.dto.S3UploadPresignResponseDTO;
import com.tone.modelo.Usuario;
import com.tone.servico.PerfilCriadorService;
import com.tone.servico.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/perfis")
public class PerfilCriadorController {

    private final PerfilCriadorService perfilService;
    private final UsuarioService usuarioService;

    public PerfilCriadorController(PerfilCriadorService perfilService, UsuarioService usuarioService) {
        this.perfilService = perfilService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/{usuarioId}")
    public ResponseEntity<PerfilCriadorRespostaDTO> obterPerfilPublico(@PathVariable Long usuarioId) {
        PerfilCriadorRespostaDTO response = perfilService.obterPerfilPublico(usuarioId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CRIADOR', 'ADMIN')")
    public ResponseEntity<PerfilCriadorRespostaDTO> obterMeuPerfil(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        PerfilCriadorRespostaDTO response = perfilService.obterOuCriarPerfil(usuario.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('CRIADOR', 'ADMIN')")
    public ResponseEntity<PerfilCriadorRespostaDTO> atualizarMeuPerfil(
            @Valid @RequestBody PerfilCriadorRequisicaoDTO requisicao,
            Authentication authentication
    ) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        PerfilCriadorRespostaDTO response = perfilService.atualizarPerfil(usuario.getId(), requisicao);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-imagem")
    @PreAuthorize("hasAnyRole('CRIADOR', 'ADMIN')")
    public ResponseEntity<S3UploadPresignResponseDTO> solicitarUrlUpload(
            @RequestBody Map<String, String> payload,
            Authentication authentication
    ) {
        String mimeType = payload.get("mimeType");
        if (mimeType == null || mimeType.trim().isEmpty()) {
            throw new IllegalArgumentException("O mimeType é obrigatório");
        }

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        S3UploadPresignResponseDTO response = perfilService.gerarUrlUploadFotoPerfil(usuario.getId(), mimeType);
        return ResponseEntity.ok(response);
    }
}
