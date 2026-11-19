package com.tone.payment.sandbox;

import com.tone.dto.OrderDtos;
import com.tone.security.UserPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sandbox/orders")
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "app.payment.sandbox-enabled", havingValue = "true")
public class PaymentSandboxController {

    private final PaymentSandboxService sandboxService;

    public PaymentSandboxController(PaymentSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @PostMapping("/{orderId}/confirm-payment")
    public OrderDtos.OrderDto confirmPayment(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return sandboxService.simulatePaymentConfirmation(principal.getId(), orderId);
    }
}
