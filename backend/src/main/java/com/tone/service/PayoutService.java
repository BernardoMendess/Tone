package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Payout;
import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.PayoutStatus;
import com.tone.domain.enums.SplitStatus;
import com.tone.dto.CreatorDtos;
import com.tone.exception.ApiException;
import com.tone.repository.CreatorProfileRepository;
import com.tone.repository.PaymentSplitRepository;
import com.tone.repository.PayoutRepository;
import com.tone.service.event.PayoutRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * C5: Race-free, idempotent payout service.
 * Serializes withdrawals per creator using PESSIMISTIC_WRITE, enforces hold period,
 * restricts destination strictly to the creator's registered PIX key, and dispatches asynchronously after commit.
 */
@Service
public class PayoutService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final PayoutRepository payoutRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PayoutService(
            CreatorProfileRepository creatorProfileRepository,
            PaymentSplitRepository paymentSplitRepository,
            PayoutRepository payoutRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.creatorProfileRepository = creatorProfileRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.payoutRepository = payoutRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public CreatorDtos.PayoutDto requestPayout(
            UUID userId,
            String idempotencyKey,
            CreatorDtos.RequestPayoutRequest req
    ) {
        // Pessimistic row lock on creator profile serializes concurrent payout requests
        CreatorProfile creator = creatorProfileRepository.findByUserIdForUpdate(userId)
                .filter(c -> c.getStatus() == CreatorStatus.APPROVED)
                .orElseThrow(() -> ApiException.forbidden("Seu perfil de criador não está aprovado para saques."));

        Optional<Payout> replay = payoutRepository.findByCreatorIdAndIdempotencyKey(creator.getId(), idempotencyKey);
        if (replay.isPresent()) {
            return toDto(replay.get());
        }

        if (creator.getPixKey() == null || creator.getPixKey().isBlank()) {
            throw ApiException.badRequest("Cadastre uma chave PIX válida no seu perfil antes de solicitar saques.");
        }

        BigDecimal available = availableBalance(creator.getId());
        if (req.amount().compareTo(available) > 0) {
            throw ApiException.unprocessable(String.format(
                    "Saldo insuficiente para saque. Disponível liberado: R$ %.2f.", available
            ));
        }

        Payout payout = Payout.request(
                creator,
                req.amount(),
                creator.getPixKey(),
                idempotencyKey,
                clock.instant()
        );
        payout = payoutRepository.save(payout);
        eventPublisher.publishEvent(new PayoutRequestedEvent(payout.getId()));
        return toDto(payout);
    }

    @Transactional(readOnly = true)
    public BigDecimal availableBalance(UUID creatorId) {
        BigDecimal availableNet = paymentSplitRepository.sumAvailableNetByCreator(
                creatorId, SplitStatus.SETTLED, clock.instant());
        BigDecimal reserved = payoutRepository.sumAmountByCreatorAndStatusIn(
                creatorId, List.of(PayoutStatus.PROCESSING, PayoutStatus.COMPLETED));
        return availableNet.subtract(reserved).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static CreatorDtos.PayoutDto toDto(Payout p) {
        return new CreatorDtos.PayoutDto(
                p.getId(),
                p.getAmount(),
                p.getPixKey(),
                p.getStatus(),
                p.getRequestedAt(),
                p.getCompletedAt()
        );
    }
}
