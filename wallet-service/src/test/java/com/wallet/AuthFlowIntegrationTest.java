package com.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ПОЛНЫЙ интеграционный тест e2e через MockMvc.
 *
 * Поднимаются:
 * - весь Spring-контекст
 * - настоящий Postgres в контейнере
 * - Spring Security с реальной цепочкой фильтров
 *
 * Проверяем сквозной сценарий: регистрация → логин → JWT → защищённый эндпоинт.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void cleanUp() {
        // Изолируем тесты — Spring не откатит транзакции контроллера автоматически
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fullAuthFlow_registerLoginAccessProtected() throws Exception {
        // ============================================================
        // 1. Регистрация — открытый эндпоинт, 201
        // ============================================================
        String registerBody = """
            {
              "email": "e2e-user@example.com",
              "password": "supersecret",
              "firstName": "E2E",
              "lastName": "User"
            }
            """;

        MvcResult registerResult = mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("e2e-user@example.com"))
            .andReturn();

        JsonNode registerJson = objectMapper.readTree(
            registerResult.getResponse().getContentAsString());
        String userId = registerJson.get("id").asText();

        // ============================================================
        // 2. Логин — получаем JWT
        // ============================================================
        String loginBody = """
            { "email": "e2e-user@example.com", "password": "supersecret" }
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();

        JsonNode loginJson = objectMapper.readTree(
            loginResult.getResponse().getContentAsString());
        String token = loginJson.get("token").asText();
        assertThat(token).isNotBlank();

        // ============================================================
        // 3. Без токена — 401
        // ============================================================
        mockMvc.perform(get("/api/v1/wallets/user/{userId}", userId))
            .andExpect(status().isUnauthorized());

        // ============================================================
        // 4. С токеном — 200
        // ============================================================
        mockMvc.perform(get("/api/v1/wallets/user/{userId}", userId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));   // пока нет кошельков

        // ============================================================
        // 5. Создаём кошелёк с токеном — 201
        // ============================================================
        String createWalletBody = """
            { "userId": "%s", "currency": "USD" }
            """.formatted(userId);

        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(createWalletBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currency").value("USD"));

        // ============================================================
        // 6. Проверяем — кошелёк появился
        // ============================================================
        mockMvc.perform(get("/api/v1/wallets/user/{userId}", userId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].currency").value("USD"));

        // ============================================================
        // 7. Второй кошелёк той же валюты — 409 Conflict
        // ============================================================
        mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(createWalletBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        // GIVEN — зарегистрированный пользователь
        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "bob@example.com",
                      "password": "correct-password",
                      "firstName": "Bob",
                      "lastName": "B"
                    }
                    """))
            .andExpect(status().isCreated());

        // WHEN — логин с неверным паролем
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "bob@example.com", "password": "WRONG" }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            // Защита от user enumeration: то же сообщение для «нет юзера» и «неверный пароль»
            .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void loginWithNonexistentEmailReturns401_sameMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "nobody@example.com", "password": "any" }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
}
