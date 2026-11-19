package com.toneguitar.repository;

import com.toneguitar.domain.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    @Query("SELECT wi FROM WishlistItem wi JOIN FETCH wi.product p JOIN FETCH p.creator WHERE wi.user.id = :userId ORDER BY wi.createdAt DESC")
    List<WishlistItem> findByUserIdWithProducts(UUID userId);

    Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
