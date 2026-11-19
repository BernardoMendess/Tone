package com.tone.controller;

import com.tone.dto.OrderDtos;
import com.tone.security.UserPrincipal;
import com.tone.service.CheckoutSplitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CheckoutController {

    private final CheckoutSplitService checkoutSplitService;

    public CheckoutController(CheckoutSplitService checkoutSplitService) {
        this.checkoutSplitService = checkoutSplitService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrderDto checkout(
            @Valid @RequestBody OrderDtos.CheckoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return checkoutSplitService.checkout(principal.getId(), request);
    }

    @GetMapping("/orders")
    public List<OrderDtos.OrderDto> myOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return checkoutSplitService.getUserOrders(principal.getId());
    }

    @GetMapping("/orders/{orderId}")
    public OrderDtos.OrderDto getOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return checkoutSplitService.getOrder(principal.getId(), orderId);
    }

    @PostMapping("/orders/{orderId}/simulate-pix-payment")
    public OrderDtos.OrderDto simulatePixPayment(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return checkoutSplitService.simulatePixConfirmation(principal.getId(), orderId);
    }

    @PostMapping("/webhooks/payments")
    public OrderDtos.OrderDto paymentWebhook(@RequestBody OrderDtos.PaymentWebhookRequest payload) {
        return checkoutSplitService.handlePaymentWebhook(payload);
    }
}
