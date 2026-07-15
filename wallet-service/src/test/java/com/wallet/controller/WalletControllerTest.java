package com.wallet.controller;

import com.wallet.config.JwtAuthenticationFilter;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-тест: поднимается ТОЛЬКО веб-слой (контроллер + MVC-инфраструктура).
 * Сервисы мокируются. БД, Tomcat, JPA — не поднимаются.
 *
 * Ключевые аннотации:
 * - @WebMvcTest(WalletController.class) — только этот контроллер.
 * - @MockBean — мок в контексте Spring, заменяет реальный бин.
 * - @WithMockUser — эмулирует залогиненного пользователя.
 */
@WebMvcTest(WalletController.class)
@ActiveProfiles("test")
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    // Security-инфраструктура из SecurityConfig не поднимается в @WebMvcTest,
    // но фильтр остаётся в контексте (@Component). Мокаем его зависимости:
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    // ============================================================
    // POST /api/v1/wallets — создание кошелька
    // ============================================================

    @Test
    @WithMockUser
    void shouldReturn201WhenCreatingWallet() throws Exception {
        // GIVEN
        User user = new User();
        user.setId(userId);

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setUser(user);
        wallet.setCurrency("USD");
        wallet.setBalance(BigDecimal.ZERO);

        when(walletService.createWallet(eq(userId), eq("USD"))).thenReturn(wallet);

        // WHEN + THEN
        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "userId": "%s", "currency": "USD" }
                    """.formatted(userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(walletId.toString()))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @WithMockUser
    void shouldReturn400OnInvalidCurrencyLowercase() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "userId": "%s", "currency": "usd" }
                    """.formatted(userId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser
    void shouldReturn400OnMissingCurrency() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "userId": "%s" }
                    """.formatted(userId)))
            .andExpect(status().isBadRequest());
    }

    // ============================================================
    // GET /api/v1/wallets/user/{userId}
    // ============================================================

    @Test
    @WithMockUser
    void shouldReturnUserWallets() throws Exception {
        // GIVEN
        User user = new User();
        user.setId(userId);

        Wallet w1 = new Wallet();
        w1.setId(UUID.randomUUID());
        w1.setUser(user);
        w1.setCurrency("USD");
        w1.setBalance(BigDecimal.valueOf(100));

        Wallet w2 = new Wallet();
        w2.setId(UUID.randomUUID());
        w2.setUser(user);
        w2.setCurrency("EUR");
        w2.setBalance(BigDecimal.ZERO);

        when(walletService.getUserWallets(userId)).thenReturn(List.of(w1, w2));

        // WHEN + THEN
        mockMvc.perform(get("/api/v1/wallets/user/{userId}", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].currency").value("USD"))
            .andExpect(jsonPath("$[1].currency").value("EUR"));
    }
}
