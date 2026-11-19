package com.tone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.domain.entity.Product;
import com.tone.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationAndIdorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;

    @Test
    void roleBasedRoutesBlockUnauthorizedProfiles() throws Exception {
        String buyerToken = registerBuyer("rbac-buyer-" + UUID.randomUUID() + "@tone.com");

        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/creator/dashboard").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/creator/products").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorCannotModifyOrVersionAnotherCreatorsProduct() throws Exception {
        String rafaelToken = login("rafael@tone.com", "Creator@123");
        Product lucasProduct = productRepository.findBySlugWithCreator("ac30-worship-shimmer-pack").orElseThrow();

        mockMvc.perform(patch("/api/creator/products/" + lucasProduct.getId())
                        .header("Authorization", "Bearer " + rafaelToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Hijacked Title"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/creator/products/" + lucasProduct.getId())
                        .header("Authorization", "Bearer " + rafaelToken))
                .andExpect(status().isNotFound());

        MockMultipartFile patch = new MockMultipartFile(
                "patchFile", "evil.hlx", "application/octet-stream", "{}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/creator/products/" + lucasProduct.getId() + "/versions")
                        .file(patch)
                        .param("versionNumber", "9.9.9")
                        .header("Authorization", "Bearer " + rafaelToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void buyerCannotAccessAnotherBuyersOrderAndRefundRevokesDownloadAccess() throws Exception {
        String buyerAToken = registerBuyer("buyer-a-" + UUID.randomUUID() + "@tone.com");
        String buyerBToken = registerBuyer("buyer-b-" + UUID.randomUUID() + "@tone.com");
        String adminToken = login("admin@tone.com", "Admin@123");
        Product product = productRepository.findBySlugWithCreator("jc120-lush-ambient-swells").orElseThrow();

        mockMvc.perform(post("/api/cart/" + product.getId()).header("Authorization", "Bearer " + buyerAToken))
                .andExpect(status().isOk());

        MvcResult checkout = mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerAToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(checkout.getResponse().getContentAsString()).get("id").asText();

        // IDOR check: Buyer B cannot inspect Buyer A's order
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + buyerBToken))
                .andExpect(status().isNotFound());

        // Settle Buyer A's order via sandbox -> Buyer A can presign download
        mockMvc.perform(post("/api/sandbox/orders/" + orderId + "/confirm-payment")
                        .header("Authorization", "Bearer " + buyerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(post("/api/library/downloads/" + product.getId() + "/presign")
                        .header("Authorization", "Bearer " + buyerAToken))
                .andExpect(status().isOk());

        // Admin refunds order -> Buyer A's download access is immediately revoked
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Solicitação de estorno"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/library/downloads/" + product.getId() + "/presign")
                        .header("Authorization", "Bearer " + buyerAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedProductIsHiddenFromPublicDetailAndAudioDemo() throws Exception {
        String camilaToken = login("camila@tone.com", "Creator@123");
        Product product = productRepository.findBySlugWithCreator("twin-reverb-1965-edge-breakup").orElseThrow();

        mockMvc.perform(delete("/api/creator/products/" + product.getId())
                        .header("Authorization", "Bearer " + camilaToken))
                .andExpect(status().isNoContent());

        // Public detail, reviews & demo audio return 404 for anonymous users
        mockMvc.perform(get("/api/products/" + product.getSlug()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/" + product.getId() + "/reviews"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audio/demo/" + product.getId()))
                .andExpect(status().isNotFound());

        // Owner creator can still read reviews of their own deactivated product
        mockMvc.perform(get("/api/products/" + product.getId() + "/reviews")
                        .header("Authorization", "Bearer " + camilaToken))
                .andExpect(status().isOk());

        // Re-activate so other tests aren't affected
        mockMvc.perform(patch("/api/creator/products/" + product.getId())
                        .header("Authorization", "Bearer " + camilaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private String registerBuyer(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "RBAC User", "email", email, "password", "StrongPass123!"))))
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
