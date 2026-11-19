package com.tone.service;

import com.tone.domain.entity.Notification;
import com.tone.domain.entity.Order;
import com.tone.domain.entity.PaymentSplit;
import com.tone.domain.enums.NotificationType;
import com.tone.domain.enums.OrderStatus;
import com.tone.dto.OrderDtos;
import com.tone.dto.PageResponse;
import com.tone.exception.ApiException;
import com.tone.repository.NotificationRepository;
import com.tone.repository.OrderItemRepository;
import com.tone.repository.UserRepository;
import com.tone.service.event.PatchVersionPublishedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.NotificationDto> listUserNotifications(UUID userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page, n -> new OrderDtos.NotificationDto(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getRelatedProductId(),
                n.isRead(),
                n.getCreatedAt()
        ));
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notif = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notificação não encontrada."));
        notif.markRead();
        notificationRepository.save(notif);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifySalesSettled(Order order, List<PaymentSplit> splits) {
        List<Notification> batch = new ArrayList<>(splits.size());
        for (PaymentSplit split : splits) {
            Notification notif = new Notification();
            notif.setUser(split.getCreator().getUser());
            notif.setType(NotificationType.SALE_COMPLETED);
            notif.setTitle("Nova venda confirmada (" + order.getOrderCode() + ")");
            notif.setMessage(String.format(
                    "Split liquidado: Bruto R$ %.2f | Taxa plataforma (%.0f%%) R$ %.2f | Líquido R$ %.2f.",
                    split.getGrossAmount(), split.getTakeRatePercent(),
                    split.getPlatformFeeAmount(), split.getCreatorNetAmount()
            ));
            batch.add(notif);
        }
        notificationRepository.saveAll(batch);
    }

    /**
     * A4: Asynchronous batched fan-out of patch update notifications after version commit.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPatchVersionPublished(PatchVersionPublishedEvent evt) {
        List<UUID> buyerIds = orderItemRepository.findBuyerIdsWhoPurchasedProduct(evt.productId(), OrderStatus.PAID);
        if (buyerIds.isEmpty()) {
            return;
        }
        String title = "Atualização disponível: " + evt.productTitle() + " (v" + evt.versionNumber() + ")";
        String message = "O criador " + evt.creatorDisplayName() + " lançou a versão " + evt.versionNumber()
                + ". Novidades: " + evt.changelog() + " — Já liberado na sua Biblioteca Digital.";

        for (int i = 0; i < buyerIds.size(); i += BATCH_SIZE) {
            List<UUID> chunk = buyerIds.subList(i, Math.min(i + BATCH_SIZE, buyerIds.size()));
            List<Notification> batch = new ArrayList<>(chunk.size());
            for (UUID buyerId : chunk) {
                Notification notif = new Notification();
                notif.setUser(userRepository.getReferenceById(buyerId));
                notif.setType(NotificationType.PATCH_UPDATE);
                notif.setTitle(title);
                notif.setMessage(message);
                notif.setRelatedProductId(evt.productId());
                batch.add(notif);
            }
            notificationRepository.saveAll(batch);
        }
    }
}
