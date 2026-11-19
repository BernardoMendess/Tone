package com.tone.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SimulatedPayoutGateway implements PayoutGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPayoutGateway.class);

    @Override
    public TransferResult executePixTransfer(UUID payoutId, String pixKey, BigDecimal amount) {
        String ref = "PIX_OUT_" + payoutId.toString().replace("-", "").substring(0, 12).toUpperCase();
        log.info("Saque PIX despachado ao gateway: payoutId={}, amount={}, ref={}", payoutId, amount, ref);
        return new TransferResult(true, ref);
    }
}
