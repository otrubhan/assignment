package com.example.payments.service;

import java.time.Instant;

public interface ProviderCallbackService {

    void handle(Callback callback);

    record Callback(String eventId, String providerPaymentId, String status, Instant occurredAt) {
    }
}
