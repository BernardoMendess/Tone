package com.tone.controller;

import com.tone.domain.enums.CreatorStatus;
import com.tone.dto.AdminDtos;
import com.tone.dto.CreatorDtos;
import com.tone.dto.OrderDtos;
import com.tone.dto.PageResponse;
import com.tone.security.UserPrincipal;
import com.tone.service.AdminCurationService;
import com.tone.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminCurationService adminCurationService;
    private final RefundService refundService;

    public AdminController(AdminCurationService adminCurationService, RefundService refundService) {
        this.adminCurationService = adminCurationService;
        this.refundService = refundService;
    }

    @GetMapping("/overview")
    public AdminDtos.AdminOverviewDto getOverview() {
        return adminCurationService.getOverview();
    }

    @GetMapping("/creators")
    public PageResponse<CreatorDtos.CreatorProfileDto> listCreators(
            @RequestParam(required = false) CreatorStatus status,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return adminCurationService.listCreators(status, pageable);
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

    @PostMapping("/creators/{creatorProfileId}/suspend")
    public CreatorDtos.CreatorProfileDto suspend(
            @PathVariable UUID creatorProfileId,
            @Valid @RequestBody AdminDtos.SuspendCreatorRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return adminCurationService.suspendCreator(principal.getId(), creatorProfileId, request.reason());
    }

    @PostMapping("/orders/{orderId}/refund")
    public OrderDtos.OrderDto refundOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminDtos.RefundOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return refundService.refundByAdmin(orderId, principal.getId(), request.reason());
    }

    @PutMapping("/settings/take-rate")
    public Map<String, BigDecimal> updateTakeRate(@Valid @RequestBody AdminDtos.UpdateTakeRateRequest request) {
        BigDecimal updated = adminCurationService.updateTakeRate(request.takeRatePercent());
        return Map.of("takeRatePercent", updated);
    }
}
