package com.tone.security;

import com.tone.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String ISSUER = "tone-api";
    public static final String AUDIENCE = "tone-web";

    private final SecretKey signingKey;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:900000}") long expirationMs,
            Clock clock
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    /**
     * B6 + M4: Minimal JWT containing only subject (userId) and tokenVersion (ver),
     * with strict issuer/audience and short TTL. No PII in claims.
     */
    public String generateToken(User user) {
        Instant now = clock.instant();
        Instant expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("ver", user.getTokenVersion())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public Integer extractTokenVersion(Claims claims) {
        return claims.get("ver", Integer.class);
    }
}
