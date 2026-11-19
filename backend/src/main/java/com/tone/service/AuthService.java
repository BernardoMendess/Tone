package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.RefreshToken;
import com.tone.domain.entity.User;
import com.tone.domain.enums.UserRole;
import com.tone.dto.AuthDtos;
import com.tone.exception.ApiException;
import com.tone.repository.CreatorProfileRepository;
import com.tone.repository.NotificationRepository;
import com.tone.repository.RefreshTokenRepository;
import com.tone.repository.UserRepository;
import com.tone.security.JwtService;
import com.tone.storage.StorageSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // Valid BCrypt hash used to equalize timing when email does not exist
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5E0g6E5vW6f2M7kQ7J3j8x8y3G2zW";

    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final long refreshExpirationMs;

    public AuthService(
            UserRepository userRepository,
            CreatorProfileRepository creatorProfileRepository,
            NotificationRepository notificationRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
    ) {
        this.userRepository = userRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.notificationRepository = notificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public record IssuedSession(AuthDtos.AuthResponse response, String rawRefreshToken) {}

    @Transactional
    public IssuedSession register(AuthDtos.RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw ApiException.conflict("Já existe uma conta cadastrada com este e-mail.");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.BUYER);
        user = userRepository.save(user);

        return issueSession(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession login(AuthDtos.LoginRequest request) {
        Instant now = clock.instant();
        User user = userRepository.findByEmailIgnoreCaseForUpdate(request.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            throw ApiException.unauthorized("Credenciais inválidas.");
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Esta conta foi desativada.");
        }
        if (user.isLockedAt(now)) {
            throw ApiException.tooManyRequests("Conta temporariamente bloqueada por excesso de tentativas. Aguarde alguns minutos.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(now);
            userRepository.save(user);
            throw ApiException.unauthorized("Credenciais inválidas.");
        }
        user.resetFailedLogins(now);
        userRepository.save(user);
        return issueSession(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Sessão expirada. Faça login novamente.");
        }
        Instant now = clock.instant();
        String hash = hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> ApiException.unauthorized("Sessão inválida. Faça login novamente."));

        User user = stored.getUser();
        if (stored.isRevoked()) {
            // Replay of an already-rotated token: possible theft — invalidate all active sessions
            log.warn("Possível reutilização de refresh token revogado para usuário {}", user.getId());
            user.revokeAllTokens();
            userRepository.save(user);
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            throw ApiException.unauthorized("Sessão comprometida e revogada. Faça login novamente.");
        }
        if (stored.isExpiredAt(now) || !user.isEnabled()) {
            stored.revoke(now);
            refreshTokenRepository.save(stored);
            throw ApiException.unauthorized("Sessão expirada. Faça login novamente.");
        }

        // Rotate: revoke old refresh token and issue a fresh pair
        stored.revoke(now);
        refreshTokenRepository.save(stored);
        return issueSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        String hash = hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(rt -> {
            rt.revoke(clock.instant());
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void logoutAll(UUID userId) {
        Instant now = clock.instant();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
        user.revokeAllTokens();
        userRepository.save(user);
        refreshTokenRepository.revokeAllActiveByUserId(userId, now);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserInfo getCurrentUserInfo(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
        return toUserInfo(user);
    }

    public AuthDtos.UserInfo toUserInfo(User user) {
        Optional<CreatorProfile> creatorOpt = creatorProfileRepository.findByUserId(user.getId());
        long unread = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return new AuthDtos.UserInfo(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                creatorOpt.map(CreatorProfile::getId).orElse(null),
                creatorOpt.map(CreatorProfile::getSlug).orElse(null),
                creatorOpt.map(CreatorProfile::getStatus).orElse(null),
                unread
        );
    }

    private IssuedSession issueSession(User user) {
        Instant now = clock.instant();
        String accessToken = jwtService.generateToken(user);
        String rawRefresh = generateOpaqueToken();
        RefreshToken rt = RefreshToken.issue(user, hashRefreshToken(rawRefresh), now.plusMillis(refreshExpirationMs), now);
        refreshTokenRepository.save(rt);
        return new IssuedSession(new AuthDtos.AuthResponse(accessToken, toUserInfo(user)), rawRefresh);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashRefreshToken(String raw) {
        return StorageSupport.sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
