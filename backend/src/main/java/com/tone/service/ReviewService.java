package com.tone.service;

import com.tone.domain.entity.Product;
import com.tone.domain.entity.Review;
import com.tone.domain.entity.User;
import com.tone.domain.enums.OrderStatus;
import com.tone.dto.CatalogDtos;
import com.tone.exception.ApiException;
import com.tone.repository.OrderItemRepository;
import com.tone.repository.ProductRepository;
import com.tone.repository.ReviewRepository;
import com.tone.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * RF05 - Sistema de Avaliações: apenas usuários com compra verificada podem avaliar (1 a 5 estrelas + comentário).
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ReviewDto> listReviews(UUID productId) {
        return reviewRepository.findByProductIdWithUser(productId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CatalogDtos.ReviewDto submitReview(UUID userId, UUID productId, CatalogDtos.CreateReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        if (!orderItemRepository.hasVerifiedPurchase(userId, productId, OrderStatus.PAID)) {
            throw ApiException.forbidden("Apenas compradores com compra verificada deste timbre podem publicar uma avaliação.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        Review review = reviewRepository.findByProductIdAndUserId(productId, userId)
                .orElseGet(() -> {
                    Review r = new Review();
                    r.setProduct(product);
                    r.setUser(user);
                    r.setVerifiedPurchase(true);
                    return r;
                });

        review.setRating(request.rating());
        review.setComment(request.comment() != null ? request.comment().trim() : "");
        review = reviewRepository.save(review);

        Double avg = reviewRepository.calculateAverageRating(productId);
        int count = reviewRepository.countByProductId(productId);
        product.setAverageRating(avg != null
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        product.setReviewCount(count);
        productRepository.save(product);

        return toDto(review);
    }

    private CatalogDtos.ReviewDto toDto(Review r) {
        return new CatalogDtos.ReviewDto(
                r.getId(),
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getRating(),
                r.getComment(),
                r.isVerifiedPurchase(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
