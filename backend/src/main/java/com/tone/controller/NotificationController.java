package com.tone.controller;

import com.tone.domain.entity.Notification;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.repository.NotificationRepository;
import com.tone.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<OrderDtos.NotificationDto> listMyNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(principal.getId()).stream()
                .map(n -> new OrderDtos.NotificationDto(
                        n.getId(),
                        n.getType(),
                        n.getTitle(),
                        n.getMessage(),
                        n.getRelatedProductId(),
                        n.isRead(),
                        n.getCreatedAt()
                ))
                .toList();
    }

    @PatchMapping("/{notificationId}/read")
    @Transactional
    public Map<String, Boolean> markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Notification notif = notificationRepository.findByIdAndUserId(notificationId, principal.getId())
                .orElseThrow(() -> ApiException.notFound("Notificação não encontrada."));
        notif.setRead(true);
        notificationRepository.save(notif);
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Boolean> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationRepository.markAllAsRead(principal.getId());
        return Map.of("ok", true);
    }
}
