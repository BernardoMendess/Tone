package com.tone.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fail-fast validation preventing startup in non-dev/test environments with missing,
 * weak, dev-only, or historically leaked secrets.
 */
@Component
@Profile("!dev & !test")
public class SecretsStartupValidator implements InitializingBean {

    private static final int MIN_SECRET_LENGTH = 32;

    private static final Set<String> KNOWN_LEAKED = Set.of(
            "ToneGuitarMarketplaceSuperSecure256BitJwtSecretKey2026!",
            "ToneGuitarPixWebhookHmacSecret2026!",
            "ToneGuitarPrivateVaultHmacSigningSecretKey2026!",
            "ToneMarketplaceSuperSecure256BitJwtSecretKey2026!",
            "TonePixWebhookHmacSecret2026!",
            "TonePrivateVaultHmacSigningSecretKey2026!"
    );

    private final Map<String, String> secrets;

    public SecretsStartupValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.payment.webhook-secret}") String webhookSecret,
            @Value("${app.storage.signing-secret}") String storageSecret
    ) {
        this.secrets = new LinkedHashMap<>();
        this.secrets.put("app.jwt.secret", jwtSecret);
        this.secrets.put("app.payment.webhook-secret", webhookSecret);
        this.secrets.put("app.storage.signing-secret", storageSecret);
    }

    @Override
    public void afterPropertiesSet() {
        secrets.forEach((name, value) -> {
            if (value == null || value.isBlank() || value.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "Segredo ausente ou muito curto (mínimo " + MIN_SECRET_LENGTH + " caracteres): " + name);
            }
            if (KNOWN_LEAKED.contains(value)) {
                throw new IllegalStateException("Segredo vazado detectado em produção: " + name);
            }
            if (value.startsWith("dev-only-") || value.startsWith("test-only-")) {
                throw new IllegalStateException("Segredo de desenvolvimento/teste usado fora de dev/test: " + name);
            }
        });
    }
}
