package com.wallet.exception;

import com.wallet.client.RateUnavailableException;
import com.wallet.dto.ErrorResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Централизованный обработчик исключений.
 * Маппит доменные исключения → HTTP-коды + единый формат ErrorResponse.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------- 401 — не аутентифицирован ----------
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuth(Exception ex) {
        // Одинаковое сообщение — защита от user enumeration
        return ErrorResponse.of("UNAUTHORIZED", "Invalid credentials");
    }

    // ---------- 403 — нет прав ----------
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(AccessDeniedException ex) {
        return ErrorResponse.of("FORBIDDEN", "Access denied");
    }

    // ---------- 404 ----------
    @ExceptionHandler({UserNotFoundException.class, WalletNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(RuntimeException ex) {
        return ErrorResponse.of("NOT_FOUND", ex.getMessage());
    }

    // ---------- 409 — конфликт состояния ----------
    @ExceptionHandler({EmailAlreadyExistsException.class, WalletAlreadyExistsException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(RuntimeException ex) {
        return ErrorResponse.of("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ErrorResponse.of("CONFLICT_RETRY",
                "Resource was modified concurrently. Please retry.");
    }

    // ---------- 422 — бизнес-правило не пускает ----------
    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInsufficientFunds(InsufficientFundsException ex) {
        return ErrorResponse.of("INSUFFICIENT_FUNDS", ex.getMessage());
    }

    @ExceptionHandler(DifferentCurrencyException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleDifferentCurrency(DifferentCurrencyException ex) {
        return ErrorResponse.of("CURRENCY_MISMATCH", ex.getMessage());
    }

    // ---------- 503 — downstream недоступен ----------
    @ExceptionHandler(RateUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleRateUnavailable(RateUnavailableException ex) {
        log.warn("Rate service degraded, no fallback available: {}", ex.getMessage());
        return ErrorResponse.of("RATE_UNAVAILABLE",
                "Exchange rates temporarily unavailable, please retry");
    }

    // ---------- 400 — невалидный запрос ----------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ErrorResponse.of("VALIDATION_ERROR", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException ex) {
        return ErrorResponse.of("BAD_REQUEST", ex.getMessage());
    }

    // ---------- 500 — fallback для непредвиденного ----------
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        // ВАЖНО: stacktrace в лог, клиенту — общий месседж (не утекаем детали)
        log.error("Unhandled exception", ex);
        return ErrorResponse.of("INTERNAL_ERROR", "Something went wrong");
    }
}
