package com.tone.controlador;

import com.tone.controlador.dto.ProdutoRequest;
import com.tone.controlador.dto.ProdutoResponse;
import com.tone.controlador.dto.UploadUrlRequest;
import com.tone.controlador.dto.UploadUrlResponse;
import com.tone.modelo.Produto;
import com.tone.servico.ProdutoServico;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoControlador {

    private final ProdutoServico produtoServico;

    public ProdutoControlador(ProdutoServico produtoServico) {
        this.produtoServico = produtoServico;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> solicitarUrlUpload(
            @Valid @RequestBody UploadUrlRequest request,
            Authentication authentication) {

        UploadUrlResponse response = produtoServico.gerarUrlUpload(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProdutoResponse> cadastrarProduto(
            @Valid @RequestBody ProdutoRequest request,
            Authentication authentication) {

        Produto produto = produtoServico.criarProduto(request, authentication.getName());

        ProdutoResponse response = new ProdutoResponse(
                produto.getId(),
                produto.getStatus(),
                "Produto cadastrado com sucesso."
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<String> gerarUrlDownload(
            @PathVariable UUID id,
            Authentication authentication) {
        // TODO (RF08): Verificar se authentication.getName() possui Compra aprovada para este Produto.
        String presignedUrl = produtoServico.gerarUrlDownload(id);
        return ResponseEntity.ok(presignedUrl);
    }

    @GetMapping
    public ResponseEntity<java.util.List<com.tone.controlador.dto.ProdutoDetalheResponseDTO>> listarProdutos(
            @RequestParam(required = false) Long criadorId
    ) {
        if (criadorId != null) {
            java.util.List<com.tone.controlador.dto.ProdutoDetalheResponseDTO> produtos = 
                    produtoServico.buscarPorCriadorEStatus(criadorId, com.tone.modelo.enums.StatusProduto.PUBLICADO);
            return ResponseEntity.ok(produtos);
        }
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }
}
