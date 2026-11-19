package com.tone.controller;

import com.tone.exception.ApiException;
import com.tone.storage.FileStorageService;
import com.tone.storage.LocalPresignedTokenVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B4 + A6 + B6: Controller exclusivo do modo Local Vault para entrega por streaming (FileSystemResource)
 * de URLs pré-assinadas de uso único. Em modo S3/MinIO este bean nem é registrado.
 */
@RestController
@RequestMapping("/api/downloads")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPresignedDownloadController {

    private final LocalPresignedTokenVerifier tokenVerifier;
    private final FileStorageService fileStorageService;

    public LocalPresignedDownloadController(
            LocalPresignedTokenVerifier tokenVerifier,
            FileStorageService fileStorageService
    ) {
        this.tokenVerifier = tokenVerifier;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/presigned")
    public ResponseEntity<Resource> downloadPresigned(
            @RequestParam String token,
            @RequestParam long expires,
            @RequestParam String sig
    ) {
        LocalPresignedTokenVerifier.VerifiedPresignedPayload verified =
                tokenVerifier.verifyAndConsumeLocalPresignedToken(token, expires, sig);

        Resource resource = fileStorageService.openLocal(verified.storageKey())
                .orElseThrow(() -> ApiException.notFound("Arquivo solicitado não encontrado no cofre privado."));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(verified.filename()).build().toString())
                .body(resource);
    }
}
