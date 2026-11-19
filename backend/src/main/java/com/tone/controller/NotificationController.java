package com.tone.controller;

import com.tone.dto.OrderDtos;
import com.tone.dto.PageResponse;
import com.tone.security.UserPrincipal;
import com.tone.service.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<OrderDtos.NotificationDto> listMyNotifications(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return notificationService.listUserNotifications(principal.getId(), pageable);
    }

    @PatchMapping("/{notificationId}/read")
    public Map<String, Boolean> markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        notificationService.markRead(principal.getId(), notificationId);
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    public Map<String, Boolean> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return Map.of("ok", true);
    }
}
