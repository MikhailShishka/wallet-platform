package com.wallet.history.repository;

import com.wallet.history.entity.TransactionHistoryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionHistoryRepository
        extends MongoRepository<TransactionHistoryEntry, String> {

    /** Пагинированная выборка истории кошелька, свежие сверху. */
    Page<TransactionHistoryEntry> findByWalletIdOrderByOccurredAtDesc(
            UUID walletId, Pageable pageable);

    /** Для идемпотентности consumer'а. */
    boolean existsByEventId(UUID eventId);
}
