package com.toneguitar.service;

import com.toneguitar.domain.entity.CreatorProfile;
import com.toneguitar.domain.entity.Notification;
import com.toneguitar.domain.entity.User;
import com.toneguitar.domain.enums.CreatorStatus;
import com.toneguitar.domain.enums.NotificationType;
import com.toneguitar.domain.enums.OrderStatus;
import com.toneguitar.domain.enums.UserRole;
import com.toneguitar.dto.AdminDtos;
import com.toneguitar.dto.CreatorDtos;
import com.toneguitar.exception.ApiException;
import com.toneguitar.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Módulo Administrativo: RF12 (Aprovação/Curadoria de Criadores) + RF13 (Configuração de Take Rate / Split).
 */
@Service
public class AdminCurationService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;
    private final PlatformSettingsService platformSettingsService;
    private final CreatorStudioService creatorStudioService;

    public AdminCurationService(
            CreatorProfileRepository creatorProfileRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            NotificationRepository notificationRepository,
            PlatformSettingsService platformSettingsService,
            CreatorStudioService creatorStudioService
    ) {
        this.creatorProfileRepository = creatorProfileRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.notificationRepository = notificationRepository;
        this.platformSettingsService = platformSettingsService;
        this.creatorStudioService = creatorStudioService;
    }

    @Transactional(readOnly = true)
    public AdminDtos.AdminOverviewDto getOverview() {
        List<CreatorDtos.CreatorProfileDto> pending = creatorProfileRepository
                .findByStatusWithUser(CreatorStatus.PENDING).stream()
                .map(creatorStudioService::toProfileDto)
                .toList();

        return new AdminDtos.AdminOverviewDto(
                userRepository.count(),
                creatorProfileRepository.countByStatus(CreatorStatus.APPROVED),
                creatorProfileRepository.countByStatus(CreatorStatus.PENDING),
                productRepository.countByActiveTrue(),
                orderRepository.countByStatus(OrderStatus.PAID),
                orderRepository.sumTotalAmountByStatus(OrderStatus.PAID).setScale(2, RoundingMode.HALF_UP),
                orderRepository.sumPlatformFeeByStatus(OrderStatus.PAID).setScale(2, RoundingMode.HALF_UP),
                platformSettingsService.getTakeRatePercent(),
                pending
        );
    }

    @Transactional(readOnly = true)
    public List<CreatorDtos.CreatorProfileDto> listCreators(CreatorStatus status) {
        List<CreatorProfile> list = (status != null)
                ? creatorProfileRepository.findByStatusWithUser(status)
                : creatorProfileRepository.findAllWithUser();
        return list.stream().map(creatorStudioService::toProfileDto).toList();
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto approveCreator(UUID adminUserId, UUID creatorProfileId) {
        CreatorProfile profile = creatorProfileRepository.findById(creatorProfileId)
                .orElseThrow(() -> ApiException.notFound("Perfil de criador não encontrado."));
        User admin = userRepository.getReferenceById(adminUserId);

        profile.setStatus(CreatorStatus.APPROVED);
        profile.setRejectionReason(null);
        profile.setReviewedBy(admin);
        profile.setReviewedAt(Instant.now());
        creatorProfileRepository.save(profile);

        User owner = profile.getUser();
        if (owner.getRole() == UserRole.BUYER) {
            owner.setRole(UserRole.CREATOR);
            userRepository.save(owner);
        }

        Notification notif = new Notification();
        notif.setUser(owner);
        notif.setType(NotificationType.CREATOR_APPROVED);
        notif.setTitle("Perfil de Criador Aprovado!");
        notif.setMessage("Parabéns! Sua conta '" + profile.getDisplayName()
                + "' foi aprovada pela curadoria. Você já pode publicar timbres e pacotes no Estúdio do Criador.");
        notificationRepository.save(notif);

        return creatorStudioService.toProfileDto(profile);
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto rejectCreator(UUID adminUserId, UUID creatorProfileId, String reason) {
        CreatorProfile profile = creatorProfileRepository.findById(creatorProfileId)
                .orElseThrow(() -> ApiException.notFound("Perfil de criador não encontrado."));
        User admin = userRepository.getReferenceById(adminUserId);

        profile.setStatus(CreatorStatus.REJECTED);
        profile.setRejectionReason(reason.trim());
        profile.setReviewedBy(admin);
        profile.setReviewedAt(Instant.now());
        creatorProfileRepository.save(profile);

        Notification notif = new Notification();
        notif.setUser(profile.getUser());
        notif.setType(NotificationType.CREATOR_REJECTED);
        notif.setTitle("Solicitação de Criador Revisada");
        notif.setMessage("Sua solicitação não foi aprovada neste momento. Motivo: " + reason.trim());
        notificationRepository.save(notif);

        return creatorStudioService.toProfileDto(profile);
    }

    @Transactional
    public BigDecimal updateTakeRate(BigDecimal newTakeRatePercent) {
        return platformSettingsService.updateTakeRatePercent(newTakeRatePercent);
    }
}
