package com.tone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A7: Rate limiting on sensitive authentication, checkout, payout and webhook endpoints.
 * For multi-instance production deployments, replace the in-memory map with Redis/Bucket4j.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(HttpMethod method, String path, int limit, Duration window) {}
    private record Window(long startEpochSec, AtomicInteger count) {}

    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/api/auth/login", 10, Duration.ofMinutes(1)),
            new Rule(HttpMethod.POST, "/api/auth/register", 5, Duration.ofMinutes(10)),
            new Rule(HttpMethod.POST, "/api/auth/refresh", 30, Duration.ofMinutes(1)),
            new Rule(HttpMethod.POST, "/api/checkout", 10, Duration.ofMinutes(1)),
            new Rule(HttpMethod.POST, "/api/creator/payouts", 5, Duration.ofMinutes(10)),
            new Rule(HttpMethod.POST, "/api/webhooks/payments", 120, Duration.ofMinutes(1))
    );

    private final boolean enabled;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.security.rate-limit-enabled:true}") boolean enabled,
            Clock clock
    ) {
        this.enabled = enabled;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(req, res);
            return;
        }
        String uri = req.getRequestURI();
        for (Rule rule : RULES) {
            if (rule.method().matches(req.getMethod()) && uri.equals(rule.path())) {
                String key = rule.path() + "|" + clientKey(req);
                long now = clock.instant().getEpochSecond();
                long windowSec = rule.window().toSeconds();
                Window w = windows.compute(key, (k, cur) ->
                        (cur == null || now - cur.startEpochSec() >= windowSec)
                                ? new Window(now, new AtomicInteger(0))
                                : cur);
                if (w.count().incrementAndGet() > rule.limit()) {
                    long retryAfter = Math.max(1, windowSec - (now - w.startEpochSec()));
                    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    res.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    res.setCharacterEncoding("UTF-8");
                    res.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\","
                            + "\"detail\":\"Muitas tentativas. Aguarde e tente novamente.\","
                            + "\"message\":\"Muitas tentativas. Aguarde e tente novamente.\"}");
                    return;
                }
                break;
            }
        }
        chain.doFilter(req, res);
    }

    private String clientKey(HttpServletRequest req) {
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpiredWindows() {
        long now = clock.instant().getEpochSecond();
        windows.entrySet().removeIf(e -> now - e.getValue().startEpochSec() > 3600);
    }
}
