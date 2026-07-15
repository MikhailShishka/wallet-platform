package com.wallet.controller;

import com.wallet.config.JwtService;
import com.wallet.dto.LoginRequest;
import com.wallet.dto.LoginResponse;
import com.wallet.entity.User;
import com.wallet.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Аутентификация и выдача JWT")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    @SecurityRequirements   // публичный эндпоинт, отменяем глобальный bearer
    @Operation(
        summary = "Логин",
        description = "Возвращает JWT для дальнейшей аутентификации. " +
                     "Одинаковое сообщение для «нет пользователя» и «неверный пароль» — защита от user enumeration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Логин успешен, токен выдан"),
        @ApiResponse(responseCode = "400", description = "Невалидное тело (например, битый email)"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.debug("Failed login attempt for email={}", request.email());
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return LoginResponse.of(token, user.getId());
    }
}
