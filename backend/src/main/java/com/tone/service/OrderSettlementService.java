package com.tone.service;

import com.tone.domain.entity.Order;
import com.tone.domain.entity.OrderStatusHistory;
import com.tone.domain.entity.PaymentSplit;
import com.tone.domain.enums.OrderStatus;
import com.tone.repository.OrderItemRepository;
import com.tone.repository.OrderRepository;
import com.tone.repository.OrderStatusHistoryRepository;
import com.tone.repository.PaymentSplitRepository;
import com.tone.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class OrderSettlementService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final NotificationService notificationService;
    private final Period holdPeriod;

    public OrderSettlementService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            PaymentSplitRepository paymentSplitRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            NotificationService notificationService,
            @Value("${app.payout.hold-period:P7D}") String holdPeriodIso
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.notificationService = notificationService;
        this.holdPeriod = Period.parse(holdPeriodIso);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settle(Order order, Instant now, String actor) {
        OrderStatus previous = order.getStatus();
        order.markPaid(now);
        orderRepository.save(order);
        statusHistoryRepository.save(
                OrderStatusHistory.of(order, previous, OrderStatus.PAID, actor, "Pagamento confirmado", now));

        // A2: Atomic counter update (prevents lost updates under concurrent settlements)
        List<UUID> productIds = orderItemRepository.findProductIdsByOrderId(order.getId());
        if (!productIds.isEmpty()) {
            productRepository.incrementSalesCount(productIds);
        }

        // A9: Settle splits with hold period before payout eligibility
        Instant availableAt = now.atZone(ZoneOffset.UTC).plus(holdPeriod).toInstant();
        List<PaymentSplit> splits = paymentSplitRepository.findByOrderIdWithCreator(order.getId());
        for (PaymentSplit split : splits) {
            split.markSettled(now, availableAt);
        }
        paymentSplitRepository.saveAll(splits);
        notificationService.notifySalesSettled(order, splits);
    }
}
