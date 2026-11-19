package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.OrderItem;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.PayoutStatus;
import com.tone.domain.enums.SplitStatus;
import com.tone.dto.CreatorDtos;
import com.tone.repository.OrderItemRepository;
import com.tone.repository.PaymentSplitRepository;
import com.tone.repository.PayoutRepository;
import com.tone.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A4 + A9: Financial dashboard powered by DB-side monthly aggregation and paginated recent sales.
 */
@Service
public class CreatorDashboardService {

    private final CreatorProfileService creatorProfileService;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final PayoutRepository payoutRepository;
    private final PlatformSettingsService platformSettingsService;
    private final Clock clock;

    public CreatorDashboardService(
            CreatorProfileService creatorProfileService,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            PaymentSplitRepository paymentSplitRepository,
            PayoutRepository payoutRepository,
            PlatformSettingsService platformSettingsService,
            Clock clock
    ) {
        this.creatorProfileService = creatorProfileService;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.payoutRepository = payoutRepository;
        this.platformSettingsService = platformSettingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CreatorDtos.CreatorDashboardDto getFinancialDashboard(UUID userId) {
        CreatorProfile creator = creatorProfileService.requireApprovedCreator(userId);
        UUID creatorId = creator.getId();

        long totalSalesCount = orderItemRepository.countSalesByCreator(creatorId, OrderStatus.PAID);
        long activeProductsCount = productRepository.countByCreatorIdAndActiveTrue(creatorId);

        BigDecimal gross = paymentSplitRepository.sumGrossByCreatorAndStatus(creatorId, SplitStatus.SETTLED)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = paymentSplitRepository.sumPlatformFeeByCreatorAndStatus(creatorId, SplitStatus.SETTLED)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = paymentSplitRepository.sumNetByCreatorAndStatus(creatorId, SplitStatus.SETTLED)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal availableNet = paymentSplitRepository.sumAvailableNetByCreator(
                        creatorId, SplitStatus.SETTLED, clock.instant())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawn = payoutRepository.sumAmountByCreatorAndStatusIn(
                        creatorId, List.of(PayoutStatus.PROCESSING, PayoutStatus.COMPLETED))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal held = net.subtract(availableNet).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal available = availableNet.subtract(withdrawn).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        List<CreatorDtos.MonthlyRevenuePoint> series = orderItemRepository
                .monthlyRevenueByCreator(creatorId, OrderStatus.PAID)
                .stream()
                .map(row -> new CreatorDtos.MonthlyRevenuePoint(
                        String.format("%04d-%02d", row.getYear(), row.getMonth()),
                        row.getGross().setScale(2, RoundingMode.HALF_UP),
                        row.getNet().setScale(2, RoundingMode.HALF_UP),
                        (int) row.getSalesCount()
                ))
                .toList();

        Page<OrderItem> recentPage = orderItemRepository.findSalesByCreator(
                creatorId, OrderStatus.PAID, PageRequest.of(0, 50));
        List<CreatorDtos.SaleRecordDto> recentSales = recentPage.getContent().stream()
                .map(oi -> {
                    Instant paidAt = oi.getOrder().getPaidAt() != null
                            ? oi.getOrder().getPaidAt()
                            : oi.getOrder().getCreatedAt();
                    return new CreatorDtos.SaleRecordDto(
                            oi.getOrder().getId(),
                            oi.getOrder().getOrderCode(),
                            oi.getProductTitleSnapshot(),
                            oi.getOrder().getBuyer().getFullName(),
                            oi.getUnitPrice(),
                            oi.getPlatformFeeAmount(),
                            oi.getCreatorNetAmount(),
                            oi.getTakeRatePercent(),
                            paidAt
                    );
                })
                .toList();

        List<CreatorDtos.PayoutDto> payouts = payoutRepository.findByCreatorIdOrderByRequestedAtDesc(creatorId)
                .stream()
                .map(PayoutService::toDto)
                .toList();

        return new CreatorDtos.CreatorDashboardDto(
                (int) totalSalesCount,
                (int) activeProductsCount,
                gross,
                fee,
                net,
                withdrawn,
                held,
                available,
                platformSettingsService.getTakeRatePercent(),
                series,
                recentSales,
                payouts
        );
    }
}
