package com.wallet;

import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import com.wallet.service.WalletOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ГЛАВНЫЙ тест на конкурентность:
 * два параллельных withdraw одного и того же кошелька.
 *
 * Ожидание:
 * - Один поток успешно списывает.
 * - Другой получает OptimisticLockException, потому что @Version
 *   на Wallet не позволяет двум UPDATE с одинаковой версией пройти.
 *
 * Это доказывает, что защита от двойного списания РАБОТАЕТ.
 */
@SpringBootTest
class WalletConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private WalletOperationService operationService;

    private UUID walletId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("concurrent-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setCurrency("USD");
        wallet.setBalance(new BigDecimal("100.00"));
        wallet = walletRepository.save(wallet);

        walletId = wallet.getId();
    }

    @Test
    void concurrentWithdrawsShouldFailOne() throws InterruptedException {
        // GIVEN — баланс 100, два потока пытаются снять по 80 одновременно.
        // Оба видят балланс 100, оба считают "ok, хватает", оба пытаются записать 20.
        // Без @Version один из них перезапишет другого → двойное списание.
        // С @Version — один падает с OptimisticLockException.

        var barrier = new CyclicBarrier(2);      // синхронизация старта
        var successes = new AtomicInteger(0);
        var conflicts = new AtomicInteger(0);
        var errors = new AtomicInteger(0);

        Runnable withdraw80 = () -> {
            try {
                barrier.await();     // ждём напарника
                operationService.withdraw(walletId, new BigDecimal("80"), UUID.randomUUID());
                successes.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                errors.incrementAndGet();
                e.printStackTrace();
            }
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            pool.submit(withdraw80);
            pool.submit(withdraw80);
            pool.shutdown();
            boolean finished = pool.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(finished).isTrue();
        }

        // THEN — ровно один успех, ровно один конфликт, никаких других ошибок.
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(errors.get()).isZero();

        // И баланс = 100 - 80 = 20, а не отрицательный.
        Wallet finalState = walletRepository.findById(walletId).orElseThrow();
        assertThat(finalState.getBalance()).isEqualByComparingTo("20.00");
    }

    @Test
    void idempotentWithdrawShouldReturnSameTransaction() {
        // GIVEN
        UUID idempotencyKey = UUID.randomUUID();

        // WHEN — тот же ключ дважды
        var first = operationService.withdraw(walletId, new BigDecimal("10"), idempotencyKey);
        var second = operationService.withdraw(walletId, new BigDecimal("10"), idempotencyKey);

        // THEN — вторая операция не изменила баланс, вернула ту же транзакцию
        assertThat(second.getId()).isEqualTo(first.getId());

        Wallet finalState = walletRepository.findById(walletId).orElseThrow();
        assertThat(finalState.getBalance()).isEqualByComparingTo("90");   // не 80!
    }
}
