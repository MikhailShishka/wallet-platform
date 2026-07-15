package com.wallet.controller;

import com.wallet.dto.AmountRequest;
import com.wallet.dto.BalanceInCurrencyDto;
import com.wallet.dto.TransactionDto;
import com.wallet.dto.TransferRequest;
import com.wallet.entity.Transaction;
import com.wallet.service.BalanceConversionService;
import com.wallet.service.WalletOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallet operations", description = "Операции: deposit / withdraw / transfer, история")
public class WalletOperationController {

    private final WalletOperationService operationService;
    private final BalanceConversionService balanceConversionService;

    @PostMapping("/{walletId}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")
    @Operation(
        summary = "Пополнение кошелька",
        description = "Идемпотентная операция. Требуется заголовок Idempotency-Key (UUID). " +
                     "Повторный запрос с тем же ключом вернёт ту же транзакцию, не изменив баланс."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Операция выполнена"),
        @ApiResponse(responseCode = "400", description = "Невалидное тело (например, amount ≤ 0)"),
        @ApiResponse(responseCode = "401", description = "Не залогинен"),
        @ApiResponse(responseCode = "403", description = "Не владелец кошелька"),
        @ApiResponse(responseCode = "404", description = "Кошелёк не найден"),
        @ApiResponse(responseCode = "409", description = "Конкурентный конфликт версии, повторите")
    })
    public TransactionDto deposit(
            @PathVariable UUID walletId,
            @Parameter(description = "Уникальный ключ идемпотентности (UUID)", required = true)
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody AmountRequest request) {

        Transaction tx = operationService.deposit(walletId, request.amount(), idempotencyKey);
        return TransactionDto.from(tx);
    }

    @PostMapping("/{walletId}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")
    @Operation(summary = "Снятие с кошелька", description = "Идемпотентно. Требуется Idempotency-Key.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Операция выполнена"),
        @ApiResponse(responseCode = "401", description = "Не залогинен"),
        @ApiResponse(responseCode = "403", description = "Не владелец кошелька"),
        @ApiResponse(responseCode = "404", description = "Кошелёк не найден"),
        @ApiResponse(responseCode = "409", description = "Конкурентный конфликт версии"),
        @ApiResponse(responseCode = "422", description = "Недостаточно средств")
    })
    public TransactionDto withdraw(
            @PathVariable UUID walletId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody AmountRequest request) {

        Transaction tx = operationService.withdraw(walletId, request.amount(), idempotencyKey);
        return TransactionDto.from(tx);
    }

    @PostMapping("/{walletId}/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")
    @Operation(
        summary = "Перевод между кошельками",
        description = "Обе валюты кошельков должны совпадать. Возвращает transferGroupId, " +
                     "по которому в истории обоих кошельков можно найти парные записи."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Перевод выполнен"),
        @ApiResponse(responseCode = "401", description = "Не залогинен"),
        @ApiResponse(responseCode = "403", description = "Не владелец кошелька-источника"),
        @ApiResponse(responseCode = "404", description = "Один из кошельков не найден"),
        @ApiResponse(responseCode = "422", description = "Недостаточно средств / разные валюты")
    })
    public Map<String, UUID> transfer(
            @PathVariable UUID walletId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        UUID groupId = operationService.transfer(
            walletId, request.toWalletId(), request.amount(), idempotencyKey);
        return Map.of("transferGroupId", groupId);
    }

    @GetMapping("/{walletId}/balance-in/{targetCurrency}")
    @PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")
    @Operation(
        summary = "Баланс кошелька в другой валюте",
        description = "Конвертирует баланс через exchange-rate-service. " +
                     "Защищён CircuitBreaker + Retry + TimeLimiter. " +
                     "Поле stale=true, если курс из fallback-кэша или устарел (>5 мин)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Баланс с указанием свежести курса"),
        @ApiResponse(responseCode = "404", description = "Кошелёк не найден"),
        @ApiResponse(responseCode = "503", description = "Сервис курсов недоступен и в кэше пусто")
    })
    public BalanceInCurrencyDto getBalanceInCurrency(
            @PathVariable UUID walletId,
            @PathVariable String targetCurrency) {
        return balanceConversionService.getBalanceInCurrency(walletId, targetCurrency);
    }

    @GetMapping("/{walletId}/transactions")
    @PreAuthorize("@walletSecurity.isOwner(#walletId, authentication)")
    @Operation(
        summary = "История операций кошелька",
        description = "Пагинация offset-based: ?page=0&size=20&sort=createdAt,desc. " +
                     "Свежие сверху по умолчанию."
    )
    public Page<TransactionDto> getHistory(
            @PathVariable UUID walletId,
            @PageableDefault(size = 20) Pageable pageable) {

        return operationService.getWalletHistory(walletId, pageable)
            .map(TransactionDto::from);
    }
}
