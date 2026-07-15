package com.wallet.config;

import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Проверка «текущий пользователь — владелец кошелька».
 *
 * Используется в @PreAuthorize через SpEL:
 *   @PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")
 *
 * Имя бина "walletSecurity" фиксировано, чтобы совпадало со ссылкой в SpEL.
 */
@Component("walletSecurity")
@RequiredArgsConstructor
public class WalletSecurity {

    private final WalletRepository walletRepository;

    public boolean isOwner(UUID walletId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AppUserDetails userDetails)) {
            return false;
        }

        UUID currentUserId = userDetails.getUserId();

        return walletRepository.findById(walletId)
            .map(w -> w.getUser().getId().equals(currentUserId))
            .orElse(false);
    }
}
