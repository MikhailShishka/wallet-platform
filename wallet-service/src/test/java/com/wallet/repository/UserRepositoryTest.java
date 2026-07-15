package com.wallet.repository;

import com.wallet.AbstractIntegrationTest;
import com.wallet.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-тест репозитория на настоящем Postgres через Testcontainers.
 *
 * @DataJpaTest — slice-тест JPA-слоя.
 * @AutoConfigureTestDatabase(replace = NONE) — не подменять на H2!
 * Хотим настоящий Postgres из AbstractIntegrationTest.
 *
 * Каждый тест обёрнут в транзакцию, которая откатывается по завершении → изоляция.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindByEmail() {
        // GIVEN
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setFirstName("Alice");
        user.setLastName("Wonder");

        userRepository.save(user);

        // WHEN
        Optional<User> found = userRepository.findByEmail("alice@example.com");

        // THEN
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getId()).isNotNull();     // UUID сгенерирован
        assertThat(found.get().getCreatedAt()).isNotNull();  // @PrePersist сработал
    }

    @Test
    void shouldReturnEmptyWhenEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("nobody@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmailShouldReturnTrue() {
        // GIVEN
        User user = new User();
        user.setEmail("bob@example.com");
        user.setPasswordHash("hash");
        userRepository.save(user);

        // WHEN + THEN
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("no-one@example.com")).isFalse();
    }
}
