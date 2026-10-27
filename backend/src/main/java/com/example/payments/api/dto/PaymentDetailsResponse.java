package com.example.payments.api.dto;

import com.example.payments.domain.Currency;
import com.example.payments.domain.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PaymentDetailsResponse(
        UUID id,
        PaymentStatus status,
        String providerStatus,
        String providerPaymentId,
        Instant lastCallbackOccurredAt,
        String amount,
        Currency currency,
        String customerReference) {
}
