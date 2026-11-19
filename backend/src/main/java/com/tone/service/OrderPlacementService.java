package com.tone.service;

import com.tone.domain.entity.*;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.ProductType;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.payment.PaymentGateway;
import com.tone.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A1: Short-transaction checkout placement & charge attachment, serialized per buyer via PESSIMISTIC_WRITE.
 */
@Service
public class OrderPlacementService {

    public record PlacedOrder(
            UUID orderId,
            String orderCode,
            BigDecimal totalAmount,
            boolean alreadyCharged,
            List<PaymentGateway.SplitInstruction> splitInstructions
    ) {}

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final CartItemRepository cartItemRepository;
    private final BundleItemRepository bundleItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PlatformSettingsService platformSettingsService;
    private final Clock clock;

    public OrderPlacementService(
            UserRepository userRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentSplitRepository paymentSplitRepository,
            CartItemRepository cartItemRepository,
            BundleItemRepository bundleItemRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            PlatformSettingsService platformSettingsService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.cartItemRepository = cartItemRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.platformSettingsService = platformSettingsService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PlacedOrder placeOrReplay(UUID buyerId, String idempotencyKey, OrderDtos.CheckoutRequest request) {
        // Serialize concurrent checkouts for the same buyer
        User buyer = userRepository.findByIdForUpdate(buyerId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        Optional<Order> existingOpt = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey);
        if (existingOpt.isPresent()) {
            Order existing = existingOpt.get();
            if (existing.hasChargeAttached() || existing.getStatus() != OrderStatus.PENDING_PAYMENT) {
                return new PlacedOrder(existing.getId(), existing.getOrderCode(), existing.getTotalAmount(), true, List.of());
            }
            List<PaymentGateway.SplitInstruction> instructions = toSplitInstructions(
                    paymentSplitRepository.findByOrderIdWithCreator(existing.getId()));
            return new PlacedOrder(existing.getId(), existing.getOrderCode(), existing.getTotalAmount(), false, instructions);
        }

        List<CartItem> cartItems = cartItemRepository.findByUserIdWithProducts(buyerId);
        if (cartItems.isEmpty()) {
            throw ApiException.badRequest("Seu carrinho está vazio.");
        }

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            if (!product.isPurchasable()) {
                throw ApiException.unprocessable("O timbre '" + product.getTitle() + "' não está mais disponível.");
            }
            if (product.isOwnedBy(buyerId)) {
                throw ApiException.unprocessable("Você não pode comprar o próprio timbre ('" + product.getTitle() + "').");
            }
            if (orderItemRepository.hasVerifiedPurchase(buyerId, product.getId(), OrderStatus.PAID)) {
                throw ApiException.conflict("Você já adquiriu o timbre '" + product.getTitle() + "'. Remova-o do carrinho.");
            }
            if (product.getProductType() == ProductType.BUNDLE
                    && bundleItemRepository.countNotOwnedItems(product.getId(), buyerId, OrderStatus.PAID) == 0) {
                throw ApiException.conflict("Você já possui todos os timbres incluídos em '" + product.getTitle() + "'.");
            }
            if (orderItemRepository.hasPendingOrder(buyerId, product.getId(), OrderStatus.PENDING_PAYMENT)) {
                throw ApiException.conflict("Já existe um pedido pendente de pagamento para '" + product.getTitle() + "'.");
            }
        }

        BigDecimal takeRate = platformSettingsService.getTakeRatePercent();
        Instant now = clock.instant();
        Order order = Order.place(buyer, idempotencyKey, request.paymentMethod(), cartItems, takeRate, now);
        order = orderRepository.save(order);
        statusHistoryRepository.save(
                OrderStatusHistory.of(order, null, OrderStatus.PENDING_PAYMENT, "BUYER:" + buyerId, "Pedido criado", now));

        return new PlacedOrder(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                false,
                toSplitInstructions(order.getSplits())
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void attachCharge(UUID orderId, PaymentGateway.GatewayChargeResult charge) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        Instant now = clock.instant();
        List<PaymentSplit> splits = paymentSplitRepository.findByOrderIdWithCreator(orderId);
        order.attachCharge(
                charge.gatewayTransactionId(),
                charge.pixCopyPaste(),
                charge.pixExpiresAt(),
                null,
                now
        );
        if (charge.creatorSplitReferences() != null) {
            for (PaymentSplit split : splits) {
                String ref = charge.creatorSplitReferences().get(split.getCreator().getId());
                if (ref != null) {
                    split.assignGatewayReference(ref);
                }
            }
            paymentSplitRepository.saveAll(splits);
        }
        orderRepository.save(order);
        cartItemRepository.deleteByUserId(order.getBuyer().getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelAfterGatewayFailure(UUID orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                Instant now = clock.instant();
                order.cancel(now);
                orderRepository.save(order);
                statusHistoryRepository.save(OrderStatusHistory.of(
                        order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, "SYSTEM",
                        "Falha ao criar cobrança no gateway", now));
            }
        });
    }

    private List<PaymentGateway.SplitInstruction> toSplitInstructions(List<PaymentSplit> splits) {
        return splits.stream()
                .map(s -> new PaymentGateway.SplitInstruction(
                        s.getCreator().getId(),
                        s.getCreator().getPixKey(),
                        s.getGrossAmount(),
                        s.getCreatorNetAmount(),
                        s.getPlatformFeeAmount(),
                        s.getTakeRatePercent()
                ))
                .toList();
    }
}
