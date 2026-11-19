package com.tone.controller;

import com.tone.dto.AuthDtos;
import com.tone.security.UserPrincipal;
import com.tone.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "tone_refresh";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final long refreshExpirationMs;

    public AuthController(
            AuthService authService,
            @Value("${app.jwt.cookie-secure:true}") boolean cookieSecure,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
    ) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthService.IssuedSession session = authService.register(request);
        setRefreshCookie(response, session.rawRefreshToken());
        return session.response();
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.IssuedSession session = authService.login(request);
        setRefreshCookie(response, session.rawRefreshToken());
        return session.response();
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        AuthService.IssuedSession session = authService.refresh(refreshToken);
        setRefreshCookie(response, session.rawRefreshToken());
        return session.response();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse response
    ) {
        authService.logoutAll(principal.getId());
        clearRefreshCookie(response);
    }

    @GetMapping("/me")
    public AuthDtos.UserInfo me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.getCurrentUserInfo(principal.getId());
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
