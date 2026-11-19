package com.tone.domain;

import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineTest {

    @Test
    void orderStatusAllowsOnlyValidTransitions() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.FAILED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.REFUNDED)).isFalse();

        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FAILED)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.EXPIRED)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isFalse();

        for (OrderStatus terminal : new OrderStatus[]{
                OrderStatus.FAILED, OrderStatus.EXPIRED, OrderStatus.CANCELLED, OrderStatus.REFUNDED}) {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void creatorStatusEnforcesCurationStateMachine() {
        assertThat(CreatorStatus.PENDING.canTransitionTo(CreatorStatus.APPROVED)).isTrue();
        assertThat(CreatorStatus.PENDING.canTransitionTo(CreatorStatus.REJECTED)).isTrue();
        assertThat(CreatorStatus.PENDING.canTransitionTo(CreatorStatus.SUSPENDED)).isFalse();

        assertThat(CreatorStatus.APPROVED.canTransitionTo(CreatorStatus.SUSPENDED)).isTrue();
        assertThat(CreatorStatus.APPROVED.canTransitionTo(CreatorStatus.REJECTED)).isFalse();
        assertThat(CreatorStatus.APPROVED.canTransitionTo(CreatorStatus.APPROVED)).isFalse();

        assertThat(CreatorStatus.REJECTED.canTransitionTo(CreatorStatus.PENDING)).isTrue();
        assertThat(CreatorStatus.REJECTED.canTransitionTo(CreatorStatus.APPROVED)).isFalse();

        assertThat(CreatorStatus.SUSPENDED.canTransitionTo(CreatorStatus.APPROVED)).isTrue();
        assertThat(CreatorStatus.SUSPENDED.canTransitionTo(CreatorStatus.PENDING)).isFalse();
    }
}
