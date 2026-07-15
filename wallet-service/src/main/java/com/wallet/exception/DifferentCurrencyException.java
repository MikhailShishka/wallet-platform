package com.wallet.exception;

public class DifferentCurrencyException extends RuntimeException {
    public DifferentCurrencyException(String from, String to) {
        super("Cannot transfer between different currencies: " + from + " → " + to
              + ". Use exchange service first.");
    }
}
