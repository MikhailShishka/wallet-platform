package com.wallet.service;

import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.exception.UserNotFoundException;
import com.wallet.exception.WalletAlreadyExistsException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    /**
     * Создать счёт для пользователя в указанной валюте.
     * Один пользователь × одна валюта = один счёт (UNIQUE constraint в БД).
     */
    @Transactional
    public Wallet createWallet(UUID userId, String currency) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        if (walletRepository.existsByUserIdAndCurrency(userId, currency)) {
            throw new WalletAlreadyExistsException(userId, currency);
        }

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setCurrency(currency);
        wallet.setBalance(BigDecimal.ZERO);

        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet created: id={}, userId={}, currency={}",
                saved.getId(), userId, currency);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Wallet> getUserWallets(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return walletRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Wallet getById(UUID walletId) {
        return walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}
