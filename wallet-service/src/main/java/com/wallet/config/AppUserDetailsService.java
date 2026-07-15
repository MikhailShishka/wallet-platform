package com.wallet.config;

import com.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Spring зовёт loadUserByUsername(...) для поиска пользователя.
 * Параметр "username" в нашем случае = userId.toString()
 * (так как в JWT.sub лежит userId).
 *
 * Для логина по email используем отдельный путь через AuthController.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        return userRepository.findById(UUID.fromString(userId))
            .map(AppUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }
}
