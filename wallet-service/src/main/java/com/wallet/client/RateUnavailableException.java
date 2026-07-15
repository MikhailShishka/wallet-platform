package com.wallet.client;

/**
 * Курс полностью недоступен: и downstream не отвечает, и в локальном кэше пусто.
 * Обработчик исключений вернёт клиенту 503 Service Unavailable.
 */
public class RateUnavailableException extends RuntimeException {
    public RateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
