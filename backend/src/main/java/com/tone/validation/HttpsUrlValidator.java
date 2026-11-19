package com.tone.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {

    private Set<String> allowedHosts = Set.of();

    @Override
    public void initialize(HttpsUrl annotation) {
        this.allowedHosts = Set.of(annotation.allowedHosts());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            return false;
        }
        try {
            URI uri = new URI(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            if (allowedHosts.isEmpty()) {
                return true;
            }
            return allowedHosts.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
