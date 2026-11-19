package com.tone.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.domain.entity.Order;
import com.tone.domain.entity.OrderStatusHistory;
import com.tone.domain.entity.PaymentEvent;
import com.tone.domain.enums.OrderStatus;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.payment.WebhookSigner;
import com.tone.repository.OrderRepository;
import com.tone.repository.OrderStatusHistoryRepository;
import com.tone.repository.PaymentEventRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * C4: Idempotent, anti-replay, pessimistic-locked payment webhook processor with strict state machine enforcement.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final WebhookSigner signer;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final OrderRepository orderRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderSettlementService settlementService;
    private final RefundService refundService;
    private final Clock clock;

    public PaymentWebhookService(
            WebhookSigner signer,
            ObjectMapper objectMapper,
            Validator validator,
            OrderRepository orderRepository,
            PaymentEventRepository paymentEventRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            OrderSettlementService settlementService,
            RefundService refundService,
            Clock clock
    ) {
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.orderRepository = orderRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.settlementService = settlementService;
        this.refundService = refundService;
        this.clock = clock;
    }

    @Transactional
    public void handleWebhook(String rawBody, long timestampEpochSec, String signature) {
        signer.verify(timestampEpochSec, rawBody, signature);
        OrderDtos.PaymentWebhookEvent evt = parseAndValidate(rawBody);

        if (paymentEventRepository.existsByGatewayEventId(evt.eventId())) {
            log.debug("Evento de webhook já processado (no-op idempotente): {}", evt.eventId());
            return;
        }

        Order order = orderRepository.findByGatewayTransactionIdForUpdate(evt.gatewayTransactionId())
                .orElseThrow(() -> ApiException.notFound("Transação não encontrada."));

        Instant now = clock.instant();
        String outcome = switch (evt.eventType()) {
            case PAYMENT_CONFIRMED -> handleConfirmed(order, evt, now);
            case PAYMENT_FAILED -> handleFailed(order, now);
            case PAYMENT_REFUNDED -> handleRefunded(order);
        };

        paymentEventRepository.save(PaymentEvent.record(
                evt.eventId(),
                order,
                evt.eventType(),
                evt.amount(),
                outcome,
                now
        ));
    }

    private String handleConfirmed(Order order, OrderDtos.PaymentWebhookEvent evt, Instant now) {
        if (order.getStatus() == OrderStatus.PAID) {
            return "IGNORED_ALREADY_PAID";
        }
        if (evt.amount().compareTo(order.getTotalAmount()) != 0) {
            log.error("Divergência de valor no pedido {}: esperado={}, recebido={}",
                    order.getOrderCode(), order.getTotalAmount(), evt.amount());
            return "AMOUNT_MISMATCH";
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.isPixExpired(now)) {
            log.warn("Pagamento tardio recebido para pedido {} no estado {}",
                    order.getOrderCode(), order.getStatus());
            return "LATE_PAYMENT_REFUND";
        }
        settlementService.settle(order, now, "WEBHOOK");
        return "APPLIED";
    }

    private String handleFailed(Order order, Instant now) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("Evento PAYMENT_FAILED ignorado para pedido {} no estado {}",
                    order.getOrderCode(), order.getStatus());
            return "IGNORED_INVALID_TRANSITION";
        }
        OrderStatus previous = order.getStatus();
        order.markFailed(now);
        orderRepository.save(order);
        statusHistoryRepository.save(
                OrderStatusHistory.of(order, previous, OrderStatus.FAILED, "WEBHOOK", "Falha no pagamento", now));
        return "APPLIED";
    }

    private String handleRefunded(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            return "IGNORED_INVALID_TRANSITION";
        }
        refundService.applyGatewayRefund(order, "Estorno/Chargeback confirmado pelo gateway");
        return "APPLIED";
    }

    private OrderDtos.PaymentWebhookEvent parseAndValidate(String rawBody) {
        try {
            OrderDtos.PaymentWebhookEvent evt = objectMapper.readValue(rawBody, OrderDtos.PaymentWebhookEvent.class);
            Set<ConstraintViolation<OrderDtos.PaymentWebhookEvent>> violations = validator.validate(evt);
            if (!violations.isEmpty()) {
                throw ApiException.badRequest("Payload de webhook incompleto ou inválido.");
            }
            return evt;
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("Payload JSON de webhook malformado.");
        }
    }
}
