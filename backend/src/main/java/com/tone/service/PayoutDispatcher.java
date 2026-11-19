package com.tone.service;

import com.tone.domain.entity.Payout;
import com.tone.domain.enums.PayoutStatus;
import com.tone.payment.PayoutGateway;
import com.tone.repository.PayoutRepository;
import com.tone.service.event.PayoutRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * C5: Dispatches payout transfers strictly AFTER database commit so a rolled-back transaction
 * can never trigger an outbound PIX transfer.
 */
@Component
public class PayoutDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PayoutDispatcher.class);

    private final PayoutRepository payoutRepository;
    private final PayoutGateway payoutGateway;
    private final Clock clock;

    public PayoutDispatcher(PayoutRepository payoutRepository, PayoutGateway payoutGateway, Clock clock) {
        this.payoutRepository = payoutRepository;
        this.payoutGateway = payoutGateway;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPayoutRequested(PayoutRequestedEvent event) {
        Payout payout = payoutRepository.findById(event.payoutId()).orElse(null);
        if (payout == null || payout.getStatus() != PayoutStatus.PROCESSING) {
            return;
        }
        try {
            PayoutGateway.TransferResult res = payoutGateway.executePixTransfer(
                    payout.getId(), payout.getPixKey(), payout.getAmount());
            if (res.accepted()) {
                payout.markCompleted(clock.instant());
            } else {
                payout.markFailed();
            }
            payoutRepository.save(payout);
        } catch (Exception ex) {
            log.error("Falha ao despachar saque {}", payout.getId(), ex);
            payout.markFailed();
            payoutRepository.save(payout);
        }
    }
}
