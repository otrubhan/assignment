package com.example.payments.provider;

import java.time.Instant;
import java.util.List;

public record FakeProviderScenario(List<Callback> callbacks) {

    public FakeProviderScenario {
        if (callbacks == null || callbacks.isEmpty()) {
            throw new IllegalArgumentException("Scenario must contain at least one callback.");
        }
        callbacks = List.copyOf(callbacks);
        for (Callback callback : callbacks) {
            if (callback.eventId() != null && callback.eventId().isBlank()) {
                throw new IllegalArgumentException("Scenario eventId must not be blank.");
            }
        }
    }

    public record Callback(String eventId, String status, Instant occurredAt) {
    }
}
