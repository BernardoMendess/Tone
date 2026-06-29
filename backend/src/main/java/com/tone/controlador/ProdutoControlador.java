package com.tone.controlador;

import com.tone.controlador.dto.ProdutoRequest;
import com.tone.controlador.dto.ProdutoResponse;
import com.tone.controlador.dto.UploadUrlRequest;
import com.tone.controlador.dto.UploadUrlResponse;
import com.tone.modelo.Produto;
import com.tone.servico.ProdutoServico;
import com.tone.servico.S3Service;
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
    private final S3Service s3Service;

    public ProdutoControlador(ProdutoServico produtoServico, S3Service s3Service) {
        this.produtoServico = produtoServico;
        this.s3Service = s3Service;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> solicitarUrlUpload(
            @Valid @RequestBody UploadUrlRequest request,
            Authentication authentication) {
        
        // Na vida real, o email (ou username) vem da Authentication JWT
        String userEmail = authentication.getName();
        
        // Gera um identificador único para o arquivo do produto
        String uuidArquivo = UUID.randomUUID().toString();
        // Ex: timbres/email@teste.com/123e4567-e89b-12d3.zip
        String objectKey = "timbres/" + userEmail + "/" + uuidArquivo + "_" + request.getNomeArquivo();

        int expiraEmSegundos = 900; // 15 minutos

        String presignedUrl = s3Service.gerarUrlUpload(objectKey, request.getMimeType(), expiraEmSegundos);

        UploadUrlResponse response = new UploadUrlResponse(presignedUrl, objectKey, expiraEmSegundos);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProdutoResponse> cadastrarProduto(
            @Valid @RequestBody ProdutoRequest request,
            Authentication authentication) {
        
        String userEmail = authentication.getName();
        
        Produto produto = produtoServico.criarProduto(request, userEmail);

        ProdutoResponse response = new ProdutoResponse(
                produto.getId(),
                produto.getStatus(),
                "Produto cadastrado com sucesso."
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> gerarUrlDownload(@PathVariable UUID id, Authentication authentication) {
        // TODO: Verificar se o authentication.getName() (Usuário) tem uma Compra aprovada para este Produto UUID.
        // Para fins deste requisito (RF07), geraremos a URL assumindo validação de domínio futura.
        
        // Suponha que buscamos o produto no BD e obtemos o arquivoUri
        Produto produto = produtoServico.obterProduto(id);
        
        int expiraEmSegundos = 300; // 5 minutos para download
        String presignedUrl = s3Service.gerarUrlDownload(produto.getArquivoUri(), expiraEmSegundos);
        
        return ResponseEntity.ok(presignedUrl);
    }
}
