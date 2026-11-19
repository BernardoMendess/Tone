package com.toneguitar.controller;

import com.toneguitar.domain.enums.CreatorStatus;
import com.toneguitar.dto.AdminDtos;
import com.toneguitar.dto.CreatorDtos;
import com.toneguitar.security.UserPrincipal;
import com.toneguitar.service.AdminCurationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminCurationService adminCurationService;

    public AdminController(AdminCurationService adminCurationService) {
        this.adminCurationService = adminCurationService;
    }

    @GetMapping("/overview")
    public AdminDtos.AdminOverviewDto getOverview() {
        return adminCurationService.getOverview();
    }

    @GetMapping("/creators")
    public List<CreatorDtos.CreatorProfileDto> listCreators(@RequestParam(required = false) CreatorStatus status) {
        return adminCurationService.listCreators(status);
    }

    @PostMapping("/creators/{creatorProfileId}/approve")
    public CreatorDtos.CreatorProfileDto approve(
            @PathVariable UUID creatorProfileId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return adminCurationService.approveCreator(principal.getId(), creatorProfileId);
    }

    @PostMapping("/creators/{creatorProfileId}/reject")
    public CreatorDtos.CreatorProfileDto reject(
            @PathVariable UUID creatorProfileId,
            @Valid @RequestBody AdminDtos.RejectCreatorRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return adminCurationService.rejectCreator(principal.getId(), creatorProfileId, request.reason());
    }

    @PutMapping("/settings/take-rate")
    public Map<String, BigDecimal> updateTakeRate(@Valid @RequestBody AdminDtos.UpdateTakeRateRequest request) {
        BigDecimal updated = adminCurationService.updateTakeRate(request.takeRatePercent());
        return Map.of("takeRatePercent", updated);
    }
}
