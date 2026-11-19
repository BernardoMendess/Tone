package com.tone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.domain.entity.Order;
import com.tone.domain.entity.Product;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.PaymentEventType;
import com.tone.dto.OrderDtos;
import com.tone.payment.WebhookSigner;
import com.tone.repository.OrderRepository;
import com.tone.repository.ProductRepository;
import com.tone.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WebhookSigner webhookSigner;
    @Autowired private PaymentWebhookService webhookService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void webhookRejectsInvalidSignatureAndExpiredTimestamp() throws Exception {
        String body = objectMapper.writeValueAsString(new OrderDtos.PaymentWebhookEvent(
                "evt-" + UUID.randomUUID(), "TN_GW_UNKNOWN", PaymentEventType.PAYMENT_CONFIRMED, new BigDecimal("10.00")));
        long now = Instant.now().getEpochSecond();

        // Tampered signature
        mockMvc.perform(post("/api/webhooks/payments")
                        .header("X-Webhook-Timestamp", now)
                        .header("X-Webhook-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // Timestamp outside 5-minute replay window
        long expiredTs = now - 600;
        mockMvc.perform(post("/api/webhooks/payments")
                        .header("X-Webhook-Timestamp", expiredTs)
                        .header("X-Webhook-Signature", webhookSigner.sign(expiredTs, body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookIsIdempotentBlocksPaidToFailedAndChecksAmount() throws Exception {
        String buyerToken = registerBuyer("webhook-buyer-" + UUID.randomUUID() + "@tone.com");
        Product product = productRepository.findBySlugWithCreator("matchless-dc30-boutique-clean").orElseThrow();
        int initialSales = product.getSalesCount();

        mockMvc.perform(post("/api/cart/" + product.getId()).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        MvcResult checkoutRes = mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode orderJson = objectMapper.readTree(checkoutRes.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(orderJson.get("id").asText());
        String txId = orderJson.get("gatewayTransactionId").asText();
        BigDecimal total = orderJson.get("totalAmount").decimalValue();

        // 1. Amount mismatch MUST NOT settle order
        sendSignedWebhook("evt-mismatch-" + UUID.randomUUID(), txId, PaymentEventType.PAYMENT_CONFIRMED, new BigDecimal("0.01"));
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // 2. Concurrent deliveries of PAYMENT_CONFIRMED must settle strictly once
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Object>> futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
            latch.await();
            String evtId = "evt-concurrent-" + i + "-" + UUID.randomUUID();
            long ts = Instant.now().getEpochSecond();
            String body = objectMapper.writeValueAsString(
                    new OrderDtos.PaymentWebhookEvent(evtId, txId, PaymentEventType.PAYMENT_CONFIRMED, total));
            webhookService.handleWebhook(body, ts, webhookSigner.sign(ts, body));
            return null;
        })).toList();
        latch.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Order settled = orderRepository.findById(orderId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getSalesCount())
                .isEqualTo(initialSales + 1);

        // 3. Replaying same eventId is a clean no-op
        String dupId = "evt-dup-" + UUID.randomUUID();
        sendSignedWebhook(dupId, txId, PaymentEventType.PAYMENT_CONFIRMED, total);
        sendSignedWebhook(dupId, txId, PaymentEventType.PAYMENT_CONFIRMED, total);

        // 4. PAYMENT_FAILED after PAID must NEVER revert order to FAILED
        sendSignedWebhook("evt-late-fail-" + UUID.randomUUID(), txId, PaymentEventType.PAYMENT_FAILED, total);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private void sendSignedWebhook(String eventId, String txId, PaymentEventType type, BigDecimal amount) throws Exception {
        long ts = Instant.now().getEpochSecond();
        String body = objectMapper.writeValueAsString(new OrderDtos.PaymentWebhookEvent(eventId, txId, type, amount));
        mockMvc.perform(post("/api/webhooks/payments")
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", webhookSigner.sign(ts, body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    private String registerBuyer(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Buyer Webhook", "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}
