package com.toneguitar.domain.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "bundle_items", uniqueConstraints = @UniqueConstraint(columnNames = {"bundle_id", "included_product_id"}))
public class BundleItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    private Product bundle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "included_product_id", nullable = false)
    private Product includedProduct;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Product getBundle() { return bundle; }
    public void setBundle(Product bundle) { this.bundle = bundle; }
    public Product getIncludedProduct() { return includedProduct; }
    public void setIncludedProduct(Product includedProduct) { this.includedProduct = includedProduct; }
}
