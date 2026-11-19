package com.toneguitar.repository;

import com.toneguitar.domain.entity.Product;
import com.toneguitar.domain.enums.CreatorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c WHERE p.slug = :slug")
    Optional<Product> findBySlugWithCreator(String slug);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c WHERE p.id = :id")
    Optional<Product> findByIdWithCreator(UUID id);

    boolean existsBySlug(String slug);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c WHERE c.id = :creatorId ORDER BY p.createdAt DESC")
    List<Product> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c WHERE c.id = :creatorId AND p.active = true ORDER BY p.salesCount DESC, p.createdAt DESC")
    List<Product> findActiveByCreatorId(UUID creatorId);

    @Query("SELECT DISTINCT p.genre FROM Product p WHERE p.active = true AND p.creator.status = :status ORDER BY p.genre")
    List<String> findDistinctGenres(CreatorStatus status);

    @Query("SELECT DISTINCT p.targetGear FROM Product p WHERE p.active = true AND p.creator.status = :status ORDER BY p.targetGear")
    List<String> findDistinctTargetGears(CreatorStatus status);

    @Query("SELECT DISTINCT p.sourceGear FROM Product p WHERE p.active = true AND p.creator.status = :status ORDER BY p.sourceGear")
    List<String> findDistinctSourceGears(CreatorStatus status);

    long countByActiveTrue();
}
