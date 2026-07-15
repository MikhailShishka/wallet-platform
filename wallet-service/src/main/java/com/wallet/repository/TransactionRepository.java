package com.wallet.repository;

import com.wallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Пагинированная история кошелька, свежие сверху. */
    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    /** Для идемпотентности — есть ли уже такая операция? */
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
}
