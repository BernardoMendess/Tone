package com.tone.controller;

import com.tone.dto.CatalogDtos;
import com.tone.dto.OrderDtos;
import com.tone.security.UserPrincipal;
import com.tone.service.CartWishlistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CartWishlistController {

    private final CartWishlistService cartWishlistService;

    public CartWishlistController(CartWishlistService cartWishlistService) {
        this.cartWishlistService = cartWishlistService;
    }

    @GetMapping("/cart")
    public OrderDtos.CartDto getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return cartWishlistService.getCart(principal.getId());
    }

    @PostMapping("/cart/{productId}")
    public OrderDtos.CartDto addToCart(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return cartWishlistService.addToCart(principal.getId(), productId);
    }

    @DeleteMapping("/cart/{productId}")
    public OrderDtos.CartDto removeFromCart(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return cartWishlistService.removeFromCart(principal.getId(), productId);
    }

    @DeleteMapping("/cart")
    public OrderDtos.CartDto clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        return cartWishlistService.clearCart(principal.getId());
    }

    @GetMapping("/wishlist")
    public List<CatalogDtos.ProductCardDto> getWishlist(@AuthenticationPrincipal UserPrincipal principal) {
        return cartWishlistService.getWishlist(principal.getId());
    }

    @PostMapping("/wishlist/{productId}/toggle")
    public Map<String, Boolean> toggleWishlist(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean wishlisted = cartWishlistService.toggleWishlist(principal.getId(), productId);
        return Map.of("wishlisted", wishlisted);
    }

    @DeleteMapping("/wishlist/{productId}")
    public void removeFromWishlist(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        cartWishlistService.removeFromWishlist(principal.getId(), productId);
    }
}
