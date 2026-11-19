package com.toneguitar.service;

import com.toneguitar.domain.entity.CreatorProfile;
import com.toneguitar.domain.entity.User;
import com.toneguitar.domain.enums.UserRole;
import com.toneguitar.dto.AuthDtos;
import com.toneguitar.exception.ApiException;
import com.toneguitar.repository.CreatorProfileRepository;
import com.toneguitar.repository.NotificationRepository;
import com.toneguitar.repository.UserRepository;
import com.toneguitar.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            CreatorProfileRepository creatorProfileRepository,
            NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("Já existe uma conta cadastrada com este e-mail.");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.BUYER);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthDtos.AuthResponse(token, toUserInfo(user));
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Credenciais inválidas."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Credenciais inválidas.");
        }
        String token = jwtService.generateToken(user);
        return new AuthDtos.AuthResponse(token, toUserInfo(user));
    }

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
}
