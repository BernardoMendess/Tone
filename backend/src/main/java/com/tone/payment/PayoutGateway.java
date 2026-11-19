package com.tone.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PayoutGateway {

    TransferResult executePixTransfer(UUID payoutId, String pixKey, BigDecimal amount);

    record TransferResult(boolean accepted, String externalReference) {}
}
