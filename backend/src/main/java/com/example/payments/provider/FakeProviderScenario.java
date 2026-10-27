package com.example.payments.provider;

import com.fasterxml.jackson.annotation.JsonFormat;

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

    public record Callback(String eventId, String providerPaymentId, String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
    }
}
