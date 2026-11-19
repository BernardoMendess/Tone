package com.toneguitar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toneguitar.audio.GuitarDemoSynthesizer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GuitarDemoSynthesizer synthesizer;

    @Test
    void fullMarketplaceLifecycleCoversAllCoreRequirements() throws Exception {
        // 1. RF01: Cross-filtered catalog search
        MvcResult catalogRes = mockMvc.perform(get("/api/products")
                        .param("genre", "Worship")
                        .param("targetGear", "Line 6 Helix")
                        .param("pickupType", "SINGLE_COIL"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode products = objectMapper.readTree(catalogRes.getResponse().getContentAsString());
        assertThat(products.isArray()).isTrue();
        assertThat(products.size()).isGreaterThanOrEqualTo(1);
        String ac30Id = products.get(0).get("id").asText();

        // 2. RF02 + RNF03 + RNF04: Demo audio stream with normalization & anti-ripping headers
        mockMvc.perform(get("/api/audio/demo/" + ac30Id))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Audio-LUFS"))
                .andExpect(header().string("X-Audio-Protection", "COMPRESSED_PREVIEW_NO_RAW_IR"))
                .andExpect(header().string("Accept-Ranges", "bytes"));

        // 3. Register a fresh buyer
        String buyerToken = registerAndGetToken("Teste Comprador", "newbuyer@toneguitar.com", "Secret123!");

        // 4. RNF02 & RF05: Unpurchased buyer CANNOT presign download or submit a review
        mockMvc.perform(post("/api/library/downloads/" + ac30Id + "/presign")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/products/" + ac30Id + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5, "comment", "Sem compra"))))
                .andExpect(status().isForbidden());

        // 5. RF06: Wishlist toggle
        mockMvc.perform(post("/api/wishlist/" + ac30Id + "/toggle")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(true));

        // 6. RF03 & RF13: Add to cart -> PIX Checkout -> Split calculation -> Webhook confirmation
        mockMvc.perform(post("/api/cart/" + ac30Id)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        MvcResult checkoutRes = mockMvc.perform(post("/api/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.pixCopyPaste").isNotEmpty())
                .andExpect(jsonPath("$.splits[0].takeRatePercent").value(15.0))
                .andReturn();

        JsonNode orderJson = objectMapper.readTree(checkoutRes.getResponse().getContentAsString());
        String orderId = orderJson.get("id").asText();
        assertThat(orderJson.get("pixCopyPaste").asText()).startsWith("000201");

        // Confirm PIX payment via signed webhook simulator
        mockMvc.perform(post("/api/orders/" + orderId + "/simulate-pix-payment")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.splits[0].status").value("SETTLED"));

        // 7. RF04 & RNF02: Buyer can now generate a valid Presigned URL and download binary, tampered sig rejected!
        MvcResult presignRes = mockMvc.perform(post("/api/library/downloads/" + ac30Id + "/presign")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty())
                .andReturn();
        String fullDownloadUrl = objectMapper.readTree(presignRes.getResponse().getContentAsString())
                .get("downloadUrl").asText();
        String relativePath = fullDownloadUrl.substring(fullDownloadUrl.indexOf("/api/downloads/presigned"));

        mockMvc.perform(get(relativePath))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));

        // Tampered signature must be rejected
        mockMvc.perform(get(relativePath + "TAMPERED"))
                .andExpect(status().isForbidden());

        // 8. RF05: Verified buyer CAN submit a review
        mockMvc.perform(post("/api/products/" + ac30Id + "/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5, "comment", "Timbre excelente no Helix!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verifiedPurchase").value(true));

        // 9. RF12 + RF07 + RF09: Creator curation approval, product creation & version update notification
        String applicantToken = registerAndGetToken("Novo Criador", "newcreator@toneguitar.com", "Secret123!");
        MvcResult applyRes = mockMvc.perform(post("/api/creator/apply")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "Studio Alpha Tones",
                                "bio", "Criador de timbres valvulados de estúdio.",
                                "mainGear", "Line 6 Helix",
                                "pixKey", "alpha@pix.com"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String creatorProfileId = objectMapper.readTree(applyRes.getResponse().getContentAsString()).get("id").asText();

        String adminToken = loginAndGetToken("admin@toneguitar.com", "Admin@123");
        mockMvc.perform(post("/api/admin/creators/" + creatorProfileId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Approved creator publishes a new product (RF07)
        byte[] wavDemo = synthesizer.synthesizeDemoWav(GuitarDemoSynthesizer.ToneProfile.BOUTIQUE_CLEAN);
        MockMultipartFile patchPart = new MockMultipartFile(
                "patchFile", "Alpha_Clean.hlx", "application/octet-stream",
                "{\"patch\":\"alpha\"}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile demoPart = new MockMultipartFile(
                "demoAudioFile", "alpha_demo.wav", "audio/wav", wavDemo);

        MvcResult createdProdRes = mockMvc.perform(multipart("/api/creator/products")
                        .file(patchPart)
                        .file(demoPart)
                        .param("title", "Alpha Boutique Clean")
                        .param("description", "Preset limpo boutique para Helix")
                        .param("price", "39.90")
                        .param("productType", "PATCH")
                        .param("genre", "Blues")
                        .param("targetGear", "Line 6 Helix")
                        .param("sourceGear", "Fender Deluxe Reverb")
                        .param("pickupType", "SINGLE_COIL")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andReturn();
        String newProductId = objectMapper.readTree(createdProdRes.getResponse().getContentAsString()).get("id").asText();

        // Creator uploads v1.1.0 update (RF09)
        MockMultipartFile patchV2 = new MockMultipartFile(
                "patchFile", "Alpha_Clean_v1.1.hlx", "application/octet-stream",
                "{\"patch\":\"alpha-v1.1\"}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/creator/products/" + newProductId + "/versions")
                        .file(patchV2)
                        .param("versionNumber", "1.1.0")
                        .param("changelog", "Ajuste de headroom")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value("1.1.0"));

        // 10. RF10: Approved seeded creator checks Financial Dashboard and requests PIX payout
        String lucasToken = loginAndGetToken("lucas@toneguitar.com", "Creator@123");
        MvcResult dashRes = mockMvc.perform(get("/api/creator/dashboard")
                        .header("Authorization", "Bearer " + lucasToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesCount").isNumber())
                .andExpect(jsonPath("$.grossRevenue").isNumber())
                .andExpect(jsonPath("$.platformFeeRetained").isNumber())
                .andExpect(jsonPath("$.availableBalance").isNumber())
                .andReturn();
        double available = objectMapper.readTree(dashRes.getResponse().getContentAsString()).get("availableBalance").asDouble();
        assertThat(available).isGreaterThan(10.0);

        mockMvc.perform(post("/api/creator/payouts")
                        .header("Authorization", "Bearer " + lucasToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 10.00, "pixKey", "lucas@toneguitar.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Overdraft payout must fail with 400
        mockMvc.perform(post("/api/creator/payouts")
                        .header("Authorization", "Bearer " + lucasToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 99999.00))))
                .andExpect(status().isBadRequest());

        // 11. RF08 + RF04: Seeded buyer purchased Heavy Studio Essentials Bundle -> all 3 included patches unlocked in Library!
        String seededBuyerToken = loginAndGetToken("buyer@toneguitar.com", "Buyer@123");
        MvcResult libRes = mockMvc.perform(get("/api/library")
                        .header("Authorization", "Bearer " + seededBuyerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode library = objectMapper.readTree(libRes.getResponse().getContentAsString());
        assertThat(library.size()).isGreaterThanOrEqualTo(5); // 2 individual + 3 unlocked from bundle2
    }

    private String registerAndGetToken(String name, String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name, "email", email, "password", password
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}
