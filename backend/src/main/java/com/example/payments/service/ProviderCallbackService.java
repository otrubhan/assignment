package com.example.payments.service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProviderCallbackService {

    public void handleProviderUpdate(ProviderCallbackData providerCallbackData) {
        // find the payment record in the database by providerPaymentId, if not found throw PaymentNotFoundException
        // check if occurredAt is after the last recorded callback time for this payment, if not ignore the callback
        // update the payment status, provider payment status and last_occured_at in the database based on the provider callback data (optimistic locking)
        //TODO: return appropriate HTTP response to the provider, not 200 automatically
        //return 200 if OK
    }

    public record ProviderCallbackData(String eventId, String providerPaymentId, String status, Instant occurredAt) {
    }
}
