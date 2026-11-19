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

    @Test
    void concurrentRefreshWithSameTokenAllowsAtMostOneRotationAndTriggersTheftRevocation() throws Exception {
        String email = "race-refresh-" + UUID.randomUUID() + "@tone.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Race User", "email", email, "password", "VeryStrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie refreshCookie = reg.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
        assertThat(refreshCookie).isNotNull();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
            start.await();
            return mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        })).toList();
        start.countDown();

        int succeeded = 0;
        int unauthorized = 0;
        for (Future<Integer> f : futures) {
            int code = f.get(15, TimeUnit.SECONDS);
            if (code == 200) succeeded++;
            if (code == 401) unauthorized++;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(unauthorized).isEqualTo(threads - 1);
    }
}
