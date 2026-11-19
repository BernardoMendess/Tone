package com.tone.validation;

import com.tone.config.SecretsStartupValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatorsAndSecretsTest {

    @Test
    void httpsUrlValidatorBlocksJavascriptAndHttpSchemes() {
        HttpsUrlValidator validator = new HttpsUrlValidator();
        assertThat(validator.isValid("https://tone.com.br/studio", null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("javascript:alert(1)", null)).isFalse();
        assertThat(validator.isValid("data:text/html,<script>alert(1)</script>", null)).isFalse();
        assertThat(validator.isValid("http://insecure.example.com", null)).isFalse();
    }

    @Test
    void pixKeyValidatorChecksAllSupportedKeyFormats() {
        PixKeyValidator validator = new PixKeyValidator();
        assertThat(validator.isValid("creator@tone.com.br", null)).isTrue();
        assertThat(validator.isValid("+5511999998888", null)).isTrue();
        assertThat(validator.isValid("550e8400-e29b-41d4-a716-446655440000", null)).isTrue();
        assertThat(validator.isValid("52998224725", null)).isTrue();   // valid CPF check digits
        assertThat(validator.isValid("11111111111", null)).isFalse();  // repeated digits
        assertThat(validator.isValid("52998224720", null)).isFalse();  // bad CPF check digit
        assertThat(validator.isValid("not-a-pix-key", null)).isFalse();
    }

    @Test
    void secretsValidatorFailsFastOnLeakedOrWeakSecrets() {
        SecretsStartupValidator leaked = new SecretsStartupValidator(
                "ToneMarketplaceSuperSecure256BitJwtSecretKey2026!",
                "prod-strong-webhook-secret-with-more-than-32-chars",
                "prod-strong-storage-secret-with-more-than-32-chars"
        );
        assertThatThrownBy(leaked::afterPropertiesSet).isInstanceOf(IllegalStateException.class);

        SecretsStartupValidator devSecret = new SecretsStartupValidator(
                "dev-only-jwt-secret-change-me-0123456789abcdef0123456789",
                "prod-strong-webhook-secret-with-more-than-32-chars",
                "prod-strong-storage-secret-with-more-than-32-chars"
        );
        assertThatThrownBy(devSecret::afterPropertiesSet).isInstanceOf(IllegalStateException.class);

        SecretsStartupValidator strong = new SecretsStartupValidator(
                "K9mP2vX7qL4nR8tW1zB6cF3hJ5sD0gY9aE2uI4oP6wQ8",
                "Z3xC5vB7nM9aS1dF3gH5jK7lQ9wE1rT3yU5iO7pA9sD1",
                "M4nB6vC8xZ0lK2jH4gF6dS8aP0oI2uY4tR6eW8qA0zX2"
        );
        strong.afterPropertiesSet();
    }
}
