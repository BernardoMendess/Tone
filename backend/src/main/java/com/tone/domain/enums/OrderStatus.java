package com.tone.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING_PAYMENT, EnumSet.of(PAID, FAILED, EXPIRED, CANCELLED),
            PAID, EnumSet.of(REFUNDED),
            FAILED, EnumSet.noneOf(OrderStatus.class),
            EXPIRED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class)
    );

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
