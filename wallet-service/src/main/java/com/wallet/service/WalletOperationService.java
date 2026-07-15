package com.wallet.service;

import com.wallet.dto.MoneyTransferredEvent;
import com.wallet.entity.Transaction;
import com.wallet.entity.TransactionType;
import com.wallet.entity.Wallet;
import com.wallet.exception.DifferentCurrencyException;
import com.wallet.exception.InsufficientFundsException;
import com.wallet.exception.WalletNotFoundException;
import com.wallet.repository.TransactionRepository;
import com.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ядро бизнес-логики: deposit / withdraw / transfer.
 *
 * Каждая операция:
 * - Атомарна: INSERT в transactions + UPDATE wallets + INSERT в outbox
 *   в ОДНОЙ @Transactional.
 * - Идемпотентна: UNIQUE constraint на idempotency_key ловит повторы.
 * - Оптимистически заблокирована: @Version на Wallet автоматически
 *   бросит OptimisticLockException при конкурентном UPDATE.
 * - Публикует событие через OutboxEventPublisher — событие сохраняется
 *   в outbox-таблицу той же БД. Отдельный OutboxRelay пошлёт в Kafka.
 *
 * Инструментирована метриками (Micrometer → Prometheus).
 */
@Service
@Slf4j
public class WalletOperationService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    private final Counter depositCounter;
    private final Counter withdrawCounter;
    private final Counter transferCounter;
    private final Counter insufficientFundsCounter;
    private final Timer transferTimer;

    public WalletOperationService(WalletRepository walletRepository,
                                   TransactionRepository transactionRepository,
                                   OutboxEventPublisher outboxEventPublisher,
                                   MeterRegistry registry) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventPublisher = outboxEventPublisher;

        this.depositCounter = Counter.builder("wallet.operations.total")
            .tag("type", "deposit")
            .register(registry);
        this.withdrawCounter = Counter.builder("wallet.operations.total")
            .tag("type", "withdraw")
            .register(registry);
        this.transferCounter = Counter.builder("wallet.operations.total")
            .tag("type", "transfer")
            .register(registry);
        this.insufficientFundsCounter = Counter.builder("wallet.errors.total")
            .tag("type", "insufficient_funds")
            .register(registry);
        this.transferTimer = Timer.builder("wallet.transfer.duration")
            .register(registry);
    }

    // ============================================================
    // DEPOSIT
    // ============================================================

    @Transactional
    public Transaction deposit(UUID walletId, BigDecimal amount, UUID idempotencyKey) {
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay: deposit key={}", idempotencyKey);
            return existing.get();
        }

        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException(walletId));

        wallet.setBalance(wallet.getBalance().add(amount));

        Transaction tx = new Transaction();
        tx.setWallet(wallet);
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setIdempotencyKey(idempotencyKey);

        try {
            Transaction saved = transactionRepository.save(tx);
            depositCounter.increment();

            // Публикуем событие в outbox — атомарно с изменением баланса
            outboxEventPublisher.publish(new MoneyTransferredEvent(
                saved.getId(),
                TransactionType.DEPOSIT.name(),
                walletId,
                null,
                null,
                amount,
                wallet.getCurrency(),
                saved.getCreatedAt()
            ));

            log.info("Deposit: walletId={}, amount={}, newBalance={}",
                    walletId, amount, wallet.getBalance());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.info("Race on idempotency key, returning existing: {}", idempotencyKey);
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> e);
        }
    }

    // ============================================================
    // WITHDRAW
    // ============================================================

    @Transactional
    public Transaction withdraw(UUID walletId, BigDecimal amount, UUID idempotencyKey) {
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay: withdraw key={}", idempotencyKey);
            return existing.get();
        }

        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            insufficientFundsCounter.increment();
            throw new InsufficientFundsException(walletId, amount, wallet.getBalance());
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));

        Transaction tx = new Transaction();
        tx.setWallet(wallet);
        tx.setType(TransactionType.WITHDRAW);
        tx.setAmount(amount);
        tx.setIdempotencyKey(idempotencyKey);

        try {
            Transaction saved = transactionRepository.save(tx);
            withdrawCounter.increment();

            outboxEventPublisher.publish(new MoneyTransferredEvent(
                saved.getId(),
                TransactionType.WITHDRAW.name(),
                walletId,
                null,
                null,
                amount,
                wallet.getCurrency(),
                saved.getCreatedAt()
            ));

            log.info("Withdraw: walletId={}, amount={}, newBalance={}",
                    walletId, amount, wallet.getBalance());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.info("Race on idempotency key, returning existing: {}", idempotencyKey);
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> e);
        }
    }

    // ============================================================
    // TRANSFER
    // ============================================================

    @Transactional
    public UUID transfer(UUID fromWalletId, UUID toWalletId,
                         BigDecimal amount, UUID idempotencyKey) {
        return transferTimer.record(() ->
            doTransfer(fromWalletId, toWalletId, amount, idempotencyKey));
    }

    private UUID doTransfer(UUID fromWalletId, UUID toWalletId,
                             BigDecimal amount, UUID idempotencyKey) {
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay: transfer key={}", idempotencyKey);
            return existing.get().getTransferGroupId();
        }

        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Wallet from = walletRepository.findById(fromWalletId)
            .orElseThrow(() -> new WalletNotFoundException(fromWalletId));
        Wallet to = walletRepository.findById(toWalletId)
            .orElseThrow(() -> new WalletNotFoundException(toWalletId));

        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new DifferentCurrencyException(from.getCurrency(), to.getCurrency());
        }

        if (from.getBalance().compareTo(amount) < 0) {
            insufficientFundsCounter.increment();
            throw new InsufficientFundsException(fromWalletId, amount, from.getBalance());
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        UUID groupId = UUID.randomUUID();

        Transaction outTx = new Transaction();
        outTx.setWallet(from);
        outTx.setType(TransactionType.TRANSFER);
        outTx.setAmount(amount);
        outTx.setTransferGroupId(groupId);
        outTx.setIdempotencyKey(idempotencyKey);

        Transaction inTx = new Transaction();
        inTx.setWallet(to);
        inTx.setType(TransactionType.TRANSFER);
        inTx.setAmount(amount);
        inTx.setTransferGroupId(groupId);
        inTx.setIdempotencyKey(UUID.nameUUIDFromBytes(
            (idempotencyKey + ":in").getBytes()));

        try {
            Transaction outSaved = transactionRepository.save(outTx);
            Transaction inSaved = transactionRepository.save(inTx);
            transferCounter.increment();

            // Публикуем два события — по одному на каждую сторону перевода.
            // Ключ Kafka = walletId → каждое событие идёт в партицию своего кошелька.
            outboxEventPublisher.publish(new MoneyTransferredEvent(
                outSaved.getId(),
                TransactionType.TRANSFER.name(),
                fromWalletId,
                toWalletId,
                groupId,
                amount.negate(),   // списание: отрицательная сумма
                from.getCurrency(),
                outSaved.getCreatedAt()
            ));
            outboxEventPublisher.publish(new MoneyTransferredEvent(
                inSaved.getId(),
                TransactionType.TRANSFER.name(),
                toWalletId,
                fromWalletId,
                groupId,
                amount,            // зачисление: положительная
                to.getCurrency(),
                inSaved.getCreatedAt()
            ));

            log.info("Transfer: {} → {}, amount={}, groupId={}",
                    fromWalletId, toWalletId, amount, groupId);
            return groupId;
        } catch (DataIntegrityViolationException e) {
            log.info("Race on idempotency key, returning existing: {}", idempotencyKey);
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(Transaction::getTransferGroupId)
                .orElseThrow(() -> e);
        }
    }

    // ============================================================
    // HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Transaction> getWalletHistory(UUID walletId, Pageable pageable) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
    }
}
