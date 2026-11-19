package com.tone.repository;

import com.tone.domain.entity.PaymentSplit;
import com.tone.domain.enums.SplitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentSplitRepository extends JpaRepository<PaymentSplit, UUID> {

    @Query("SELECT ps FROM PaymentSplit ps JOIN FETCH ps.creator c JOIN FETCH c.user WHERE ps.order.id = :orderId")
    List<PaymentSplit> findByOrderIdWithCreator(UUID orderId);

    @Query("SELECT ps FROM PaymentSplit ps JOIN FETCH ps.creator WHERE ps.order.id IN :orderIds")
    List<PaymentSplit> findByOrderIdInWithCreator(Collection<UUID> orderIds);

    @Query("SELECT COALESCE(SUM(ps.grossAmount), 0) FROM PaymentSplit ps WHERE ps.creator.id = :creatorId AND ps.status = :status")
    BigDecimal sumGrossByCreatorAndStatus(UUID creatorId, SplitStatus status);

    @Query("SELECT COALESCE(SUM(ps.platformFeeAmount), 0) FROM PaymentSplit ps WHERE ps.creator.id = :creatorId AND ps.status = :status")
    BigDecimal sumPlatformFeeByCreatorAndStatus(UUID creatorId, SplitStatus status);

    @Query("SELECT COALESCE(SUM(ps.creatorNetAmount), 0) FROM PaymentSplit ps WHERE ps.creator.id = :creatorId AND ps.status = :status")
    BigDecimal sumNetByCreatorAndStatus(UUID creatorId, SplitStatus status);

    @Query("""
            SELECT COALESCE(SUM(ps.creatorNetAmount), 0) FROM PaymentSplit ps
            WHERE ps.creator.id = :creatorId AND ps.status = :status
              AND (ps.availableAt IS NULL OR ps.availableAt <= :now)
            """)
    BigDecimal sumAvailableNetByCreator(UUID creatorId, SplitStatus status, Instant now);
}
