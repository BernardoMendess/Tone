package com.tone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.controller.AuthController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void jwtContainsNoPiiAndRefreshRotationDetectsReplayTheft() throws Exception {
        String email = "authsec-" + UUID.randomUUID() + "@tone.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Sec User", "email", email, "password", "VeryStrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();

        String token1 = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        String payloadJson = new String(Base64.getUrlDecoder().decode(token1.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payloadJson).doesNotContain(email).doesNotContain("Sec User");

        Cookie refreshCookie1 = reg.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
        assertThat(refreshCookie1).isNotNull();
        assertThat(refreshCookie1.isHttpOnly()).isTrue();

        // Rotate refresh token
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie1))
                .andExpect(status().isOk())
                .andReturn();
        String token2 = objectMapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk());

        // Replaying already-rotated cookie1 triggers theft detection -> revokes ALL tokens (increments tokenVersion)!
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie1))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountLocksAfterFiveConsecutiveFailedLogins() throws Exception {
        String email = "lockout-" + UUID.randomUUID() + "@tone.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Lock User", "email", email, "password", "ValidPassword123!"))))
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", email, "password", "WrongPassword123!"))))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt even with right password is locked (429)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "ValidPassword123!"))))
                .andExpect(status().isTooManyRequests());
    }
}
