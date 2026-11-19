package com.tone.controller;

import com.tone.dto.OrderDtos;
import com.tone.security.UserPrincipal;
import com.tone.service.LibraryDownloadService;
import com.tone.storage.FileStorageService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RF04 (Biblioteca Digital) + RNF02 (Presigned URLs temporárias sem URLs públicas de patches).
 */
@RestController
@RequestMapping("/api")
public class LibraryDownloadController {

    private final LibraryDownloadService libraryDownloadService;
    private final FileStorageService fileStorageService;

    public LibraryDownloadController(
            LibraryDownloadService libraryDownloadService,
            FileStorageService fileStorageService
    ) {
        this.libraryDownloadService = libraryDownloadService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/library")
    public List<OrderDtos.LibraryItemDto> getMyLibrary(@AuthenticationPrincipal UserPrincipal principal) {
        return libraryDownloadService.getDigitalLibrary(principal.getId());
    }

    @PostMapping("/library/downloads/{productId}/presign")
    public OrderDtos.PresignedDownloadDto presignDownload(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID versionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return libraryDownloadService.generatePresignedDownload(principal.getId(), productId, versionId);
    }

    /**
     * Endpoint de entrega de Presigned URL no modo Local Vault (HMAC-SHA256 + expiração curta).
     * Em modo S3/MinIO, a Presigned URL aponta diretamente para o AWS S3 / MinIO S3Presigner.
     */
    @GetMapping("/downloads/presigned")
    public ResponseEntity<byte[]> downloadPresigned(
            @RequestParam String token,
            @RequestParam long expires,
            @RequestParam String sig
    ) {
        FileStorageService.VerifiedPresignedPayload verified =
                fileStorageService.verifyLocalPresignedToken(token, expires, sig);
        byte[] bytes = fileStorageService.readObjectBytes(verified.storageKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(verified.filename()).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
