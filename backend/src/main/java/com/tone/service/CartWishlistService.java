package com.tone.service;

import com.tone.domain.entity.CartItem;
import com.tone.domain.entity.Product;
import com.tone.domain.entity.User;
import com.tone.domain.entity.WishlistItem;
import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.OrderStatus;
import com.tone.dto.CatalogDtos;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class CartWishlistService {

    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final CatalogService catalogService;
    private final PlatformSettingsService platformSettingsService;

    public CartWishlistService(
            CartItemRepository cartItemRepository,
            WishlistItemRepository wishlistItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderItemRepository orderItemRepository,
            CatalogService catalogService,
            PlatformSettingsService platformSettingsService
    ) {
        this.cartItemRepository = cartItemRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.catalogService = catalogService;
        this.platformSettingsService = platformSettingsService;
    }

    @Transactional(readOnly = true)
    public OrderDtos.CartDto getCart(UUID userId) {
        List<CartItem> items = cartItemRepository.findByUserIdWithProducts(userId);
        List<OrderDtos.CartItemDto> itemDtos = items.stream()
                .map(ci -> new OrderDtos.CartItemDto(
                        ci.getProduct().getId(),
                        catalogService.toProductCard(ci.getProduct()),
                        ci.getAddedAt()
                ))
                .toList();
        BigDecimal subtotal = items.stream()
                .map(ci -> ci.getProduct().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new OrderDtos.CartDto(
                itemDtos,
                itemDtos.size(),
                subtotal,
                platformSettingsService.getTakeRatePercent()
        );
    }

    @Transactional
    public OrderDtos.CartDto addToCart(UUID userId, UUID productId) {
        Product product = productRepository.findByIdWithCreator(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        if (!product.isActive() || product.getCreator().getStatus() != CreatorStatus.APPROVED) {
            throw ApiException.badRequest("Este timbre não está disponível para compra no momento.");
        }
        if (orderItemRepository.hasVerifiedPurchase(userId, productId, OrderStatus.PAID)) {
            throw ApiException.conflict("Você já possui este timbre na sua Biblioteca Digital.");
        }
        if (cartItemRepository.findByUserIdAndProductId(userId, productId).isEmpty()) {
            User user = userRepository.getReferenceById(userId);
            CartItem item = new CartItem();
            item.setUser(user);
            item.setProduct(product);
            cartItemRepository.save(item);
        }
        return getCart(userId);
    }

    @Transactional
    public OrderDtos.CartDto removeFromCart(UUID userId, UUID productId) {
        cartItemRepository.deleteByUserIdAndProductId(userId, productId);
        return getCart(userId);
    }

    @Transactional
    public OrderDtos.CartDto clearCart(UUID userId) {
        cartItemRepository.deleteByUserId(userId);
        return getCart(userId);
    }

    // --- RF06: Wishlist ---

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductCardDto> getWishlist(UUID userId) {
        return wishlistItemRepository.findByUserIdWithProducts(userId).stream()
                .map(wi -> catalogService.toProductCard(wi.getProduct()))
                .toList();
    }

    @Transactional
    public boolean toggleWishlist(UUID userId, UUID productId) {
        var existing = wishlistItemRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            wishlistItemRepository.delete(existing.get());
            return false;
        }
        Product product = productRepository.findByIdWithCreator(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        WishlistItem item = new WishlistItem();
        item.setUser(userRepository.getReferenceById(userId));
        item.setProduct(product);
        wishlistItemRepository.save(item);
        return true;
    }

    @Transactional
    public void removeFromWishlist(UUID userId, UUID productId) {
        wishlistItemRepository.deleteByUserIdAndProductId(userId, productId);
    }
}
