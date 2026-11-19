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

import java.util.List;
import java.util.UUID;

/**
 * RF05 + A2 (recalculo atômico de média) + M6 (bloqueio de autoavaliação).
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
        Product product = productRepository.findByIdWithCreator(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        if (product.isOwnedBy(userId)) {
            throw ApiException.forbidden("O criador não pode avaliar o próprio timbre.");
        }

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
        review = reviewRepository.saveAndFlush(review);

        // A2: Atomic DB-side rating recalculation (prevents lost updates under concurrent reviews)
        productRepository.recalculateRating(productId);

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
