package com.tone.controller;

import com.tone.domain.entity.Product;
import com.tone.exception.ApiException;
import com.tone.repository.ProductRepository;
import com.tone.storage.FileStorageService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.UUID;

/**
 * RF02 + RNF03 + RNF04: Streaming protegido de demonstrações normalizadas e comprimidas.
 * Suporta HTTP Range requests para seek fluido no player global sem expor URL pública de bucket
 * e com cabeçalhos anti-cache/anti-download para inibir ripping de Impulse Responses.
 */
@RestController
@RequestMapping("/api/audio")
public class AudioStreamController {

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    public AudioStreamController(ProductRepository productRepository, FileStorageService fileStorageService) {
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/demo/{productId}")
    public ResponseEntity<byte[]> streamDemo(
            @PathVariable UUID productId,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        if (product.getDemoAudioKey() == null || product.getDemoAudioKey().isBlank()) {
            throw ApiException.notFound("Áudio de demonstração indisponível.");
        }

        byte[] audioBytes = fileStorageService.readObjectBytes(product.getDemoAudioKey());
        MediaType mediaType = product.getDemoAudioKey().endsWith(".mp3")
                ? MediaType.parseMediaType("audio/mpeg")
                : MediaType.parseMediaType("audio/wav");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Audio-LUFS", String.valueOf(product.getDemoLoudnessLufs()));
        headers.set("X-Audio-Protection", "COMPRESSED_PREVIEW_NO_RAW_IR");

        int totalLen = audioBytes.length;
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-");
            int start = ranges[0].isEmpty() ? 0 : Integer.parseInt(ranges[0]);
            int end = (ranges.length > 1 && !ranges[1].isEmpty())
                    ? Math.min(Integer.parseInt(ranges[1]), totalLen - 1)
                    : totalLen - 1;
            if (start < 0 || start >= totalLen || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalLen)
                        .build();
            }
            byte[] slice = Arrays.copyOfRange(audioBytes, start, end + 1);
            headers.setContentLength(slice.length);
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalLen);
            return new ResponseEntity<>(slice, headers, HttpStatus.PARTIAL_CONTENT);
        }

        headers.setContentLength(totalLen);
        return new ResponseEntity<>(audioBytes, headers, HttpStatus.OK);
    }
}
