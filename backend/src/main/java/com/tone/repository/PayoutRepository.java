package com.tone.repository;

import com.tone.domain.entity.Payout;
import com.tone.domain.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    List<Payout> findByCreatorIdOrderByRequestedAtDesc(UUID creatorId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payout p WHERE p.creator.id = :creatorId AND p.status IN :statuses")
    BigDecimal sumAmountByCreatorAndStatusIn(UUID creatorId, List<PayoutStatus> statuses);
}
