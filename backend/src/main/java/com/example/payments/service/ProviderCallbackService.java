package com.example.payments.service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProviderCallbackService {

    private final CallbackTransactionProcessor processor;

    public ProviderCallbackService(CallbackTransactionProcessor processor) {
        this.processor = processor;
    }

    // Keep the monitor until the processor's transaction commits or rolls back.
    public synchronized void handleProviderUpdate(ProviderCallbackData providerCallbackData) {
        processor.process(providerCallbackData);
    }

    public record ProviderCallbackData(String eventId, String providerPaymentId, String status, Instant occurredAt) {
    }
}
