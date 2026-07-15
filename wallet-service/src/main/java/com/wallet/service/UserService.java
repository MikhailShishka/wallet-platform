package com.wallet.service;

import com.wallet.dto.RegisterUserRequest;
import com.wallet.entity.User;
import com.wallet.exception.EmailAlreadyExistsException;
import com.wallet.exception.UserNotFoundException;
import com.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UserService.
 *
 * Кэширование:
 * - getById закэширован в "users" по id.
 * - При регистрации ничего не инвалидируем (новый юзер, в кэше его быть не может).
 * - При будущих update-операциях используем @CacheEvict.
 *
 * ⚠️ Кэшируем entity User: у неё нет @Version и лениво-загружаемых полей,
 * поэтому безопасно. Wallet с @Version и балансом — НЕ кэшируем никогда.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    /**
     * @Cacheable кладёт результат в Redis-кэш "users" по ключу = id.
     * Первый вызов → БД, второй и последующие → напрямую из Redis, метод не вызывается.
     */
    @Cacheable(value = "users", key = "#id")
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        log.debug("Cache MISS for user {}", id);   // видно, когда реально идём в БД
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Пример инвалидации на будущее — когда добавим update-профиля.
     * Сейчас метода нет, но структура готова.
     */
    @CacheEvict(value = "users", key = "#id")
    public void invalidateUserCache(UUID id) {
        log.debug("Evicted user {} from cache", id);
    }
}
