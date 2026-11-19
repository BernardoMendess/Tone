package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Notification;
import com.tone.domain.entity.User;
import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.NotificationType;
import com.tone.domain.enums.OrderStatus;
import com.tone.dto.AdminDtos;
import com.tone.dto.CreatorDtos;
import com.tone.dto.PageResponse;
import com.tone.exception.ApiException;
import com.tone.repository.*;
import com.tone.service.mapper.CreatorProfileMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RF12 + RF13 + M3 (transições de estado rigorosas na curadoria e suspensão com desativação de produtos).
 */
@Service
public class AdminCurationService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;
    private final PlatformSettingsService platformSettingsService;
    private final Clock clock;

    public AdminCurationService(
            CreatorProfileRepository creatorProfileRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            NotificationRepository notificationRepository,
            PlatformSettingsService platformSettingsService,
            Clock clock
    ) {
        this.creatorProfileRepository = creatorProfileRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.notificationRepository = notificationRepository;
        this.platformSettingsService = platformSettingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDtos.AdminOverviewDto getOverview() {
        List<CreatorDtos.CreatorProfileDto> pending = creatorProfileRepository
                .findByStatusWithUser(CreatorStatus.PENDING).stream()
                .map(CreatorProfileMapper::toDto)
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
    public PageResponse<CreatorDtos.CreatorProfileDto> listCreators(CreatorStatus status, Pageable pageable) {
        Pageable effective = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<CreatorProfile> page = (status != null)
                ? creatorProfileRepository.findByStatusWithUser(status, effective)
                : creatorProfileRepository.findAllWithUser(effective);
        return PageResponse.of(page, CreatorProfileMapper::toDto);
    }

    @Transactional
    @CacheEvict(cacheNames = "catalog-filters", allEntries = true)
    public CreatorDtos.CreatorProfileDto approveCreator(UUID adminUserId, UUID creatorProfileId) {
        CreatorProfile profile = creatorProfileRepository.findByIdWithUser(creatorProfileId)
                .orElseThrow(() -> ApiException.notFound("Perfil de criador não encontrado."));
        User admin = userRepository.getReferenceById(adminUserId);
        Instant now = clock.instant();

        profile.approve(admin, now);
        creatorProfileRepository.save(profile);

        User owner = profile.getUser();
        owner.promoteToCreator();
        userRepository.save(owner);

        Notification notif = new Notification();
        notif.setUser(owner);
        notif.setType(NotificationType.CREATOR_APPROVED);
        notif.setTitle("Perfil de Criador Aprovado!");
        notif.setMessage("Parabéns! Sua conta '" + profile.getDisplayName()
                + "' foi aprovada pela curadoria. Você já pode publicar timbres e pacotes no Estúdio do Criador.");
        notificationRepository.save(notif);

        return CreatorProfileMapper.toDto(profile);
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto rejectCreator(UUID adminUserId, UUID creatorProfileId, String reason) {
        CreatorProfile profile = creatorProfileRepository.findByIdWithUser(creatorProfileId)
                .orElseThrow(() -> ApiException.notFound("Perfil de criador não encontrado."));
        User admin = userRepository.getReferenceById(adminUserId);

        profile.reject(admin, reason.trim(), clock.instant());
        creatorProfileRepository.save(profile);

        Notification notif = new Notification();
        notif.setUser(profile.getUser());
        notif.setType(NotificationType.CREATOR_REJECTED);
        notif.setTitle("Solicitação de Criador Revisada");
        notif.setMessage("Sua solicitação não foi aprovada neste momento. Motivo: " + reason.trim());
        notificationRepository.save(notif);

        return CreatorProfileMapper.toDto(profile);
    }

    /**
     * M3: Suspends an approved creator, deactivates all their listings, and revokes CREATOR role.
     */
    @Transactional
    @CacheEvict(cacheNames = "catalog-filters", allEntries = true)
    public CreatorDtos.CreatorProfileDto suspendCreator(UUID adminUserId, UUID creatorProfileId, String reason) {
        CreatorProfile profile = creatorProfileRepository.findByIdWithUser(creatorProfileId)
                .orElseThrow(() -> ApiException.notFound("Perfil de criador não encontrado."));
        User admin = userRepository.getReferenceById(adminUserId);

        profile.suspend(admin, reason.trim(), clock.instant());
        creatorProfileRepository.save(profile);

        productRepository.deactivateAllByCreatorId(profile.getId());

        User owner = profile.getUser();
        owner.demoteToBuyer();
        userRepository.save(owner);

        Notification notif = new Notification();
        notif.setUser(owner);
        notif.setType(NotificationType.CREATOR_SUSPENDED);
        notif.setTitle("Perfil de Criador Suspenso");
        notif.setMessage("Sua conta de criador foi suspensa e seus anúncios foram pausados. Motivo: " + reason.trim());
        notificationRepository.save(notif);

        return CreatorProfileMapper.toDto(profile);
    }

    @Transactional
    public BigDecimal updateTakeRate(BigDecimal newTakeRatePercent) {
        return platformSettingsService.updateTakeRatePercent(newTakeRatePercent);
    }
}
