package com.tone.repository;

import com.tone.domain.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
    boolean existsByGatewayEventId(String gatewayEventId);
}
