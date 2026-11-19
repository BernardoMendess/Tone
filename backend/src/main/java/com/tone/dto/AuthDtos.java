package com.tone.dto;

import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record UserInfo(
            UUID id,
            String email,
            String fullName,
            UserRole role,
            UUID creatorProfileId,
            String creatorSlug,
            CreatorStatus creatorStatus,
            long unreadNotifications
    ) {}

    public record AuthResponse(
            String token,
            UserInfo user
    ) {}
}
