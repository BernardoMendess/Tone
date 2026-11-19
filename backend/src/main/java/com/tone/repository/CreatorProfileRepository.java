package com.tone.repository;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.enums.CreatorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {
    Optional<CreatorProfile> findByUserId(UUID userId);

    @Query("SELECT c FROM CreatorProfile c JOIN FETCH c.user WHERE c.slug = :slug")
    Optional<CreatorProfile> findBySlugWithUser(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT c FROM CreatorProfile c JOIN FETCH c.user WHERE c.status = :status ORDER BY c.createdAt ASC")
    List<CreatorProfile> findByStatusWithUser(CreatorStatus status);

    @Query("SELECT c FROM CreatorProfile c JOIN FETCH c.user ORDER BY c.createdAt DESC")
    List<CreatorProfile> findAllWithUser();

    long countByStatus(CreatorStatus status);
}
