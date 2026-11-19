package com.tone.repository;

import com.tone.domain.entity.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {
    List<ProductVersion> findByProductIdOrderByCreatedAtDesc(UUID productId);
    Optional<ProductVersion> findFirstByProductIdOrderByCreatedAtDesc(UUID productId);
    Optional<ProductVersion> findByIdAndProductId(UUID id, UUID productId);
}
