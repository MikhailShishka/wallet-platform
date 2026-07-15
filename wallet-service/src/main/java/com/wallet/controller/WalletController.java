package com.wallet.controller;

import com.wallet.dto.CreateWalletRequest;
import com.wallet.dto.WalletDto;
import com.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.wallet.config.AppUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Создать кошелёк.
     * userId в теле должен совпадать с текущим пользователем (защита от создания
     * кошелька от чужого имени).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("#request.userId == authentication.principal.userId")
    public WalletDto create(@Valid @RequestBody CreateWalletRequest request) {
        return WalletDto.from(
            walletService.createWallet(request.userId(), request.currency())
        );
    }

    /**
     * Получить кошелёк — только владелец.
     */
    @GetMapping("/{id}")
    @PreAuthorize("@walletSecurity.isOwner(#id, authentication)")
    public WalletDto getById(@PathVariable UUID id) {
        return WalletDto.from(walletService.getById(id));
    }

    /**
     * Список своих кошельков (userId должен совпадать с токеном).
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("#userId == authentication.principal.userId")
    public List<WalletDto> getByUser(@PathVariable UUID userId) {
        return walletService.getUserWallets(userId).stream()
            .map(WalletDto::from)
            .toList();
    }
}
