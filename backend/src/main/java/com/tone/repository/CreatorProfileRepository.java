package com.tone.repository;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.enums.CreatorStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {

    Optional<CreatorProfile> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreatorProfile c WHERE c.user.id = :userId")
    Optional<CreatorProfile> findByUserIdForUpdate(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreatorProfile c WHERE c.id IN :ids ORDER BY c.id ASC")
    List<CreatorProfile> findAllByIdInForUpdateOrderById(Collection<UUID> ids);

    @Query("SELECT c FROM CreatorProfile c JOIN FETCH c.user WHERE c.id = :id")
    Optional<CreatorProfile> findByIdWithUser(UUID id);

    @Query("SELECT c FROM CreatorProfile c JOIN FETCH c.user WHERE c.slug = :slug")
    Optional<CreatorProfile> findBySlugWithUser(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT c FROM CreatorProfile c JOIN FETCH c.user WHERE c.status = :status ORDER BY c.createdAt ASC")
    List<CreatorProfile> findByStatusWithUser(CreatorStatus status);

    @Query(
            value = "SELECT c FROM CreatorProfile c JOIN FETCH c.user WHERE c.status = :status",
            countQuery = "SELECT COUNT(c) FROM CreatorProfile c WHERE c.status = :status"
    )
    Page<CreatorProfile> findByStatusWithUser(CreatorStatus status, Pageable pageable);

    @Query(
            value = "SELECT c FROM CreatorProfile c JOIN FETCH c.user",
            countQuery = "SELECT COUNT(c) FROM CreatorProfile c"
    )
    Page<CreatorProfile> findAllWithUser(Pageable pageable);

    long countByStatus(CreatorStatus status);
}
