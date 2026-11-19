package com.tone.service;

import com.tone.domain.entity.Notification;
import com.tone.domain.entity.Order;
import com.tone.domain.entity.OrderStatusHistory;
import com.tone.domain.entity.PaymentSplit;
import com.tone.domain.enums.NotificationType;
import com.tone.domain.enums.OrderStatus;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.repository.*;
import com.tone.service.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A9: Order refund & chargeback handling — revokes digital library access (status != PAID),
 * marks splits REFUNDED and decrements sales counts atomically.
 */
@Service
public class RefundService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final ProductRepository productRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public RefundService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentSplitRepository paymentSplitRepository,
            CreatorProfileRepository creatorProfileRepository,
            ProductRepository productRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            NotificationRepository notificationRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.productRepository = productRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public OrderDtos.OrderDto refundByAdmin(UUID orderId, UUID adminUserId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        applyRefundLocked(order, "ADMIN:" + adminUserId, reason);
        return OrderMapper.toDto(
                order,
                orderItemRepository.findByOrderIdWithProduct(order.getId()),
                paymentSplitRepository.findByOrderIdWithCreator(order.getId())
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyGatewayRefund(Order lockedOrder, String reason) {
        applyRefundLocked(lockedOrder, "WEBHOOK", reason);
    }

    private void applyRefundLocked(Order order, String actor, String reason) {
        List<PaymentSplit> splits = paymentSplitRepository.findByOrderIdWithCreator(order.getId());
        List<UUID> creatorIds = splits.stream()
                .map(s -> s.getCreator().getId())
                .distinct()
                .sorted()
                .toList();
        if (!creatorIds.isEmpty()) {
            // Serialize refunds against concurrent PayoutService.requestPayout calls (Order -> CreatorProfile sorted lock order)
            creatorProfileRepository.findAllByIdInForUpdateOrderById(creatorIds);
        }

        Instant now = clock.instant();
        OrderStatus previous = order.getStatus();
        order.markRefunded(now);
        orderRepository.save(order);
        statusHistoryRepository.save(
                OrderStatusHistory.of(order, previous, OrderStatus.REFUNDED, actor, reason, now));

        for (PaymentSplit split : splits) {
            split.markRefunded();
        }
        paymentSplitRepository.saveAll(splits);

        List<UUID> productIds = orderItemRepository.findProductIdsByOrderId(order.getId());
        if (!productIds.isEmpty()) {
            productRepository.decrementSalesCount(productIds);
        }

        Notification buyerNotif = new Notification();
        buyerNotif.setUser(order.getBuyer());
        buyerNotif.setType(NotificationType.ORDER_REFUNDED);
        buyerNotif.setTitle("Pedido reembolsado (" + order.getOrderCode() + ")");
        buyerNotif.setMessage("O pedido " + order.getOrderCode() + " foi reembolsado. Motivo: " + reason);
        notificationRepository.save(buyerNotif);
    }
}
