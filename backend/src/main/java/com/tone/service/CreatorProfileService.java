package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.User;
import com.tone.domain.enums.CreatorStatus;
import com.tone.dto.CreatorDtos;
import com.tone.exception.ApiException;
import com.tone.repository.CreatorProfileRepository;
import com.tone.repository.UserRepository;
import com.tone.service.mapper.CreatorProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreatorProfileService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CreatorProfileService(
            CreatorProfileRepository creatorProfileRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.creatorProfileRepository = creatorProfileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto applyAsCreator(UUID userId, CreatorDtos.CreatorApplicationRequest req) {
        Optional<CreatorProfile> existingOpt = creatorProfileRepository.findByUserId(userId);
        if (existingOpt.isPresent()) {
            CreatorProfile existing = existingOpt.get();
            if (existing.getStatus() == CreatorStatus.APPROVED) {
                throw ApiException.conflict("Seu perfil de criador já está aprovado.");
            }
            if (existing.getStatus() == CreatorStatus.PENDING) {
                throw ApiException.conflict("Sua solicitação já está em análise pela curadoria.");
            }
            if (existing.getStatus() == CreatorStatus.SUSPENDED) {
                throw ApiException.forbidden("Seu perfil de criador está suspenso. Entre em contato com o suporte.");
            }
            existing.setDisplayName(req.displayName().trim());
            existing.setBio(req.bio().trim());
            existing.setMainGear(req.mainGear().trim());
            existing.setPortfolioDemoUrl(blankToNull(req.portfolioDemoUrl()));
            existing.setYoutubeUrl(blankToNull(req.youtubeUrl()));
            existing.setInstagramUrl(blankToNull(req.instagramUrl()));
            existing.setWebsiteUrl(blankToNull(req.websiteUrl()));
            existing.setPixKey(req.pixKey().trim());
            existing.resubmit(clock.instant());
            return CreatorProfileMapper.toDto(creatorProfileRepository.save(existing));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        CreatorProfile profile = new CreatorProfile();
        profile.setUser(user);
        profile.setDisplayName(req.displayName().trim());
        profile.setSlug(SlugUtils.generateUnique(req.displayName(), creatorProfileRepository::existsBySlug));
        profile.setBio(req.bio().trim());
        profile.setMainGear(req.mainGear().trim());
        profile.setPortfolioDemoUrl(blankToNull(req.portfolioDemoUrl()));
        profile.setYoutubeUrl(blankToNull(req.youtubeUrl()));
        profile.setInstagramUrl(blankToNull(req.instagramUrl()));
        profile.setWebsiteUrl(blankToNull(req.websiteUrl()));
        profile.setPixKey(req.pixKey().trim());
        profile.setStatus(CreatorStatus.PENDING);
        return CreatorProfileMapper.toDto(creatorProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public CreatorDtos.CreatorProfileDto getMyCreatorProfile(UUID userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Você ainda não possui perfil de criador."));
        return CreatorProfileMapper.toDto(profile);
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto updateMyProfile(UUID userId, CreatorDtos.UpdateCreatorProfileRequest req) {
        CreatorProfile profile = requireApprovedCreator(userId);
        profile.setDisplayName(req.displayName().trim());
        if (req.slug() != null && !req.slug().isBlank() && !req.slug().equals(profile.getSlug())) {
            String trimmedSlug = req.slug().trim();
            if (creatorProfileRepository.existsBySlug(trimmedSlug)) {
                throw ApiException.conflict("Este slug já está em uso por outro criador.");
            }
            profile.setSlug(trimmedSlug);
        }
        profile.setBio(req.bio());
        profile.setAvatarUrl(blankToNull(req.avatarUrl()));
        profile.setMainGear(req.mainGear());
        profile.setYoutubeUrl(blankToNull(req.youtubeUrl()));
        profile.setInstagramUrl(blankToNull(req.instagramUrl()));
        profile.setWebsiteUrl(blankToNull(req.websiteUrl()));
        profile.setPixKey(req.pixKey().trim());
        return CreatorProfileMapper.toDto(creatorProfileRepository.save(profile));
    }

    public CreatorProfile requireApprovedCreator(UUID userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("Você precisa se candidatar e ser aprovado como criador."));
        if (profile.getStatus() != CreatorStatus.APPROVED) {
            throw ApiException.forbidden("Seu perfil de criador não está ativo/aprovado pela curadoria.");
        }
        return profile;
    }

    private static String blankToNull(String val) {
        return (val == null || val.isBlank()) ? null : val.trim();
    }
}
