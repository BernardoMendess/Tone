package com.tone.controller;

import com.tone.domain.entity.Product;
import com.tone.domain.enums.CreatorStatus;
import com.tone.exception.ApiException;
import com.tone.repository.ProductRepository;
import com.tone.storage.FileStorageService;
import com.tone.storage.PresignedDownload;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RF02 + RNF03 + RNF04 + A6: Streaming protegido de demonstrações via Resource / ResourceRegion
 * (sem carregar o arquivo inteiro em memória) e redirecionamento para Presigned URL curta em modo S3.
 */
@RestController
@RequestMapping("/api/audio")
public class AudioStreamController {

    private static final long MAX_CHUNK_BYTES = 512 * 1024;

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    public AudioStreamController(ProductRepository productRepository, FileStorageService fileStorageService) {
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping(value = "/demo/{productId}", headers = "!Range")
    public ResponseEntity<Resource> streamDemoFull(@PathVariable UUID productId) throws IOException {
        Product product = resolvePublicProductWithDemo(productId);
        Optional<Resource> localOpt = fileStorageService.openLocal(product.getDemoAudioKey());
        if (localOpt.isEmpty()) {
            return redirectToS3(product);
        }
        Resource resource = localOpt.get();
        HttpHeaders headers = baseHeaders(product);
        headers.setContentLength(resource.contentLength());
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @GetMapping(value = "/demo/{productId}", headers = "Range")
    public ResponseEntity<ResourceRegion> streamDemoRange(
            @PathVariable UUID productId,
            @RequestHeader(HttpHeaders.RANGE) String rawRange
    ) throws IOException {
        Product product = resolvePublicProductWithDemo(productId);
        Optional<Resource> localOpt = fileStorageService.openLocal(product.getDemoAudioKey());
        if (localOpt.isEmpty()) {
            return redirectToS3(product);
        }

        Resource resource = localOpt.get();
        long contentLength = resource.contentLength();
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rawRange);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }
        if (ranges.isEmpty()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        if (start < 0 || start >= contentLength || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        long rangeLength = Math.min(MAX_CHUNK_BYTES, end - start + 1);
        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(baseHeaders(product))
                .body(region);
    }

    private Product resolvePublicProductWithDemo(UUID productId) {
        return productRepository.findPublicById(productId, CreatorStatus.APPROVED)
                .filter(p -> p.getDemoAudioKey() != null && !p.getDemoAudioKey().isBlank())
                .orElseThrow(() -> ApiException.notFound("Áudio de demonstração indisponível."));
    }

    private <T> ResponseEntity<T> redirectToS3(Product product) {
        String key = product.getDemoAudioKey();
        PresignedDownload presigned = fileStorageService.generatePresignedDownloadUrl(
                key,
                key.endsWith(".mp3") ? "demo.mp3" : "demo.wav",
                null,
                Duration.ofSeconds(60),
                true
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(presigned.url()))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private HttpHeaders baseHeaders(Product product) {
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
        return headers;
    }
}
