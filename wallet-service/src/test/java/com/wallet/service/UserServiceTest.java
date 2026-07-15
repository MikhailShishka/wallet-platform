package com.wallet.service;

import com.wallet.dto.RegisterUserRequest;
import com.wallet.entity.User;
import com.wallet.exception.EmailAlreadyExistsException;
import com.wallet.exception.UserNotFoundException;
import com.wallet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты UserService.
 * Все внешние зависимости замокировали → Spring не поднимается → тесты миллисекундные.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ============================================================
    // register()
    // ============================================================

    @Test
    void shouldRegisterNewUser() {
        // GIVEN
        var request = new RegisterUserRequest(
            "alice@test.com", "secret123", "Alice", "Wonder");

        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        User result = userService.register(request);

        // THEN
        assertThat(result.getEmail()).isEqualTo("alice@test.com");
        assertThat(result.getPasswordHash()).isEqualTo("HASHED");
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Wonder");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        // GIVEN
        var request = new RegisterUserRequest(
            "taken@test.com", "secret", "A", "B");
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        // WHEN + THEN
        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(EmailAlreadyExistsException.class)
            .hasMessageContaining("taken@test.com");

        // save и encode НЕ должны вызываться
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    // ============================================================
    // getById()
    // ============================================================

    @Test
    void shouldReturnUserById() {
        // GIVEN
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setEmail("bob@test.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        // WHEN
        User result = userService.getById(id);

        // THEN
        assertThat(result.getEmail()).isEqualTo("bob@test.com");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        // GIVEN
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // WHEN + THEN
        assertThatThrownBy(() -> userService.getById(id))
            .isInstanceOf(UserNotFoundException.class);
    }
}
