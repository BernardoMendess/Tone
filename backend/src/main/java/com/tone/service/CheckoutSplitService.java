package com.tone.service;

import com.tone.domain.entity.*;
import com.tone.domain.enums.*;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.payment.PaymentGateway;
import com.tone.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.*;

/**
 * RF03 (Carrinho e Checkout Integrado com PIX prioritário) +
 * RF13 (Motor de Split de Pagamento automático entre Criador e Plataforma).
 */
@Service
public class CheckoutSplitService {

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PlatformSettingsService platformSettingsService;
    private final PaymentGateway paymentGateway;

    public CheckoutSplitService(
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentSplitRepository paymentSplitRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            PlatformSettingsService platformSettingsService,
            PaymentGateway paymentGateway
    ) {
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.platformSettingsService = platformSettingsService;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public OrderDtos.OrderDto checkout(UUID buyerId, OrderDtos.CheckoutRequest request) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdWithProducts(buyerId);
        if (cartItems.isEmpty()) {
            throw ApiException.badRequest("Seu carrinho está vazio.");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
        BigDecimal takeRate = platformSettingsService.getTakeRatePercent();

        Order order = new Order();
        order.setBuyer(buyer);
        order.setOrderCode(generateOrderCode());
        order.setPaymentMethod(request.paymentMethod());
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalPlatformFee = BigDecimal.ZERO;
        BigDecimal totalCreatorNet = BigDecimal.ZERO;

        // Calculate exact per-item split and aggregate per creator (multi-creator cart support)
        List<OrderItem> orderItems = new ArrayList<>();
        Map<UUID, CreatorSplitAccumulator> splitByCreator = new LinkedHashMap<>();

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            if (!product.isActive()) {
                throw ApiException.badRequest("O timbre '" + product.getTitle() + "' não está mais disponível.");
            }
            if (orderItemRepository.hasVerifiedPurchase(buyerId, product.getId(), OrderStatus.PAID)) {
                throw ApiException.conflict("Você já adquiriu o timbre '" + product.getTitle() + "'. Remova-o do carrinho.");
            }

            BigDecimal gross = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = gross.multiply(takeRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(fee).setScale(2, RoundingMode.HALF_UP);

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(product);
            oi.setCreator(product.getCreator());
            oi.setProductTitleSnapshot(product.getTitle());
            oi.setPurchasedVersion(product.getCurrentVersion());
            oi.setUnitPrice(gross);
            oi.setTakeRatePercent(takeRate);
            oi.setPlatformFeeAmount(fee);
            oi.setCreatorNetAmount(net);
            orderItems.add(oi);

            totalGross = totalGross.add(gross);
            totalPlatformFee = totalPlatformFee.add(fee);
            totalCreatorNet = totalCreatorNet.add(net);

            splitByCreator.computeIfAbsent(product.getCreator().getId(),
                    k -> new CreatorSplitAccumulator(product.getCreator())).add(gross, fee, net);
        }

        order.setTotalAmount(totalGross.setScale(2, RoundingMode.HALF_UP));
        order.setPlatformFeeTotal(totalPlatformFee.setScale(2, RoundingMode.HALF_UP));
        order.setCreatorNetTotal(totalCreatorNet.setScale(2, RoundingMode.HALF_UP));

        // Dispatch payment + split manifest to payment gateway (RF03 & RF13)
        List<PaymentGateway.SplitInstruction> splitInstructions = splitByCreator.values().stream()
                .map(acc -> new PaymentGateway.SplitInstruction(
                        acc.creator.getId(),
                        acc.creator.getPixKey(),
                        acc.gross,
                        acc.net,
                        acc.fee,
                        takeRate
                ))
                .toList();

        PaymentGateway.GatewayChargeResult gatewayResult = paymentGateway.createChargeWithSplit(
                order.getOrderCode(),
                order.getTotalAmount(),
                request.paymentMethod(),
                splitInstructions
        );

        order.setGatewayTransactionId(gatewayResult.gatewayTransactionId());
        order.setPixCopyPaste(gatewayResult.pixCopyPaste());
        order.setPixExpiresAt(gatewayResult.pixExpiresAt());
        order = orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
            orderItemRepository.save(oi);
        }

        List<PaymentSplit> savedSplits = new ArrayList<>();
        for (CreatorSplitAccumulator acc : splitByCreator.values()) {
            PaymentSplit split = new PaymentSplit();
            split.setOrder(order);
            split.setCreator(acc.creator);
            split.setGrossAmount(acc.gross);
            split.setPlatformFeeAmount(acc.fee);
            split.setCreatorNetAmount(acc.net);
            split.setTakeRatePercent(takeRate);
            split.setStatus(SplitStatus.SCHEDULED);
            split.setGatewaySplitReference(gatewayResult.creatorSplitReferences().get(acc.creator.getId()));
            savedSplits.add(paymentSplitRepository.save(split));
        }

        // Clear cart once order is generated
        cartItemRepository.deleteByUserId(buyerId);

        // Credit card payments confirm immediately in sandbox; PIX waits for real-time webhook confirmation
        if (request.paymentMethod() == PaymentMethod.CREDIT_CARD) {
            settleOrderPayment(order);
        }

        return toOrderDto(order);
    }

    /**
     * Webhook real-time endpoint handler (verifies HMAC signature and settles payment + splits).
     */
    @Transactional
    public OrderDtos.OrderDto handlePaymentWebhook(OrderDtos.PaymentWebhookRequest payload) {
        if (!paymentGateway.verifyWebhookSignature(payload.gatewayTransactionId(), payload.eventType(), payload.signature())) {
            throw ApiException.forbidden("Assinatura HMAC do webhook de pagamento inválida.");
        }
        Order order = orderRepository.findByGatewayTransactionId(payload.gatewayTransactionId())
                .orElseThrow(() -> ApiException.notFound("Transação não encontrada."));

        if ("PAYMENT_CONFIRMED".equalsIgnoreCase(payload.eventType())) {
            settleOrderPayment(order);
        } else if ("PAYMENT_FAILED".equalsIgnoreCase(payload.eventType())) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
        }
        return toOrderDto(order);
    }

    /**
     * Sandbox helper for the buyer to trigger a signed PIX confirmation webhook in development/demo environments.
     */
    @Transactional
    public OrderDtos.OrderDto simulatePixConfirmation(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        if (order.getStatus() == OrderStatus.PAID) {
            return toOrderDto(order);
        }
        String signature = paymentGateway.signWebhookPayload(order.getGatewayTransactionId(), "PAYMENT_CONFIRMED");
        return handlePaymentWebhook(new OrderDtos.PaymentWebhookRequest(
                order.getGatewayTransactionId(),
                "PAYMENT_CONFIRMED",
                signature
        ));
    }

    @Transactional
    public void settleOrderPayment(Order order) {
        if (order.getStatus() == OrderStatus.PAID) return;
        Instant now = Instant.now();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        orderRepository.save(order);

        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        for (OrderItem oi : items) {
            Product product = oi.getProduct();
            product.setSalesCount(product.getSalesCount() + 1);
            productRepository.save(product);
        }

        List<PaymentSplit> splits = paymentSplitRepository.findByOrderIdWithCreator(order.getId());
        for (PaymentSplit split : splits) {
            split.setStatus(SplitStatus.SETTLED);
            split.setSettledAt(now);
            paymentSplitRepository.save(split);

            // Notify creator of sale & settled split
            Notification notif = new Notification();
            notif.setUser(split.getCreator().getUser());
            notif.setType(NotificationType.SALE_COMPLETED);
            notif.setTitle("Nova venda confirmada (" + order.getOrderCode() + ")");
            notif.setMessage(String.format(
                    "Split liquidado: Bruto R$ %.2f | Taxa plataforma (%.0f%%) R$ %.2f | Líquido liberado R$ %.2f.",
                    split.getGrossAmount(), split.getTakeRatePercent(), split.getPlatformFeeAmount(), split.getCreatorNetAmount()
            ));
            notificationRepository.save(notif);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderDto> getUserOrders(UUID buyerId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(this::toOrderDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDto getOrder(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
        return toOrderDto(order);
    }

    public OrderDtos.OrderDto toOrderDto(Order order) {
        List<OrderDtos.OrderItemDto> itemDtos = orderItemRepository.findByOrderIdWithProduct(order.getId()).stream()
                .map(oi -> new OrderDtos.OrderItemDto(
                        oi.getId(),
                        oi.getProduct().getId(),
                        oi.getProduct().getSlug(),
                        oi.getProductTitleSnapshot(),
                        oi.getCreator().getDisplayName(),
                        oi.getPurchasedVersion(),
                        oi.getProduct().getCurrentVersion(),
                        oi.getUnitPrice(),
                        oi.getPlatformFeeAmount(),
                        oi.getCreatorNetAmount()
                ))
                .toList();

        List<OrderDtos.PaymentSplitDto> splitDtos = paymentSplitRepository.findByOrderIdWithCreator(order.getId()).stream()
                .map(ps -> new OrderDtos.PaymentSplitDto(
                        ps.getId(),
                        ps.getCreator().getId(),
                        ps.getCreator().getDisplayName(),
                        ps.getGrossAmount(),
                        ps.getPlatformFeeAmount(),
                        ps.getCreatorNetAmount(),
                        ps.getTakeRatePercent(),
                        ps.getStatus(),
                        ps.getGatewaySplitReference()
                ))
                .toList();

        return new OrderDtos.OrderDto(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getTotalAmount(),
                order.getPlatformFeeTotal(),
                order.getCreatorNetTotal(),
                order.getGatewayTransactionId(),
                order.getPixCopyPaste(),
                order.getPixExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                itemDtos,
                splitDtos
        );
    }

    private String generateOrderCode() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "TG-" + Year.now().getValue() + "-" + suffix;
    }

    private static class CreatorSplitAccumulator {
        final CreatorProfile creator;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;

        CreatorSplitAccumulator(CreatorProfile creator) {
            this.creator = creator;
        }

        void add(BigDecimal g, BigDecimal f, BigDecimal n) {
            this.gross = this.gross.add(g);
            this.fee = this.fee.add(f);
            this.net = this.net.add(n);
        }
    }
}
