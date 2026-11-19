package com.tone.controller;

import com.tone.dto.OrderDtos;
import com.tone.dto.PageResponse;
import com.tone.security.UserPrincipal;
import com.tone.service.CheckoutOrchestrator;
import com.tone.service.OrderQueryService;
import com.tone.service.PaymentWebhookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@Validated
public class CheckoutController {

    private final CheckoutOrchestrator checkoutOrchestrator;
    private final OrderQueryService orderQueryService;
    private final PaymentWebhookService paymentWebhookService;

    public CheckoutController(
            CheckoutOrchestrator checkoutOrchestrator,
            OrderQueryService orderQueryService,
            PaymentWebhookService paymentWebhookService
    ) {
        this.checkoutOrchestrator = checkoutOrchestrator;
        this.orderQueryService = orderQueryService;
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrderDto checkout(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 80) String idempotencyKey,
            @Valid @RequestBody OrderDtos.CheckoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return checkoutOrchestrator.checkout(principal.getId(), idempotencyKey, request);
    }

    @GetMapping("/orders")
    public PageResponse<OrderDtos.OrderDto> myOrders(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return orderQueryService.getUserOrders(principal.getId(), pageable);
    }

    @GetMapping("/orders/{orderId}")
    public OrderDtos.OrderDto getOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return orderQueryService.getOrder(principal.getId(), orderId);
    }

    @PostMapping("/webhooks/payments")
    public ResponseEntity<Void> paymentWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Webhook-Timestamp") long timestamp,
            @RequestHeader("X-Webhook-Signature") String signature
    ) {
        paymentWebhookService.handleWebhook(rawBody, timestamp, signature);
        return ResponseEntity.noContent().build();
    }
}
