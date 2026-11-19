package com.tone.service.mapper;

import com.tone.domain.entity.ProductVersion;
import com.tone.dto.CatalogDtos;

public final class ProductVersionMapper {

    private ProductVersionMapper() {}

    public static CatalogDtos.ProductVersionDto toDto(ProductVersion v) {
        return new CatalogDtos.ProductVersionDto(
                v.getId(),
                v.getVersionNumber(),
                v.getChangelog(),
                v.getOriginalFilename(),
                v.getFileSizeBytes(),
                v.getCreatedAt()
        );
    }
}
