package com.wallet.history.controller;

import com.wallet.history.dto.TransactionHistoryDto;
import com.wallet.history.service.HistoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GET /api/v1/history/wallets/{walletId}?page=0&size=20
 *
 * ⚠️ Пока без аутентификации.
 * В реальной системе за history-service стоял бы API Gateway
 * с той же JWT-проверкой, что и wallet-service.
 * Либо history-service принимал бы тот же JWT и проверял владение.
 * Разберём в блоке про микросервисы.
 */
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryQueryService historyQueryService;

    @GetMapping("/wallets/{walletId}")
    public Page<TransactionHistoryDto> getWalletHistory(
            @PathVariable UUID walletId,
            @PageableDefault(size = 20) Pageable pageable) {

        return historyQueryService.getHistory(walletId, pageable)
            .map(TransactionHistoryDto::from);
    }
}
