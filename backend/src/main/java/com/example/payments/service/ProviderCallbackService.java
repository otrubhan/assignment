package com.example.payments.service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProviderCallbackService {

    public void handleProviderUpdate(ProviderCallbackData providerCallbackData) {
        // save the provider callback data to the database, lock the payment record to prevent concurrent updates
        // update the payment status in the database based on the provider callback data
    }

    public record ProviderCallbackData(String eventId, String providerPaymentId, String status, Instant occurredAt) {
    }
}
