package com.tone.payment;

import com.tone.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentGateway {

    GatewayChargeResult createChargeWithSplit(
            String orderCode,
            BigDecimal totalAmount,
            PaymentMethod method,
            List<SplitInstruction> splitInstructions
    );

    record SplitInstruction(
            UUID creatorId,
            String creatorPixKey,
            BigDecimal grossAmount,
            BigDecimal creatorNetAmount,
            BigDecimal platformFeeAmount,
            BigDecimal takeRatePercent
    ) {}

    record GatewayChargeResult(
            String gatewayTransactionId,
            String pixCopyPaste,
            Instant pixExpiresAt,
            Map<UUID, String> creatorSplitReferences
    ) {}
}
