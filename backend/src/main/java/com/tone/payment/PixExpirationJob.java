package com.tone.payment;

import com.tone.domain.enums.OrderStatus;
import com.tone.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * C4: Marks unpaid PIX orders past their expiration timestamp as EXPIRED.
 */
@Component
public class PixExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(PixExpirationJob.class);

    private final OrderRepository orderRepository;
    private final Clock clock;

    public PixExpirationJob(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.payment.expiration-job-delay-ms:60000}")
    @Transactional
    public void expireUnpaidPixOrders() {
        int expired = orderRepository.expirePendingPix(
                clock.instant(), OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
        if (expired > 0) {
            log.info("Pedidos PIX expirados automaticamente: {}", expired);
        }
    }
}
