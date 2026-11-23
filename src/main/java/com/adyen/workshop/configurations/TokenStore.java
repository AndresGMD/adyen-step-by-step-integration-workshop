package com.adyen.workshop.configurations;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tokenization module - minimal in-memory storage for the latest token (recurringDetailReference).
 * Workshop only: use a database in a real application.
 */
@Component
public class TokenStore {
    /** Identifies the shopper the token belongs to. Must be identical in the create and the token payment. */
    public static final String SHOPPER_REFERENCE = "workshop-shopper";

    private final AtomicReference<String> token = new AtomicReference<>();

    public void save(String value) {
        token.set(value);
    }

    public Optional<String> latest() {
        return Optional.ofNullable(token.get());
    }
}
