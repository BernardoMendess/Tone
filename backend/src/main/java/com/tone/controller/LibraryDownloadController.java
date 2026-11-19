package com.tone.controller;

import com.tone.dto.OrderDtos;
import com.tone.security.UserPrincipal;
import com.tone.service.LibraryDownloadService;
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

    public LibraryDownloadController(LibraryDownloadService libraryDownloadService) {
        this.libraryDownloadService = libraryDownloadService;
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
}
