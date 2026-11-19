package com.tone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Order;
import com.tone.domain.entity.Product;
import com.tone.domain.entity.User;
import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.PickupType;
import com.tone.domain.enums.ProductType;
import com.tone.domain.enums.UserRole;
import com.tone.dto.CreatorDtos;
import com.tone.exception.ApiException;
import com.tone.repository.CreatorProfileRepository;
import com.tone.repository.OrderRepository;
import com.tone.repository.ProductRepository;
import com.tone.repository.UserRepository;
import com.tone.service.PayoutService;
import com.tone.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutAndPayoutConcurrencyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private CreatorProfileRepository creatorProfileRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PayoutService payoutService;
    @Autowired private RefundService refundService;

    @Test
    void checkoutIdempotencyKeyReturnsSameOrderAndBlocksDuplicatePending() throws Exception {
        String buyerToken = registerBuyer("idem-buyer-" + UUID.randomUUID() + "@tone.com");
        Product product = productRepository.findBySlugWithCreator("dumble-ods-fusion-lead").orElseThrow();

        mockMvc.perform(post("/api/cart/" + product.getId()).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        String idemKey = UUID.randomUUID().toString();
        MvcResult first = mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult replay = mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String replayId = objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText();
        assertThat(replayId).isEqualTo(firstId);

        // Adding same product again and checking out with a NEW key must fail because of PENDING_PAYMENT order
        mockMvc.perform(post("/api/cart/" + product.getId()).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isConflict());
    }

    @Test
    void creatorCannotBuyOwnProduct() throws Exception {
        String lucasToken = login("lucas@tone.com", "Creator@123");
        Product lucasProduct = productRepository.findBySlugWithCreator("ac30-worship-shimmer-pack").orElseThrow();

        mockMvc.perform(post("/api/cart/" + lucasProduct.getId()).header("Authorization", "Bearer " + lucasToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void concurrentPayoutsNeverOverdrawCreatorBalance() throws Exception {
        CreatorProfile rafael = creatorProfileRepository.findBySlugWithUser("riffforge-audio").orElseThrow();
        UUID rafaelUserId = rafael.getUser().getId();
        BigDecimal initialAvailable = payoutService.availableBalance(rafael.getId());
        assertThat(initialAvailable).isGreaterThan(new BigDecimal("60.00"));

        // Request 60% of available balance concurrently from 8 threads -> strictly 1 must succeed!
        BigDecimal withdrawAmount = initialAvailable.multiply(new BigDecimal("0.60"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
            start.await();
            try {
                payoutService.requestPayout(
                        rafaelUserId,
                        "concurrent-payout-" + i + "-" + UUID.randomUUID(),
                        new CreatorDtos.RequestPayoutRequest(withdrawAmount)
                );
                return true;
            } catch (ApiException ex) {
                return false;
            }
        })).toList();

        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get(15, TimeUnit.SECONDS)) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(payoutService.availableBalance(rafael.getId()))
                .isEqualByComparingTo(initialAvailable.subtract(withdrawAmount));
    }

    @Test
    void payoutWithSameIdempotencyKeyIsReplayedWithoutDoubleDeduction() {
        CreatorProfile camila = creatorProfileRepository.findBySlugWithUser("vintage-valve-co").orElseThrow();
        UUID camilaUserId = camila.getUser().getId();
        BigDecimal before = payoutService.availableBalance(camila.getId());
        assertThat(before).isGreaterThanOrEqualTo(new BigDecimal("20.00"));

        String key = "idem-payout-" + UUID.randomUUID();
        CreatorDtos.PayoutDto first = payoutService.requestPayout(
                camilaUserId, key, new CreatorDtos.RequestPayoutRequest(new BigDecimal("15.00")));
        CreatorDtos.PayoutDto second = payoutService.requestPayout(
                camilaUserId, key, new CreatorDtos.RequestPayoutRequest(new BigDecimal("15.00")));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(payoutService.availableBalance(camila.getId()))
                .isEqualByComparingTo(before.subtract(new BigDecimal("15.00")));
    }

    @Test
    void creditCardOrderReceivesExpirationTimestampAndAttachChargeNeverOverwrites() throws Exception {
        String buyerToken = registerBuyer("cc-buyer-" + UUID.randomUUID() + "@tone.com");
        Product product = productRepository.findBySlugWithCreator("jcm800-hot-rodded-crunch").orElseThrow();

        mockMvc.perform(post("/api/cart/" + product.getId()).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        MvcResult checkout = mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CREDIT_CARD"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode orderJson = objectMapper.readTree(checkout.getResponse().getContentAsString());
        assertThat(orderJson.get("pixExpiresAt").isNull()).isFalse();

        UUID orderId = UUID.fromString(orderJson.get("id").asText());
        Order order = orderRepository.findById(orderId).orElseThrow();
        String originalTxId = order.getGatewayTransactionId();
        boolean overwritten = order.attachChargeIfAbsent("TN_GW_OVERWRITE", null, null, null, java.time.Instant.now());
        assertThat(overwritten).isFalse();
        assertThat(order.getGatewayTransactionId()).isEqualTo(originalTxId);
    }

    @Test
    void concurrentPayoutAndRefundAreSerializedWithoutDoubleSpend() throws Exception {
        // Isolated fixture (fresh creator, product, buyer & paid order) to avoid cross-test interference
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = userRepository.findByEmailIgnoreCase("admin@tone.com").orElseThrow();

        User creatorUser = new User();
        creatorUser.setEmail("race-creator-" + suffix + "@tone.com");
        creatorUser.setPasswordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5E0g6E5vW6f2M7kQ7J3j8x8y3G2zW");
        creatorUser.setFullName("Race Creator " + suffix);
        creatorUser.setRole(UserRole.CREATOR);
        creatorUser = userRepository.save(creatorUser);

        CreatorProfile creator = new CreatorProfile();
        creator.setUser(creatorUser);
        creator.setDisplayName("Race Lab " + suffix);
        creator.setSlug("race-lab-" + suffix);
        creator.setBio("Isolated concurrency test creator");
        creator.setMainGear("Helix");
        creator.setPixKey("race-" + suffix + "@tone.com");
        creator.setStatus(CreatorStatus.APPROVED);
        creator.setReviewedBy(admin);
        creator.setReviewedAt(java.time.Instant.now());
        creator = creatorProfileRepository.save(creator);

        Product product = new Product();
        product.setCreator(creator);
        product.setTitle("Race Patch " + suffix);
        product.setSlug("race-patch-" + suffix);
        product.setDescription("Isolated concurrency test product");
        product.setPrice(new BigDecimal("100.00"));
        product.setProductType(ProductType.PATCH);
        product.setGenre("Rock");
        product.setTargetGear("Helix");
        product.setSourceGear("JCM800");
        product.setPickupType(PickupType.HUMBUCKER);
        product.setActive(true);
        product = productRepository.save(product);

        User buyer = new User();
        buyer.setEmail("race-buyer-" + suffix + "@tone.com");
        buyer.setPasswordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5E0g6E5vW6f2M7kQ7J3j8x8y3G2zW");
        buyer.setFullName("Race Buyer " + suffix);
        buyer.setRole(UserRole.BUYER);
        buyer = userRepository.save(buyer);

        Order paidOrder = Order.seedPaid(
                buyer,
                "TN-2026-R" + suffix.toUpperCase(),
                "TN_GW_RACE_" + suffix,
                List.of(product),
                null,
                new BigDecimal("15.00"),
                java.time.Instant.now().minusSeconds(60)
        );
        paidOrder = orderRepository.save(paidOrder);

        BigDecimal initialAvailable = payoutService.availableBalance(creator.getId());
        assertThat(initialAvailable).isEqualByComparingTo(new BigDecimal("85.00"));

        UUID creatorUserId = creatorUser.getId();
        UUID orderId = paidOrder.getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> payoutFuture = pool.submit(() -> {
            start.await();
            try {
                payoutService.requestPayout(
                        creatorUserId,
                        "race-payout-" + UUID.randomUUID(),
                        new CreatorDtos.RequestPayoutRequest(initialAvailable)
                );
                return true;
            } catch (ApiException ex) {
                return false;
            }
        });

        Future<Boolean> refundFuture = pool.submit(() -> {
            start.await();
            refundService.refundByAdmin(orderId, admin.getId(), "Chargeback concorrente");
            return true;
        });

        start.countDown();
        payoutFuture.get(15, TimeUnit.SECONDS);
        boolean refundSucceeded = refundFuture.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(refundSucceeded).isTrue();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(com.tone.domain.enums.OrderStatus.REFUNDED);
        assertThat(payoutService.availableBalance(creator.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String registerBuyer(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Buyer Idem", "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}
