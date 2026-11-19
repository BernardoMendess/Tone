package com.toneguitar.repository;

import com.toneguitar.domain.entity.BundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BundleItemRepository extends JpaRepository<BundleItem, UUID> {

    @Query("SELECT bi FROM BundleItem bi JOIN FETCH bi.includedProduct ip JOIN FETCH ip.creator WHERE bi.bundle.id = :bundleId")
    List<BundleItem> findByBundleIdWithProducts(UUID bundleId);

    @Query("SELECT bi.bundle.id FROM BundleItem bi WHERE bi.includedProduct.id = :productId")
    List<UUID> findBundleIdsContainingProduct(UUID productId);
}
