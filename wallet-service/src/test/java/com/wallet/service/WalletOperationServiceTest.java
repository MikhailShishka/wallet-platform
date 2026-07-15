package com.wallet.service;

import com.wallet.entity.Transaction;
import com.wallet.entity.TransactionType;
import com.wallet.entity.Wallet;
import com.wallet.exception.DifferentCurrencyException;
import com.wallet.exception.InsufficientFundsException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletOperationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletOperationService service;

    // ============================================================
    // DEPOSIT
    // ============================================================

    @Test
    void depositShouldIncreaseBalance() {
        // GIVEN
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrency("USD");
        wallet.setBalance(new BigDecimal("100.00"));

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        Transaction result = service.deposit(walletId, new BigDecimal("50.00"), key);

        // THEN
        assertThat(wallet.getBalance()).isEqualByComparingTo("150.00");
        assertThat(result.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    void depositShouldBeIdempotent() {
        // GIVEN — этот ключ уже был использован
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Transaction existing = new Transaction();
        existing.setIdempotencyKey(key);
        existing.setType(TransactionType.DEPOSIT);

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        // WHEN
        Transaction result = service.deposit(walletId, new BigDecimal("50"), key);

        // THEN — вернулась прежняя транзакция, БД не тронута
        assertThat(result).isSameAs(existing);
        verify(walletRepository, never()).findById(any());
        verify(transactionRepository, never()).save(any());
    }

    // ============================================================
    // WITHDRAW
    // ============================================================

    @Test
    void withdrawShouldDecreaseBalance() {
        // GIVEN
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setBalance(new BigDecimal("100.00"));

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        service.withdraw(walletId, new BigDecimal("30.00"), key);

        // THEN
        assertThat(wallet.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void withdrawShouldThrowWhenInsufficientFunds() {
        // GIVEN
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setBalance(new BigDecimal("10.00"));

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        // WHEN + THEN
        assertThatThrownBy(() ->
            service.withdraw(walletId, new BigDecimal("50.00"), key))
            .isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");   // не изменилось
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdrawShouldThrowWhenWalletNotFound() {
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.withdraw(walletId, BigDecimal.TEN, key))
            .isInstanceOf(WalletNotFoundException.class);
    }

    // ============================================================
    // TRANSFER
    // ============================================================

    @Test
    void transferShouldMoveMoneyBetweenWallets() {
        // GIVEN
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet from = new Wallet();
        from.setId(fromId); from.setCurrency("USD");
        from.setBalance(new BigDecimal("100"));

        Wallet to = new Wallet();
        to.setId(toId); to.setCurrency("USD");
        to.setBalance(new BigDecimal("50"));

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findById(fromId)).thenReturn(Optional.of(from));
        when(walletRepository.findById(toId)).thenReturn(Optional.of(to));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        UUID groupId = service.transfer(fromId, toId, new BigDecimal("30"), key);

        // THEN
        assertThat(from.getBalance()).isEqualByComparingTo("70");
        assertThat(to.getBalance()).isEqualByComparingTo("80");
        assertThat(groupId).isNotNull();
        verify(transactionRepository, times(2)).save(any());   // две строки ledger
    }

    @Test
    void transferShouldThrowWhenDifferentCurrency() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        Wallet from = new Wallet();
        from.setCurrency("USD");
        from.setBalance(new BigDecimal("100"));

        Wallet to = new Wallet();
        to.setCurrency("EUR");   // разная валюта!
        to.setBalance(BigDecimal.ZERO);

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(walletRepository.findById(fromId)).thenReturn(Optional.of(from));
        when(walletRepository.findById(toId)).thenReturn(Optional.of(to));

        assertThatThrownBy(() ->
            service.transfer(fromId, toId, new BigDecimal("30"), key))
            .isInstanceOf(DifferentCurrencyException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferShouldThrowWhenSameWallet() {
        UUID walletId = UUID.randomUUID();
        UUID key = UUID.randomUUID();

        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.transfer(walletId, walletId, BigDecimal.TEN, key))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
