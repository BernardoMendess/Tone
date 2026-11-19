package com.tone.repository;

import com.tone.domain.entity.Product;
import com.tone.domain.enums.CreatorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c JOIN FETCH c.user WHERE p.slug = :slug")
    Optional<Product> findBySlugWithCreator(String slug);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c JOIN FETCH c.user WHERE p.id = :id")
    Optional<Product> findByIdWithCreator(UUID id);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c JOIN FETCH c.user " +
           "WHERE p.slug = :slug AND p.active = true AND c.status = :status")
    Optional<Product> findPublicBySlug(String slug, CreatorStatus status);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c JOIN FETCH c.user " +
           "WHERE p.id = :id AND p.active = true AND c.status = :status")
    Optional<Product> findPublicById(UUID id, CreatorStatus status);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c JOIN FETCH c.user " +
           "WHERE p.id = :productId AND c.user.id = :userId")
    Optional<Product> findOwnedBy(UUID productId, UUID userId);

    boolean existsBySlug(String slug);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c WHERE c.id = :creatorId ORDER BY p.createdAt DESC")
    List<Product> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    @Query(
            value = "SELECT p FROM Product p JOIN FETCH p.creator c WHERE c.id = :creatorId",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.creator.id = :creatorId"
    )
    Page<Product> findByCreatorId(UUID creatorId, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.creator c WHERE c.id = :creatorId AND p.active = true ORDER BY p.salesCount DESC, p.createdAt DESC")
    List<Product> findActiveByCreatorId(UUID creatorId);

    @Query("SELECT DISTINCT p.genre FROM Product p WHERE p.active = true AND p.creator.status = :status ORDER BY p.genre")
    List<String> findDistinctGenres(CreatorStatus status);

    @Query("SELECT DISTINCT p.targetGear FROM Product p WHERE p.active = true AND p.creator.status = :status ORDER BY p.targetGear")
    List<String> findDistinctTargetGears(CreatorStatus status);

    @Query("SELECT DISTINCT p.sourceGear FROM Product p WHERE p.active = true AND p.creator.status = :status ORDER BY p.sourceGear")
    List<String> findDistinctSourceGears(CreatorStatus status);

    long countByActiveTrue();

    long countByCreatorIdAndActiveTrue(UUID creatorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.salesCount = p.salesCount + 1 WHERE p.id IN :ids")
    int incrementSalesCount(Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.salesCount = CASE WHEN p.salesCount > 0 THEN p.salesCount - 1 ELSE 0 END WHERE p.id IN :ids")
    int decrementSalesCount(Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.active = false WHERE p.creator.id = :creatorId AND p.active = true")
    int deactivateAllByCreatorId(UUID creatorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products SET
              average_rating = COALESCE((SELECT CAST(AVG(r.rating) AS NUMERIC(3,2)) FROM reviews r WHERE r.product_id = :productId), 0),
              review_count   = (SELECT COUNT(*) FROM reviews r WHERE r.product_id = :productId)
            WHERE id = :productId
            """, nativeQuery = true)
    int recalculateRating(UUID productId);
}
