package com.tone.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record RejectCreatorRequest(
            @NotBlank String reason
    ) {}

    public record UpdateTakeRateRequest(
            @NotNull @DecimalMin("1.00") @DecimalMax("40.00") BigDecimal takeRatePercent
    ) {}

    public record AdminOverviewDto(
            long totalUsers,
            long approvedCreators,
            long pendingCreators,
            long activeProducts,
            long paidOrdersCount,
            BigDecimal gmvTotal,
            BigDecimal platformRevenueTotal,
            BigDecimal currentTakeRatePercent,
            List<CreatorDtos.CreatorProfileDto> pendingQueue
    ) {}
}
