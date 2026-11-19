package com.tone.service;

import com.tone.domain.entity.CartItem;
import com.tone.domain.entity.Product;
import com.tone.domain.entity.User;
import com.tone.domain.entity.WishlistItem;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.repository.*;
import com.tone.service.mapper.ProductCardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartWishlistService {

    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final BundleItemRepository bundleItemRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductCardMapper productCardMapper;
    private final PlatformSettingsService platformSettingsService;

    public CartWishlistService(
            CartItemRepository cartItemRepository,
            WishlistItemRepository wishlistItemRepository,
            ProductRepository productRepository,
            BundleItemRepository bundleItemRepository,
            UserRepository userRepository,
            OrderItemRepository orderItemRepository,
            ProductCardMapper productCardMapper,
            PlatformSettingsService platformSettingsService
    ) {
        this.cartItemRepository = cartItemRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.productCardMapper = productCardMapper;
        this.platformSettingsService = platformSettingsService;
    }

    @Transactional(readOnly = true)
    public OrderDtos.CartDto getCart(UUID userId) {
        List<CartItem> items = cartItemRepository.findByUserIdWithProducts(userId);
        List<Product> products = items.stream().map(CartItem::getProduct).toList();
        Map<UUID, CatalogDtos.ProductCardDto> cardsById = productCardMapper.toCards(products).stream()
                .collect(Collectors.toMap(CatalogDtos.ProductCardDto::id, Function.identity()));

        List<OrderDtos.CartItemDto> itemDtos = items.stream()
                .map(ci -> new OrderDtos.CartItemDto(
                        ci.getProduct().getId(),
                        cardsById.get(ci.getProduct().getId()),
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
        if (!product.isPurchasable()) {
            throw ApiException.unprocessable("Este timbre não está disponível para compra no momento.");
        }
        if (product.isOwnedBy(userId)) {
            throw ApiException.unprocessable("Você não pode adicionar seu próprio timbre ao carrinho.");
        }
        if (orderItemRepository.hasVerifiedPurchase(userId, productId, OrderStatus.PAID)) {
            throw ApiException.conflict("Você já possui este timbre na sua Biblioteca Digital.");
        }
        if (product.getProductType() == ProductType.BUNDLE
                && bundleItemRepository.countNotOwnedItems(product.getId(), userId, OrderStatus.PAID) == 0) {
            throw ApiException.conflict("Você já possui todos os timbres incluídos neste pacote.");
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

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductCardDto> getWishlist(UUID userId) {
        List<Product> products = wishlistItemRepository.findByUserIdWithProducts(userId).stream()
                .map(WishlistItem::getProduct)
                .filter(Product::isPurchasable)
                .toList();
        return productCardMapper.toCards(products);
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
        if (!product.isPurchasable()) {
            throw ApiException.notFound("Timbre não encontrado.");
        }
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
