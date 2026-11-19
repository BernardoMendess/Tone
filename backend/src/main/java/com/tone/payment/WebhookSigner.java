package com.tone.payment;

import com.tone.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/**
 * C4: HMAC-SHA256 webhook signature over (timestamp + "." + rawBody) with 5-minute replay tolerance.
 */
@Component
public class WebhookSigner {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final byte[] secret;
    private final Clock clock;

    public WebhookSigner(
            @Value("${app.payment.webhook-secret}") String secret,
            Clock clock
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String sign(long timestampEpochSec, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] payload = (timestampEpochSec + "." + rawBody).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar webhook de pagamento.", e);
        }
    }

    public void verify(long timestampEpochSec, String rawBody, String providedSignature) {
        long nowSec = clock.instant().getEpochSecond();
        if (Math.abs(nowSec - timestampEpochSec) > TOLERANCE.toSeconds()) {
            throw ApiException.unauthorized("Webhook fora da janela de tolerância anti-replay.");
        }
        if (providedSignature == null || providedSignature.isBlank() || rawBody == null) {
            throw ApiException.unauthorized("Assinatura do webhook ausente ou inválida.");
        }
        String expected = sign(timestampEpochSec, rawBody);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Assinatura HMAC do webhook inválida.");
        }
    }
}
