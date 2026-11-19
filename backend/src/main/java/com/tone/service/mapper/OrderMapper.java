package com.tone.service.mapper;

import com.tone.domain.entity.Order;
import com.tone.domain.entity.OrderItem;
import com.tone.domain.entity.PaymentSplit;
import com.tone.dto.OrderDtos;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {}

    public static OrderDtos.OrderDto toDto(Order order, List<OrderItem> items, List<PaymentSplit> splits) {
        List<OrderDtos.OrderItemDto> itemDtos = items.stream()
                .map(oi -> new OrderDtos.OrderItemDto(
                        oi.getId(),
                        oi.getProduct().getId(),
                        oi.getProduct().getSlug(),
                        oi.getProductTitleSnapshot(),
                        oi.getCreator().getDisplayName(),
                        oi.getPurchasedVersion(),
                        oi.getProduct().getCurrentVersion(),
                        oi.getUnitPrice(),
                        oi.getPlatformFeeAmount(),
                        oi.getCreatorNetAmount()
                ))
                .toList();

        List<OrderDtos.PaymentSplitDto> splitDtos = splits.stream()
                .map(ps -> new OrderDtos.PaymentSplitDto(
                        ps.getId(),
                        ps.getCreator().getId(),
                        ps.getCreator().getDisplayName(),
                        ps.getGrossAmount(),
                        ps.getPlatformFeeAmount(),
                        ps.getCreatorNetAmount(),
                        ps.getTakeRatePercent(),
                        ps.getStatus(),
                        ps.getGatewaySplitReference(),
                        ps.getAvailableAt()
                ))
                .toList();

        return new OrderDtos.OrderDto(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getTotalAmount(),
                order.getPlatformFeeTotal(),
                order.getCreatorNetTotal(),
                order.getGatewayTransactionId(),
                order.getPixCopyPaste(),
                order.getPixExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                itemDtos,
                splitDtos
        );
    }
}
