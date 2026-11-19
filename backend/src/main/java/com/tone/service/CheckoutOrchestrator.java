package com.tone.service;

import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.payment.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * A1: Orchestrates checkout across two short DB transactions with external payment gateway I/O strictly outside transactions.
 */
@Service
public class CheckoutOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrchestrator.class);

    private final TransactionTemplate transactionTemplate;
    private final OrderPlacementService placementService;
    private final PaymentGateway paymentGateway;
    private final OrderQueryService orderQueryService;

    public CheckoutOrchestrator(
            TransactionTemplate transactionTemplate,
            OrderPlacementService placementService,
            PaymentGateway paymentGateway,
            OrderQueryService orderQueryService
    ) {
        this.transactionTemplate = transactionTemplate;
        this.placementService = placementService;
        this.paymentGateway = paymentGateway;
        this.orderQueryService = orderQueryService;
    }

    public OrderDtos.OrderDto checkout(UUID buyerId, String idempotencyKey, OrderDtos.CheckoutRequest request) {
        // Step 1: Lock buyer, validate cart, create PENDING_PAYMENT order or replay existing idempotent order
        OrderPlacementService.PlacedOrder placed = transactionTemplate.execute(
                status -> placementService.placeOrReplay(buyerId, idempotencyKey, request)
        );
        if (placed == null) {
            throw new IllegalStateException("Falha inesperada ao iniciar checkout.");
        }
        if (placed.alreadyCharged()) {
            return orderQueryService.getOrder(buyerId, placed.orderId());
        }

        // Step 2: External payment gateway call OUTSIDE database transaction
        PaymentGateway.GatewayChargeResult charge;
        try {
            charge = paymentGateway.createChargeWithSplit(
                    placed.orderCode(),
                    placed.totalAmount(),
                    request.paymentMethod(),
                    placed.splitInstructions()
            );
        } catch (RuntimeException ex) {
            log.error("Falha ao criar cobrança no gateway para pedido {}", placed.orderCode(), ex);
            transactionTemplate.executeWithoutResult(
                    status -> placementService.cancelAfterGatewayFailure(placed.orderId())
            );
            throw ApiException.badGateway("Não foi possível gerar a cobrança no gateway de pagamento. Tente novamente.");
        }

        // Step 3: Attach gateway charge & clear cart in a short transaction
        transactionTemplate.executeWithoutResult(
                status -> placementService.attachCharge(placed.orderId(), charge)
        );

        return orderQueryService.getOrder(buyerId, placed.orderId());
    }
}
