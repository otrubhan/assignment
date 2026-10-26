package com.example.payments.service;

import com.example.payments.domain.Currency;
import com.example.payments.domain.PaymentStatus;
import com.example.payments.domain.ProviderPaymentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    public CreatePaymentResponseData createPayment(UUID idempotencyKey, CreatePaymentData createPaymentData) {
        // handle deduplication
        // contact Fake Provider to receive providerPaymentId
        String providerPaymentId = UUID.randomUUID().toString();
        // save payment details in database
        // create a new PaymentDetails (last_occured_at?) object and save it to the database (lock?)
        return new CreatePaymentResponseData(providerPaymentId);
    }

    public PaymentDetails getByProviderPaymentId(String providerPaymentId) {
        // retrieve payment details from database by providerPaymentId
        return new PaymentDetails(UUID.randomUUID(), PaymentStatus.PENDING, ProviderPaymentStatus.CAPTURED, providerPaymentId, Instant.now(), new BigDecimal("100.00"), Currency.USD, "customer-123");
    }

    public record CreatePaymentData(BigDecimal amount, Currency currency, String customerReference) {
    }

    public record CreatePaymentResponseData(String providerPaymentId) {
    }

    public record PaymentDetails(UUID id, PaymentStatus status, ProviderPaymentStatus providerStatus,
                                 String providerPaymentId, Instant lastCallbackOccurredAt, BigDecimal amount,
                                 Currency currency, String customerReference) {
    }
}