package com.wallet.controller;

import com.wallet.dto.RegisterUserRequest;
import com.wallet.dto.UserDto;
import com.wallet.entity.User;
import com.wallet.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Регистрация и профиль")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements   // публичный эндпоинт
    @Operation(
        summary = "Регистрация нового пользователя",
        description = "Пароль хэшируется через BCrypt. Возвращает профиль (без passwordHash)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Пользователь создан"),
        @ApiResponse(responseCode = "400", description = "Невалидное тело"),
        @ApiResponse(responseCode = "409", description = "Email уже занят")
    })
    public UserDto register(@Valid @RequestBody RegisterUserRequest request) {
        User user = userService.register(request);
        return UserDto.from(user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя по id (требуется JWT)")
    public UserDto getById(@PathVariable UUID id) {
        return UserDto.from(userService.getById(id));
    }
}
