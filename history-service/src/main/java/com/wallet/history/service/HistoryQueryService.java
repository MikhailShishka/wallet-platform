package com.wallet.history.service;

import com.wallet.history.entity.TransactionHistoryEntry;
import com.wallet.history.repository.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryQueryService {

    private final TransactionHistoryRepository repository;

    public Page<TransactionHistoryEntry> getHistory(UUID walletId, Pageable pageable) {
        return repository.findByWalletIdOrderByOccurredAtDesc(walletId, pageable);
    }
}
