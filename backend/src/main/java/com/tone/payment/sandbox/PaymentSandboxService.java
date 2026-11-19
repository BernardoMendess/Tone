package com.tone.payment.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.domain.entity.Order;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.PaymentEventType;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.payment.WebhookSigner;
import com.tone.repository.OrderRepository;
import com.tone.service.OrderQueryService;
import com.tone.service.PaymentWebhookService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * C1: Isolated sandbox payment simulator — ONLY loaded in dev/test profiles AND when explicitly enabled.
 */
@Service
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "app.payment.sandbox-enabled", havingValue = "true")
public class PaymentSandboxService {

    private final OrderRepository orderRepository;
    private final PaymentWebhookService webhookService;
    private final WebhookSigner webhookSigner;
    private final ObjectMapper objectMapper;
    private final OrderQueryService orderQueryService;
    private final Clock clock;

    public PaymentSandboxService(
            OrderRepository orderRepository,
            PaymentWebhookService webhookService,
            WebhookSigner webhookSigner,
            ObjectMapper objectMapper,
            OrderQueryService orderQueryService,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.webhookService = webhookService;
        this.webhookSigner = webhookSigner;
        this.objectMapper = objectMapper;
        this.orderQueryService = orderQueryService;
        this.clock = clock;
    }

    public OrderDtos.OrderDto simulatePaymentConfirmation(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (order.getStatus() == OrderStatus.PAID) {
            return orderQueryService.getOrder(buyerId, orderId);
        }
        long timestamp = clock.instant().getEpochSecond();
        OrderDtos.PaymentWebhookEvent event = new OrderDtos.PaymentWebhookEvent(
                "SANDBOX-" + UUID.randomUUID(),
                order.getGatewayTransactionId(),
                PaymentEventType.PAYMENT_CONFIRMED,
                order.getTotalAmount()
        );
        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar evento sandbox.", ex);
        }
        String signature = webhookSigner.sign(timestamp, rawBody);
        webhookService.handleWebhook(rawBody, timestamp, signature);
        return orderQueryService.getOrder(buyerId, orderId);
    }
}
