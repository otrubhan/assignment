package com.example.payments.service;

import com.example.payments.domain.Currency;
import com.example.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PaymentService {

    CreationResult create(UUID idempotencyKey, CreatePayment command);

    PaymentDetails getByProviderPaymentId(String providerPaymentId);

    record CreatePayment(BigDecimal amount, Currency currency, String customerReference) {
    }

    record CreationResult(String providerPaymentId) {
    }

    record PaymentDetails(
            UUID id,
            PaymentStatus status,
            String providerStatus,
            String providerPaymentId,
            Instant lastCallbackOccurredAt,
            BigDecimal amount,
            Currency currency,
            String customerReference) {
    }
}
