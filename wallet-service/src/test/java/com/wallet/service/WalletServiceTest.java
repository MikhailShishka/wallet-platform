package com.wallet.service;

import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.exception.UserNotFoundException;
import com.wallet.exception.WalletAlreadyExistsException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletService walletService;

    // ============================================================
    // createWallet()
    // ============================================================

    @Test
    void shouldCreateWalletWithZeroBalance() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        Wallet result = walletService.createWallet(userId, "USD");

        // THEN
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getUser()).isSameAs(user);

        // Проверяем, что save был вызван с правильным объектом (через ArgumentCaptor)
        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // WHEN + THEN
        assertThatThrownBy(() -> walletService.createWallet(userId, "USD"))
            .isInstanceOf(UserNotFoundException.class);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenWalletAlreadyExists() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.existsByUserIdAndCurrency(userId, "USD")).thenReturn(true);

        // WHEN + THEN
        assertThatThrownBy(() -> walletService.createWallet(userId, "USD"))
            .isInstanceOf(WalletAlreadyExistsException.class);

        verify(walletRepository, never()).save(any());
    }

    // ============================================================
    // getUserWallets()
    // ============================================================

    @Test
    void shouldReturnUserWallets() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        Wallet w1 = new Wallet(); w1.setCurrency("USD");
        Wallet w2 = new Wallet(); w2.setCurrency("EUR");

        when(userRepository.existsById(userId)).thenReturn(true);
        when(walletRepository.findByUserId(userId)).thenReturn(List.of(w1, w2));

        // WHEN
        List<Wallet> result = walletService.getUserWallets(userId);

        // THEN
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Wallet::getCurrency).containsExactly("USD", "EUR");
    }

    @Test
    void shouldThrowWhenGettingWalletsForUnknownUser() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        // WHEN + THEN
        assertThatThrownBy(() -> walletService.getUserWallets(userId))
            .isInstanceOf(UserNotFoundException.class);

        verify(walletRepository, never()).findByUserId(any());
    }

    // ============================================================
    // getById()
    // ============================================================

    @Test
    void shouldReturnWalletById() {
        // GIVEN
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrency("EUR");
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // WHEN
        Wallet result = walletService.getById(walletId);

        // THEN
        assertThat(result.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void shouldThrowWhenWalletNotFound() {
        // GIVEN
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        // WHEN + THEN
        assertThatThrownBy(() -> walletService.getById(walletId))
            .isInstanceOf(WalletNotFoundException.class);
    }
}
